import type { Context, Hono } from "hono";
import { z } from "zod";
import type { MembershipRecord, VerificationFields, VerificationRequestSummary, VerificationStatus } from "@cbu-find/contracts";
import { AppError, assertFound } from "../errors";
import type { PlainDocument } from "../firestore";
import { deletePrivateObject, downloadPrivateObject, uploadPrivateObject, verificationObjectPath } from "../storage";
import type { AppBindings } from "../domain";
import { ocrProviderFor } from "./ocr";
import { parseStudentId } from "./parse";
import { canAutoApprove, matcherFor } from "./matcher";

const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
const CONTENT_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
const INSTITUTION_ID = "cbu";
const REVIEW_STATUSES: VerificationStatus[] = ["PENDING_REVIEW", "PENDING_RECORD_MATCH"];

export const fieldsSchema = z.object({
  studentNumber: z.string().trim().min(3).max(30),
  fullName: z.string().trim().min(2).max(100),
  programme: z.string().trim().max(120).default(""),
  academicYear: z.string().trim().max(30).default(""),
});

const scanSchema = z.object({
  imageBase64: z.string().min(64).max(Math.ceil((MAX_DOCUMENT_BYTES * 4) / 3) + 64),
  contentType: z.enum(CONTENT_TYPES),
});

const decisionSchema = z.object({
  decision: z.enum(["APPROVE", "REJECT", "NEEDS_RESUBMISSION"]),
  reason: z.string().trim().max(500).default(""),
});

export function normalizeStudentNumber(value: string): string {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

export function summaryOf(record: PlainDocument): VerificationRequestSummary {
  return {
    id: record.id,
    status: (record.status as VerificationStatus) ?? "DRAFT",
    fields: {
      studentNumber: String(record.studentNumber ?? ""),
      fullName: String(record.fullName ?? ""),
      programme: String(record.programme ?? ""),
      academicYear: String(record.academicYear ?? ""),
    },
    confidence: (record.confidence as VerificationRequestSummary["confidence"]) ?? {},
    ocrProvider: String(record.ocrProvider ?? "not-configured"),
    ocrConfidence: Number(record.ocrConfidence ?? 0),
    matchOutcome: (record.matchOutcome as VerificationRequestSummary["matchOutcome"]) ?? "UNAVAILABLE",
    matchConfidence: Number(record.matchConfidence ?? 0),
    submittedAt: Number(record.submittedAt ?? 0),
    updatedAt: Number(record.updatedAt ?? 0),
    ...(record.decidedAt ? { decidedAt: Number(record.decidedAt) } : {}),
    ...(record.decisionReason ? { decisionReason: String(record.decisionReason) } : {}),
  };
}

export function membershipOf(user: PlainDocument | null, request: PlainDocument | null): MembershipRecord {
  const status = user?.membershipStatus;
  return {
    status: status === "VERIFIED" || status === "PENDING" || status === "REJECTED" ? status : "UNVERIFIED",
    institutionId: String(user?.institutionId ?? INSTITUTION_ID),
    updatedAt: Number(user?.membershipUpdatedAt ?? 0),
    request: request ? summaryOf(request) : null,
  };
}

async function latestRequest(db: AppBindings["Variables"]["db"], uid: string): Promise<PlainDocument | null> {
  const rows = await db.query("verificationRequests", [{ field: "uid", value: uid }], { orderBy: "submittedAt", descending: true, limit: 1 });
  return rows[0] ?? null;
}

/** Magic-byte check: never trust the browser-supplied content type. */
export function detectImageType(bytes: Uint8Array): (typeof CONTENT_TYPES)[number] | null {
  if (bytes.length < 12) return null;
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return "image/png";
  const riff = String.fromCharCode(...bytes.subarray(0, 4));
  const webp = String.fromCharCode(...bytes.subarray(8, 12));
  if (riff === "RIFF" && webp === "WEBP") return "image/webp";
  return null;
}

function decodeBase64(value: string): Uint8Array<ArrayBuffer> {
  let binary: string;
  try { binary = atob(value.replace(/\s+/g, "")); } catch { throw new AppError("VALIDATION_FAILED", "The document could not be read. Choose the image again.", 400); }
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

async function sha256(bytes: Uint8Array<ArrayBuffer>): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function requireModerator(c: Context<AppBindings>): Promise<string> {
  const role = (await c.get("db").get(`roles/${c.get("uid")}`))?.role;
  if (role !== "MODERATOR" && role !== "ADMIN") throw new AppError("PERMISSION_DENIED", "Moderator access is required.", 403);
  return c.get("uid");
}

async function audit(c: Context<AppBindings>, entry: Record<string, unknown>): Promise<void> {
  const id = crypto.randomUUID();
  await c.get("db").commit([c.get("db").setWrite(`auditLogs/${id}`, { ...entry, createdAt: Date.now() })]);
}

async function setMembership(c: Context<AppBindings>, uid: string, status: MembershipRecord["status"], requestId: string): Promise<void> {
  const now = Date.now();
  await c.get("db").commit([c.get("db").setWrite(`users/${uid}`, { membershipStatus: status, membershipUpdatedAt: now, institutionId: INSTITUTION_ID, verificationRequestId: requestId }, ["membershipStatus", "membershipUpdatedAt", "institutionId", "verificationRequestId"])]);
}

/** Claims a unique student number for a uid. Returns false when another account owns it. */
async function claimStudentNumber(c: Context<AppBindings>, normalized: string, uid: string, requestId: string): Promise<boolean> {
  const db = c.get("db");
  const existing = await db.get(`studentDirectory/${normalized}`);
  if (existing && existing.uid !== uid) return false;
  await db.commit([db.setWrite(`studentDirectory/${normalized}`, { uid, verificationRequestId: requestId, updatedAt: Date.now() })]);
  return true;
}

async function notify(c: Context<AppBindings>, targetUid: string, body: string): Promise<void> {
  c.executionCtx.waitUntil(c.env.NOTIFICATION_QUEUE.send({
    kind: "MODERATION",
    targetUids: [targetUid],
    title: "Student verification",
    body,
    data: { route: "/verify" },
  }).catch(() => undefined));
}

export function registerVerificationRoutes(app: Hono<AppBindings>): void {
  app.get("/v1/membership", async (c) => {
    const db = c.get("db");
    const [user, request] = await Promise.all([db.get(`users/${c.get("uid")}`), latestRequest(db, c.get("uid"))]);
    return c.json({ ok: true, data: membershipOf(user, request) });
  });

  app.post("/v1/verification/scan", async (c) => {
    if (!(await c.env.SENSITIVE_RATE_LIMITER.limit({ key: `${c.get("uid")}:verification-scan` })).success) {
      throw new AppError("RATE_LIMITED", "You are submitting documents too quickly. Wait a moment and try again.", 429, true);
    }
    const db = c.get("db"), uid = c.get("uid");
    if ((await db.get(`users/${uid}`))?.membershipStatus === "VERIFIED") {
      throw new AppError("ALREADY_EXISTS", "Your membership is already verified.", 409);
    }
    const pending = await db.query("verificationRequests", [{ field: "uid", value: uid }, { field: "status", op: "IN", value: REVIEW_STATUSES }], { limit: 1 });
    if (pending.length) throw new AppError("ALREADY_EXISTS", "Your verification is already awaiting review. You will be notified when it is decided.", 409);

    const body = scanSchema.parse(await c.req.json().catch(() => { throw new AppError("VALIDATION_FAILED", "Send a valid JSON request body.", 400); }));
    const bytes = decodeBase64(body.imageBase64);
    if (bytes.length < 1024) throw new AppError("VALIDATION_FAILED", "That image is too small to read. Upload a clear photograph of your student ID.", 400);
    if (bytes.length > MAX_DOCUMENT_BYTES) throw new AppError("VALIDATION_FAILED", "The image must be 5 MB or smaller. Try a lower-resolution photo.", 400);
    const detected = detectImageType(bytes);
    if (!detected || detected !== body.contentType) throw new AppError("VALIDATION_FAILED", "Upload a JPEG, PNG or WebP photograph of the card.", 400);

    const requestId = crypto.randomUUID(), now = Date.now();
    const documentPath = verificationObjectPath(uid, requestId, detected);
    await uploadPrivateObject(c.env, documentPath, bytes, detected);

    const extraction = await ocrProviderFor(c.env).extract(bytes, detected);
    const parsed = extraction.text.trim() ? parseStudentId(extraction.text) : { fields: { studentNumber: "", fullName: "", programme: "", academicYear: "" }, confidence: {} };
    const readable = Boolean(parsed.fields.studentNumber || parsed.fields.fullName);
    const status: VerificationStatus = readable ? "DRAFT" : "EXTRACTION_FAILED";

    await db.create("verificationRequests", requestId, {
      uid,
      institutionId: INSTITUTION_ID,
      status,
      ...parsed.fields,
      confidence: parsed.confidence,
      ocrProvider: extraction.provider,
      ocrConfidence: extraction.confidence,
      matchOutcome: "UNAVAILABLE",
      matchConfidence: 0,
      documentPath,
      documentContentType: detected,
      documentBytes: bytes.length,
      documentHash: await sha256(bytes),
      submittedAt: now,
      updatedAt: now,
      attempt: 1,
    });

    return c.json({ ok: true, data: {
      requestId,
      status,
      fields: parsed.fields,
      confidence: parsed.confidence,
      ocrProvider: extraction.provider,
      extractionAvailable: extraction.provider !== "not-configured",
    } }, 201);
  });

  app.post("/v1/verification/confirm", async (c) => {
    const db = c.get("db"), uid = c.get("uid"), now = Date.now();
    const body = z.object({ requestId: z.string().uuid(), fields: fieldsSchema }).parse(await c.req.json().catch(() => { throw new AppError("VALIDATION_FAILED", "Send a valid JSON request body.", 400); }));
    const request = assertFound(await db.get(`verificationRequests/${body.requestId}`), "That verification request is no longer available. Start again.");
    if (request.uid !== uid) throw new AppError("PERMISSION_DENIED", "That verification request belongs to another account.", 403);
    if (!["DRAFT", "EXTRACTION_FAILED", "NEEDS_RESUBMISSION"].includes(String(request.status))) {
      throw new AppError("ALREADY_EXISTS", "This verification request has already been submitted.", 409);
    }

    const normalized = normalizeStudentNumber(body.fields.studentNumber);
    if (normalized.length < 3) throw new AppError("VALIDATION_FAILED", "Enter the student number printed on your ID card.", 400);
    const owner = await db.get(`studentDirectory/${normalized}`);
    if (owner && owner.uid !== uid) {
      await db.commit([db.setWrite(`verificationRequests/${request.id}`, { status: "NEEDS_RESUBMISSION", decisionReason: "This student number is already linked to another account.", updatedAt: now, decidedAt: now }, ["status", "decisionReason", "updatedAt", "decidedAt"])]);
      await setMembership(c, uid, "PENDING", request.id);
      await audit(c, { actorId: uid, targetId: uid, action: "VERIFICATION_DUPLICATE_STUDENT_NUMBER", requestId: request.id });
      throw new AppError("ALREADY_EXISTS", "That student number is already linked to another account. Contact a CBU Find moderator if this is wrong.", 409);
    }

    const match = await matcherFor(c.env).match(body.fields);
    const approved = canAutoApprove(match, Number((request.confidence as Record<string, number> | undefined)?.studentNumber ?? 0));
    const status: VerificationStatus = approved ? "APPROVED" : "PENDING_REVIEW";

    if (approved && !(await claimStudentNumber(c, normalized, uid, request.id))) {
      throw new AppError("ALREADY_EXISTS", "That student number was just linked to another account. Contact a moderator.", 409);
    }

    await db.commit([db.setWrite(`verificationRequests/${request.id}`, {
      status,
      studentNumber: body.fields.studentNumber,
      fullName: body.fields.fullName,
      programme: body.fields.programme,
      academicYear: body.fields.academicYear,
      normalizedStudentNumber: normalized,
      matchOutcome: match.outcome,
      matchConfidence: match.confidence,
      matchProvider: match.provider,
      updatedAt: now,
      submittedAt: now,
      ...(approved ? { decidedAt: now, decidedBy: "automatic-record-match" } : {}),
    }, ["status", "studentNumber", "fullName", "programme", "academicYear", "normalizedStudentNumber", "matchOutcome", "matchConfidence", "matchProvider", "updatedAt", "submittedAt", "decidedAt", "decidedBy"])]);

    await setMembership(c, uid, approved ? "VERIFIED" : "PENDING", request.id);
    await audit(c, { actorId: uid, targetId: uid, action: approved ? "VERIFICATION_AUTO_APPROVED" : "VERIFICATION_SUBMITTED", requestId: request.id, matchOutcome: match.outcome, matchConfidence: match.confidence });
    if (approved) await notify(c, uid, "Your student membership was verified.");

    return c.json({ ok: true, data: { requestId: request.id, status, matchOutcome: match.outcome, matchConfidence: match.confidence, autoApproved: approved } });
  });

  app.get("/v1/admin/verifications", async (c) => {
    await requireModerator(c);
    const requested = c.req.query("status");
    const status = requested && REVIEW_STATUSES.includes(requested as VerificationStatus) ? requested as VerificationStatus : "PENDING_REVIEW";
    const rows = await c.get("db").query("verificationRequests", [{ field: "status", value: status }], { orderBy: "submittedAt", limit: 50 });
    return c.json({ ok: true, data: { status, requests: rows.map((row) => ({ ...summaryOf(row), uid: row.uid, documentBytes: row.documentBytes ?? 0 })) } });
  });

  app.get("/v1/admin/verifications/:id/document", async (c) => {
    await requireModerator(c);
    const request = assertFound(await c.get("db").get(`verificationRequests/${c.req.param("id")}`), "That verification request is no longer available.");
    const stored = await downloadPrivateObject(c.env, String(request.documentPath ?? ""));
    if (!stored) throw new AppError("NOT_FOUND", "The uploaded document is no longer stored.", 404);
    await audit(c, { actorId: c.get("uid"), targetId: request.uid, action: "VERIFICATION_DOCUMENT_VIEWED", requestId: request.id });
    return new Response(stored.bytes as BodyInit, { headers: { "content-type": stored.contentType, "cache-control": "no-store", "content-disposition": "inline" } });
  });

  app.post("/v1/admin/verifications/:id/decision", async (c) => {
    const moderatorId = await requireModerator(c);
    const db = c.get("db"), now = Date.now();
    const body = decisionSchema.parse(await c.req.json().catch(() => { throw new AppError("VALIDATION_FAILED", "Send a valid JSON request body.", 400); }));
    const request = assertFound(await db.get(`verificationRequests/${c.req.param("id")}`), "That verification request is no longer available.");
    if (!REVIEW_STATUSES.includes(String(request.status) as VerificationStatus)) {
      throw new AppError("ALREADY_EXISTS", "This verification request has already been decided.", 409);
    }
    if (body.decision !== "APPROVE" && !body.reason) {
      throw new AppError("VALIDATION_FAILED", "Give the student a reason so they know what to fix.", 400);
    }

    const status: VerificationStatus = body.decision === "APPROVE" ? "APPROVED" : body.decision === "REJECT" ? "REJECTED" : "NEEDS_RESUBMISSION";
    if (body.decision === "APPROVE") {
      const normalized = String(request.normalizedStudentNumber ?? normalizeStudentNumber(String(request.studentNumber ?? "")));
      if (!(await claimStudentNumber(c, normalized, String(request.uid), request.id))) {
        throw new AppError("ALREADY_EXISTS", "That student number is already linked to another account. Reject this request and ask the student to contact support.", 409);
      }
    }

    await db.commit([db.setWrite(`verificationRequests/${request.id}`, { status, decisionReason: body.reason, decidedAt: now, decidedBy: moderatorId, updatedAt: now }, ["status", "decisionReason", "decidedAt", "decidedBy", "updatedAt"])]);
    await setMembership(c, String(request.uid), status === "APPROVED" ? "VERIFIED" : status === "REJECTED" ? "REJECTED" : "PENDING", request.id);
    await audit(c, { actorId: moderatorId, targetId: String(request.uid), action: `VERIFICATION_${body.decision}`, requestId: request.id, reason: body.reason });
    await notify(c, String(request.uid), status === "APPROVED" ? "Your student membership was verified." : status === "REJECTED" ? "Your verification was not approved. Open CBU Find for the reason." : "More information is needed for your verification.");

    return c.json({ ok: true, data: { requestId: request.id, status } });
  });
}

/** Removes every object and record tied to a deleted account. */
export async function purgeVerificationData(env: Env, db: AppBindings["Variables"]["db"], uid: string): Promise<void> {
  const requests = await db.query("verificationRequests", [{ field: "uid", value: uid }]);
  const writes: Array<Record<string, unknown>> = [];
  for (const request of requests) {
    if (request.documentPath) {
      try { await deletePrivateObject(env, String(request.documentPath)); }
      catch (error) { console.error(JSON.stringify({ event: "verification.document_delete_failed", requestId: request.id })); }
    }
    writes.push(db.deleteWrite(`verificationRequests/${request.id}`));
  }
  const directory = await db.query("studentDirectory", [{ field: "uid", value: uid }]);
  for (const entry of directory) writes.push(db.deleteWrite(`studentDirectory/${entry.id}`));
  for (let index = 0; index < writes.length; index += 400) await db.commit(writes.slice(index, index + 400));
}

import type { Context, Hono } from "hono";
import { z } from "zod";
import { DEFAULT_NOTIFICATION_PREFERENCES, deterministicClaimId, deterministicConversationId, type ApiResponse, type MediaAsset, type NotificationJob, type UserRole } from "@cbu-find/contracts";
import { AppError, assertFound } from "./errors";
import { FirestoreRest, type PlainDocument } from "./firestore";
import { claimSchema, mediaAssetSchema, messageEditSchema, messageSchema, notificationPreferencesSchema, profileSchema, reportAbuseSchema, reportSchema } from "./schemas";
import { destroyCloudinaryAsset } from "./queues";

export type AppBindings = { Bindings: Env; Variables: { uid: string; email: string; referenceId: string; startedAt: number; db: FirestoreRest } };

function ok<T>(data: T, status = 200): Response {
  const body: ApiResponse<T> = { ok: true, data };
  return Response.json(body, { status });
}

async function input<T>(request: Request, schema: z.ZodType<T>): Promise<T> {
  let body: unknown;
  try { body = await request.json(); } catch { throw new AppError("VALIDATION_FAILED", "Send a valid JSON request body.", 400); }
  return schema.parse(body);
}

async function roleFor(db: FirestoreRest, uid: string): Promise<UserRole> {
  const record = await db.get(`roles/${uid}`);
  const role = record?.role;
  return role === "ADMIN" || role === "MODERATOR" ? role : "USER";
}

async function requireModerator(db: FirestoreRest, uid: string): Promise<UserRole> {
  const role = await roleFor(db, uid);
  if (role === "USER") throw new AppError("PERMISSION_DENIED", "Moderator access is required.", 403);
  return role;
}

async function requireAdmin(db: FirestoreRest, uid: string): Promise<void> {
  if (await roleFor(db, uid) !== "ADMIN") throw new AppError("PERMISSION_DENIED", "Administrator access is required.", 403);
}

async function assertNotBlocked(db: FirestoreRest, first: string, second: string, transaction?: string): Promise<void> {
  const [outgoing, incoming] = await Promise.all([db.get(`blocks/${first}_${second}`, transaction), db.get(`blocks/${second}_${first}`, transaction)]);
  if (outgoing || incoming) throw new AppError("BLOCKED", "This action is unavailable because one account has blocked the other.", 403);
}

function publicProfile(profile: Record<string, unknown>, now: number) {
  return { name: profile.name ?? "Campus member", programme: profile.programme ?? "", photoUrl: profile.photoUrl ?? "", status: profile.status ?? "ACTIVE", updatedAt: now };
}

function configured(value: string, placeholder: string): boolean {
  return Boolean(value) && value !== placeholder;
}

export function moderationMessageContext(messages: PlainDocument[], targetId: string): Array<Record<string, unknown>> {
  const unique = [...new Map(messages.map((message) => [message.id, message])).values()]
    .sort((left, right) => Number(left.createdAt ?? 0) - Number(right.createdAt ?? 0) || left.id.localeCompare(right.id));
  const targetIndex = unique.findIndex((message) => message.id === targetId);
  if (targetIndex < 0) throw new AppError("NOT_FOUND", "That message is no longer available.", 404);
  return unique.slice(Math.max(0, targetIndex - 2), targetIndex + 3).map((message) => ({
    id: message.id,
    senderId: message.senderId,
    text: message.text ?? "",
    mediaType: message.mediaType ?? "",
    mediaUrl: message.mediaUrl ?? "",
    createdAt: message.createdAt,
  }));
}

export function assertOwnedMedia(assets: MediaAsset[], uid: string, folder: "reports" | "profiles" | "messages", cloudName: string): void {
  const prefix = `cbu_find/${folder}/${uid}/`;
  for (const asset of assets) {
    let url: URL;
    try { url = new URL(asset.secureUrl); } catch { throw new AppError("VALIDATION_FAILED", "The attachment URL is invalid. Upload it again and retry.", 400); }
    if (!asset.publicId || !asset.publicId.startsWith(prefix) || url.protocol !== "https:" || url.hostname !== "res.cloudinary.com" || !url.pathname.startsWith(`/${cloudName}/`)) {
      throw new AppError("VALIDATION_FAILED", "The attachment was not uploaded by this account. Upload it again and retry.", 400);
    }
  }
}

async function rateLimit(binding: RateLimit, key: string): Promise<void> {
  if (!(await binding.limit({ key })).success) throw new AppError("RATE_LIMITED", "You are doing that too quickly. Wait a moment and try again.", 429, true);
}

function enqueueNotification(c: Context<AppBindings>, job: NotificationJob): void {
  c.executionCtx.waitUntil(c.env.NOTIFICATION_QUEUE.send(job).catch((error) => {
    console.error(JSON.stringify({ event: "notification.enqueue_failed", referenceId: c.get("referenceId"), kind: job.kind, code: error instanceof Error ? error.name : "UNKNOWN" }));
  }));
}

export function initialMessageState() {
  // Firestore SDK model decoders on Android cannot assign null to primitive Long
  // properties. Keep these timestamps numeric from the first write while older
  // null-valued records remain readable through the clients' compatibility mapper.
  return { editedAt: 0, deleted: false, deletedAt: 0 } as const;
}

export function registerDomainRoutes(app: Hono<AppBindings>): void {
  app.post("/v1/profile/bootstrap", async (c) => {
    const uid = c.get("uid"), now = Date.now(), db = c.get("db");
    const body = await input(c.req.raw, profileSchema);
    const existing = await db.get(`users/${uid}`);
    if (body.photoAsset) assertOwnedMedia([body.photoAsset], uid, "profiles", c.env.CLOUDINARY_CLOUD_NAME);
    const profile = { ...body, photoUrl: body.photoAsset?.secureUrl ?? existing?.photoUrl ?? "", photoAsset: body.photoAsset ?? existing?.photoAsset, email: c.get("email"), status: typeof existing?.status === "string" ? existing.status : "ACTIVE", createdAt: typeof existing?.createdAt === "number" ? existing.createdAt : now, updatedAt: now };
    await db.commit([
      db.setWrite(`users/${uid}`, profile),
      db.setWrite(`publicProfiles/${uid}`, publicProfile(profile, now)),
      ...(existing ? [] : [db.setWrite(`roles/${uid}`, { role: "USER", updatedAt: now })]),
    ]);
    return ok({ id: uid, ...profile }, existing ? 200 : 201);
  });

  app.patch("/v1/profile", async (c) => {
    const uid = c.get("uid"), now = Date.now(), db = c.get("db");
    const body = await input(c.req.raw, profileSchema);
    const existing = assertFound(await db.get(`users/${uid}`), "Your profile is not ready yet. Sign out, sign in, and retry.");
    if (body.photoAsset) assertOwnedMedia([body.photoAsset], uid, "profiles", c.env.CLOUDINARY_CLOUD_NAME);
    if (!body.photoAsset && body.photoUrl && body.photoUrl !== existing.photoUrl) throw new AppError("VALIDATION_FAILED", "Upload profile photos through CBU Find before saving.", 400);
    const privateData = { ...body, photoUrl: body.photoAsset?.secureUrl ?? existing.photoUrl ?? "", photoAsset: body.photoAsset ?? existing.photoAsset, email: c.get("email") || existing.email || "", updatedAt: now };
    await db.commit([db.setWrite(`users/${uid}`, privateData, Object.keys(privateData)), db.setWrite(`publicProfiles/${uid}`, publicProfile({ ...existing, ...privateData } as PlainDocument, now))]);
    return ok({ ...existing, ...privateData, id: uid });
  });

  app.post("/v1/uploads/sign", async (c) => {
    await rateLimit(c.env.SENSITIVE_RATE_LIMITER, `${c.get("uid")}:upload-sign`);
    const body = await input(c.req.raw, z.object({ folder: z.enum(["reports", "profiles", "messages"]), resourceType: z.enum(["image", "video", "raw", "auto"]).default("auto") }));
    const timestamp = Math.floor(Date.now() / 1000);
    const folder = `cbu_find/${body.folder}/${c.get("uid")}`;
    const context = `app=cbu_find|uploaded_by=${c.get("uid")}|kind=${body.folder}`;
    const params = `context=${context}&folder=${folder}&timestamp=${timestamp}${c.env.CLOUDINARY_API_SECRET}`;
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(params));
    const signature = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
    return ok({ cloudName: c.env.CLOUDINARY_CLOUD_NAME, apiKey: c.env.CLOUDINARY_API_KEY, signature, signatureAlgorithm: "sha256", timestamp, folder, context, resourceType: body.resourceType, expiresAt: (timestamp + 3600) * 1000 });
  });

  app.post("/v1/reports", async (c) => {
    const uid = c.get("uid"), now = Date.now(), db = c.get("db"), body = await input(c.req.raw, reportSchema);
    assertOwnedMedia(body.media, uid, "reports", c.env.CLOUDINARY_CLOUD_NAME);
    const id = crypto.randomUUID();
    const item = { ...body, contactInfo: undefined, userId: uid, status: "ACTIVE", createdAt: now, updatedAt: now, imageUrls: body.media.map((asset) => asset.secureUrl), imageUri: body.media[0]?.secureUrl ?? null };
    await db.commit([db.setWrite(`items/${id}`, item), db.setWrite(`reportContacts/${id}`, { itemId: id, ownerId: uid, contactInfo: body.contactInfo, updatedAt: now })]);
    return ok({ id, ...item }, 201);
  });

  app.post("/v1/reports/:id/status", async (c) => {
    const db = c.get("db"), uid = c.get("uid"), id = c.req.param("id"), now = Date.now();
    const body = await input(c.req.raw, z.object({ status: z.enum(["ACTIVE", "RESOLVED"]) }));
    const transaction = await db.beginTransaction();
    const item = assertFound(await db.get(`items/${id}`, transaction), "That report is no longer available.");
    const role = await roleFor(db, uid);
    if (item.userId !== uid && role === "USER") throw new AppError("PERMISSION_DENIED", "Only the report owner can change its status.", 403);
    if (item.status === "REMOVED") throw new AppError("PERMISSION_DENIED", "A removed report must be restored by a moderator.", 403);
    const accepted = await db.query("claims", [{ field: "itemId", value: id }, { field: "status", value: "ACCEPTED" }], { transaction });
    const update = { status: body.status, matchedClaimId: body.status === "ACTIVE" ? null : item.matchedClaimId ?? null, updatedAt: now, resolvedAt: body.status === "RESOLVED" ? now : null };
    const writes = [
      db.setWrite(`items/${id}`, update, ["status", "matchedClaimId", "updatedAt", "resolvedAt"]),
      ...(body.status === "ACTIVE" ? accepted.flatMap((claim) => [
        db.setWrite(`claims/${claim.id}`, { status: "REJECTED", updatedAt: now }, ["status", "updatedAt"]),
        ...(claim.conversationId ? [db.setWrite(`conversations/${claim.conversationId}`, { closed: true, closedReason: "REPORT_REOPENED", closedAt: now, updatedAt: now }, ["closed", "closedReason", "closedAt", "updatedAt"])] : []),
      ]) : []),
    ];
    await db.commit(writes, transaction);
    const job: NotificationJob = { kind: "REPORT_STATUS", targetUids: accepted.map((claim) => String(claim.claimantId)), title: "Report updated", body: body.status === "RESOLVED" ? "A matched report was marked resolved." : "A report was reopened.", data: { itemId: id, status: body.status } };
    if (job.targetUids.length) enqueueNotification(c, job);
    return ok({ id, ...update });
  });

  app.get("/v1/reports/:id/contact", async (c) => {
    const db = c.get("db"), uid = c.get("uid"), id = c.req.param("id");
    const contact = assertFound(await db.get(`reportContacts/${id}`), "Contact details are not available for this report.");
    if (contact.ownerId !== uid) {
      const accepted = await db.get(`claims/${deterministicClaimId(id, uid)}`);
      if (!accepted || accepted.status !== "ACCEPTED") throw new AppError("PERMISSION_DENIED", "Contact details become available after the report owner accepts your claim.", 403);
    }
    return ok({ contactInfo: contact.contactInfo ?? "" });
  });

  app.post("/v1/claims", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), body = await input(c.req.raw, claimSchema), now = Date.now();
    await rateLimit(c.env.SENSITIVE_RATE_LIMITER, `${uid}:claim`);
    const item = assertFound(await db.get(`items/${body.itemId}`), "That report is no longer available.");
    if (item.userId === uid) throw new AppError("VALIDATION_FAILED", "You cannot claim your own report.", 400);
    if (item.status !== "ACTIVE") throw new AppError("CLAIM_CONFLICT", "This report is no longer accepting claims.", 409);
    await assertNotBlocked(db, uid, String(item.userId));
    const id = deterministicClaimId(body.itemId, uid);
    if (await db.get(`claims/${id}`)) throw new AppError("ALREADY_EXISTS", "You already submitted a claim for this report.", 409);
    const claim = { ...body, itemOwnerId: item.userId, claimantId: uid, status: "PENDING", createdAt: now, updatedAt: now };
    await db.create("claims", id, claim);
    enqueueNotification(c, { kind: "CLAIM_CREATED", targetUids: [String(item.userId)], title: "New claim", body: "Someone responded to your report.", data: { claimId: id, itemId: body.itemId } });
    return ok({ id, ...claim }, 201);
  });

  app.post("/v1/claims/:id/action", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), id = c.req.param("id"), now = Date.now();
    const { action } = await input(c.req.raw, z.object({ action: z.enum(["ACCEPT", "REJECT", "CANCEL"]) }));
    await rateLimit(c.env.SENSITIVE_RATE_LIMITER, `${uid}:claim-action`);
    const transaction = await db.beginTransaction();
    const claim = assertFound(await db.get(`claims/${id}`, transaction), "That claim is no longer available.");
    if (claim.status !== "PENDING") throw new AppError("CLAIM_CONFLICT", "This claim has already been decided.", 409);
    const isOwner = claim.itemOwnerId === uid, isClaimant = claim.claimantId === uid;
    if ((action === "CANCEL" && !isClaimant) || (action !== "CANCEL" && !isOwner)) throw new AppError("PERMISSION_DENIED", "You cannot perform that claim action.", 403);
    if (action !== "ACCEPT") {
      const status = action === "CANCEL" ? "CANCELLED" : "REJECTED";
      await db.commit([db.setWrite(`claims/${id}`, { status, updatedAt: now }, ["status", "updatedAt"])], transaction);
      if (action === "REJECT") enqueueNotification(c, { kind: "CLAIM_DECIDED", targetUids: [String(claim.claimantId)], title: "Claim update", body: "The report owner reviewed your claim.", data: { claimId: id, status } });
      return ok({ id, status });
    }
    const item = assertFound(await db.get(`items/${claim.itemId}`, transaction), "The report for this claim is no longer available.");
    if (item.status !== "ACTIVE") throw new AppError("CLAIM_CONFLICT", "Another claim was accepted first.", 409, true);
    await assertNotBlocked(db, String(claim.itemOwnerId), String(claim.claimantId), transaction);
    const [owner, claimant, pending] = await Promise.all([
      db.get(`publicProfiles/${claim.itemOwnerId}`, transaction), db.get(`publicProfiles/${claim.claimantId}`, transaction),
      db.query("claims", [{ field: "itemId", value: claim.itemId }, { field: "status", value: "PENDING" }], { transaction }),
    ]);
    const conversationId = deterministicConversationId(String(claim.itemId), String(claim.itemOwnerId), String(claim.claimantId));
    const writes = [
      db.setWrite(`items/${claim.itemId}`, { status: "MATCHED", matchedClaimId: id, updatedAt: now }, ["status", "matchedClaimId", "updatedAt"]),
      ...pending.map((entry) => db.setWrite(`claims/${entry.id}`, entry.id === id ? { status: "ACCEPTED", conversationId, updatedAt: now } : { status: "REJECTED", updatedAt: now }, entry.id === id ? ["status", "conversationId", "updatedAt"] : ["status", "updatedAt"])),
      db.setWrite(`conversations/${conversationId}`, {
        participantIds: [claim.itemOwnerId, claim.claimantId].sort(),
        participantNames: { [String(claim.itemOwnerId)]: owner?.name ?? "Campus member", [String(claim.claimantId)]: claimant?.name ?? "Campus member" },
        participantPhotoUrls: { [String(claim.itemOwnerId)]: owner?.photoUrl ?? "", [String(claim.claimantId)]: claimant?.photoUrl ?? "" },
        itemId: claim.itemId, claimId: id, itemTitle: item.title ?? "Matched report", itemImageUrl: (item.imageUrls as string[] | undefined)?.[0] ?? item.imageUri ?? "",
        createdAt: now, updatedAt: now, lastMessage: "", lastMessageType: "TEXT", lastSenderId: "", lastReadAt: { [uid]: now }, locked: false, closed: false,
      }),
    ];
    await db.commit(writes, transaction);
    enqueueNotification(c, { kind: "CLAIM_DECIDED", targetUids: [String(claim.claimantId)], title: "Claim accepted", body: "Your claim was accepted. You can now message the report owner.", data: { claimId: id, conversationId, status: "ACCEPTED" } });
    return ok({ id, status: "ACCEPTED", conversationId });
  });

  app.post("/v1/conversations/:id/messages", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), id = c.req.param("id"), now = Date.now();
    await rateLimit(c.env.MESSAGE_RATE_LIMITER, `${uid}:message`);
    const body = await input(c.req.raw, messageSchema), conversation = assertFound(await db.get(`conversations/${id}`), "That conversation is no longer available.");
    if (body.attachment) assertOwnedMedia([body.attachment], uid, "messages", c.env.CLOUDINARY_CLOUD_NAME);
    const participants = (conversation.participantIds as string[] | undefined) ?? [];
    if (!participants.includes(uid)) throw new AppError("PERMISSION_DENIED", "You are not a participant in this conversation.", 403);
    if (conversation.locked === true) throw new AppError("CONVERSATION_LOCKED", "A moderator locked this conversation. New messages are disabled.", 403);
    if (conversation.closed === true) throw new AppError("CONVERSATION_LOCKED", "This match was reopened, so new messages are disabled. Existing history remains visible.", 403);
    const recipient = participants.find((value) => value !== uid);
    if (!recipient) throw new AppError("VALIDATION_FAILED", "This conversation has no recipient.", 400);
    await assertNotBlocked(db, uid, recipient);
    const messageId = crypto.randomUUID();
    const attachment = body.attachment;
    const preview = body.text || (attachment?.type === "IMAGE" ? "Sent a photo" : attachment?.type === "VIDEO" ? "Sent a video" : "Sent an attachment");
    const lastReadAt = { ...((conversation.lastReadAt as Record<string, number> | undefined) ?? {}), [uid]: now };
    const message = { senderId: uid, text: body.text, mediaUrl: attachment?.secureUrl ?? "", mediaPublicId: attachment?.publicId ?? "", mediaResourceType: attachment?.resourceType ?? "", mediaType: attachment?.type ?? "", mediaName: attachment?.name ?? attachment?.originalName ?? "", mediaSizeBytes: attachment?.bytes ?? 0, createdAt: now, ...initialMessageState() };
    await db.commit([db.setWrite(`conversations/${id}/messages/${messageId}`, message), db.setWrite(`conversations/${id}`, { updatedAt: now, lastMessageId: messageId, lastMessage: preview.slice(0, 160), lastMessageType: attachment?.type ?? "TEXT", lastSenderId: uid, lastReadAt }, ["updatedAt", "lastMessageId", "lastMessage", "lastMessageType", "lastSenderId", "lastReadAt"])]);
    enqueueNotification(c, { kind: "MESSAGE", targetUids: [recipient], title: "New message", body: preview.slice(0, 120), hideSensitiveBody: true, data: { conversationId: id, messageId } });
    return ok({ id: messageId, ...message }, 201);
  });

  app.patch("/v1/conversations/:id/messages/:messageId", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), id = c.req.param("id"), messageId = c.req.param("messageId"), now = Date.now();
    await rateLimit(c.env.MESSAGE_RATE_LIMITER, `${uid}:message-edit`);
    const body = await input(c.req.raw, messageEditSchema);
    const conversation = assertFound(await db.get(`conversations/${id}`), "That conversation is no longer available.");
    const participants = (conversation.participantIds as string[] | undefined) ?? [];
    if (!participants.includes(uid)) throw new AppError("PERMISSION_DENIED", "You are not a participant in this conversation.", 403);
    if (conversation.locked === true || conversation.closed === true) throw new AppError("CONVERSATION_LOCKED", "This conversation is read-only, so messages cannot be edited.", 403);
    const recipient = participants.find((value) => value !== uid);
    if (recipient) await assertNotBlocked(db, uid, recipient);
    const message = assertFound(await db.get(`conversations/${id}/messages/${messageId}`), "That message is no longer available.");
    if (message.senderId !== uid) throw new AppError("PERMISSION_DENIED", "You can edit only messages you sent.", 403);
    if (message.deleted === true) throw new AppError("VALIDATION_FAILED", "A deleted message cannot be edited.", 409);
    if (!body.text && !message.mediaUrl) throw new AppError("VALIDATION_FAILED", "An attachment-free message cannot be empty. Delete it instead.", 400);
    const writes = [db.setWrite(`conversations/${id}/messages/${messageId}`, { text: body.text, editedAt: now }, ["text", "editedAt"]), db.setWrite(`auditLogs/${crypto.randomUUID()}`, { actorId: uid, action: "MESSAGE_EDITED", targetId: messageId, conversationId: id, createdAt: now })];
    if (conversation.lastMessageId === messageId || (!conversation.lastMessageId && conversation.lastSenderId === uid && conversation.lastMessage === message.text)) {
      const preview = body.text || messageAttachmentPreview(String(message.mediaType ?? ""));
      writes.push(db.setWrite(`conversations/${id}`, { lastMessageId: messageId, lastMessage: preview.slice(0, 160), lastMessageType: message.mediaType || "TEXT" }, ["lastMessageId", "lastMessage", "lastMessageType"]));
    }
    await db.commit(writes);
    return ok({ id: messageId, text: body.text, editedAt: now });
  });

  app.delete("/v1/conversations/:id/messages/:messageId", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), id = c.req.param("id"), messageId = c.req.param("messageId"), now = Date.now();
    await rateLimit(c.env.MESSAGE_RATE_LIMITER, `${uid}:message-delete`);
    const conversation = assertFound(await db.get(`conversations/${id}`), "That conversation is no longer available.");
    if (!((conversation.participantIds as string[] | undefined) ?? []).includes(uid)) throw new AppError("PERMISSION_DENIED", "You are not a participant in this conversation.", 403);
    const message = assertFound(await db.get(`conversations/${id}/messages/${messageId}`), "That message is no longer available.");
    if (message.senderId !== uid) throw new AppError("PERMISSION_DENIED", "You can delete only messages you sent.", 403);
    if (message.deleted === true) return ok({ id: messageId, deleted: true, deletedAt: message.deletedAt ?? now });
    if (typeof message.mediaPublicId === "string" && message.mediaPublicId) {
      await destroyCloudinaryAsset(c.env, { secureUrl: String(message.mediaUrl ?? ""), publicId: message.mediaPublicId, resourceType: String(message.mediaResourceType ?? "auto") as "image" | "video" | "raw" | "auto" });
    }
    const writes = [
      db.setWrite(`conversations/${id}/messages/${messageId}`, { text: "", mediaUrl: "", mediaPublicId: "", mediaResourceType: "", mediaType: "", mediaName: "", mediaSizeBytes: 0, deleted: true, deletedAt: now }, ["text", "mediaUrl", "mediaPublicId", "mediaResourceType", "mediaType", "mediaName", "mediaSizeBytes", "deleted", "deletedAt"]),
      db.setWrite(`auditLogs/${crypto.randomUUID()}`, { actorId: uid, action: "MESSAGE_DELETED", targetId: messageId, conversationId: id, createdAt: now }),
    ];
    if (conversation.lastMessageId === messageId || (!conversation.lastMessageId && conversation.lastSenderId === uid && conversation.lastMessage === message.text)) {
      writes.push(db.setWrite(`conversations/${id}`, { lastMessageId: messageId, lastMessage: "Message deleted", lastMessageType: "DELETED", updatedAt: now }, ["lastMessageId", "lastMessage", "lastMessageType", "updatedAt"]));
    }
    await db.commit(writes);
    return ok({ id: messageId, deleted: true, deletedAt: now });
  });

  app.post("/v1/conversations/:id/read", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), id = c.req.param("id"), conversation = assertFound(await db.get(`conversations/${id}`), "That conversation is no longer available.");
    if (!((conversation.participantIds as string[] | undefined) ?? []).includes(uid)) throw new AppError("PERMISSION_DENIED", "You are not a participant in this conversation.", 403);
    await db.patch(`conversations/${id}`, { lastReadAt: { ...((conversation.lastReadAt as Record<string, number> | undefined) ?? {}), [uid]: Date.now() } });
    return ok({ id, read: true });
  });

  app.get("/v1/blocks", async (c) => {
    const uid = c.get("uid");
    const rows = await c.get("db").query("blocks", [{ field: "ownerId", value: uid }], { limit: 200 });
    return ok({ targetUids: rows.map((row) => String(row.targetId ?? "")).filter(Boolean) });
  });

  app.post("/v1/blocks", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), body = await input(c.req.raw, z.object({ targetUid: z.string().min(1).max(500) }));
    if (body.targetUid === uid) throw new AppError("VALIDATION_FAILED", "You cannot block your own account.", 400);
    assertFound(await db.get(`users/${body.targetUid}`), "That account is no longer available.");
    await db.commit([db.setWrite(`blocks/${uid}_${body.targetUid}`, { ownerId: uid, targetId: body.targetUid, createdAt: Date.now() })]);
    return ok({ targetUid: body.targetUid, blocked: true }, 201);
  });

  app.delete("/v1/blocks/:targetUid", async (c) => {
    await c.get("db").remove(`blocks/${c.get("uid")}_${c.req.param("targetUid")}`);
    return ok({ targetUid: c.req.param("targetUid"), blocked: false });
  });

  app.post("/v1/abuse-reports", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), body = await input(c.req.raw, reportAbuseSchema), now = Date.now();
    await rateLimit(c.env.SENSITIVE_RATE_LIMITER, `${uid}:abuse-report`);
    let context: unknown[] = [];
    let subjectUserId = "";
    if (body.targetType === "MESSAGE") {
      if (!body.conversationId) throw new AppError("VALIDATION_FAILED", "Choose the conversation containing the reported message.", 400);
      const conversation = assertFound(await db.get(`conversations/${body.conversationId}`), "That conversation is no longer available.");
      if (!((conversation.participantIds as string[] | undefined) ?? []).includes(uid)) throw new AppError("PERMISSION_DENIED", "Only a conversation participant can report its messages.", 403);
      const parent = `conversations/${body.conversationId}`;
      const target = assertFound(await db.get(`${parent}/messages/${body.targetId}`), "That message is no longer available.");
      subjectUserId = String(target.senderId ?? "");
      const createdAt = Number(target.createdAt);
      let candidates = [target];
      if (Number.isFinite(createdAt)) {
        const [before, sameTime, after] = await Promise.all([
          db.query("messages", [{ field: "createdAt", op: "LESS_THAN", value: createdAt }], { parent, orderBy: "createdAt", descending: true, limit: 2 }),
          db.query("messages", [{ field: "createdAt", value: createdAt }], { parent, limit: 25 }),
          db.query("messages", [{ field: "createdAt", op: "GREATER_THAN", value: createdAt }], { parent, orderBy: "createdAt", limit: 2 }),
        ]);
        candidates = [...before, ...sameTime, ...after, target];
      }
      context = moderationMessageContext(candidates, body.targetId);
    } else if (body.targetType === "REPORT") {
      const report = assertFound(await db.get(`items/${body.targetId}`), "That report is no longer available.");
      subjectUserId = String(report.userId ?? "");
    } else {
      assertFound(await db.get(`users/${body.targetId}`), "That account is no longer available.");
      subjectUserId = body.targetId;
    }
    const id = crypto.randomUUID();
    await db.create("moderationCases", id, { reporterId: uid, ...body, subjectUserId, status: "OPEN", context, createdAt: now, updatedAt: now });
    return ok({ id, status: "OPEN", referenceId: c.get("referenceId") }, 201);
  });

  app.put("/v1/devices", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), body = await input(c.req.raw, z.object({ token: z.string().min(20).max(4096), platform: z.enum(["ANDROID", "WEB"]) }));
    const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(body.token));
    const id = `${uid}_${[...new Uint8Array(hash)].slice(0, 12).map((value) => value.toString(16).padStart(2, "0")).join("")}`;
    await db.commit([db.setWrite(`deviceTokens/${id}`, { uid, ...body, enabled: true, updatedAt: Date.now() })]);
    return ok({ id, registered: true });
  });

  app.get("/v1/notification-preferences", async (c) => {
    const stored = await c.get("db").get(`notificationPreferences/${c.get("uid")}`);
    return ok(Object.fromEntries(Object.keys(DEFAULT_NOTIFICATION_PREFERENCES).map((key) => [key, typeof stored?.[key] === "boolean" ? stored[key] : DEFAULT_NOTIFICATION_PREFERENCES[key as keyof typeof DEFAULT_NOTIFICATION_PREFERENCES]])));
  });

  app.put("/v1/notification-preferences", async (c) => {
    const body = await input(c.req.raw, notificationPreferencesSchema);
    await c.get("db").commit([c.get("db").setWrite(`notificationPreferences/${c.get("uid")}`, { ...DEFAULT_NOTIFICATION_PREFERENCES, ...body, updatedAt: Date.now() })]);
    return ok(body);
  });

  app.post("/v1/account-deletion", async (c) => {
    const uid = c.get("uid"), db = c.get("db"), now = Date.now();
    await rateLimit(c.env.SENSITIVE_RATE_LIMITER, `${uid}:delete-account`);
    const profile = assertFound(await db.get(`users/${uid}`), "Your account profile is not available.");
    if (profile.status === "DELETING") {
      const existingJob = await db.get(`deletionJobs/${uid}`);
      const referenceId = String(existingJob?.referenceId ?? c.get("referenceId"));
      await c.env.DELETION_QUEUE.send({ uid, requestedAt: Number(existingJob?.requestedAt ?? now), referenceId, attempt: Number(existingJob?.attempt ?? 0) });
      return ok({ status: "DELETING", referenceId }, 202);
    }
    const referenceId = c.get("referenceId");
    await db.commit([db.setWrite(`users/${uid}`, { status: "DELETING", deletionRequestedAt: now, updatedAt: now }, ["status", "deletionRequestedAt", "updatedAt"]), db.setWrite(`deletionJobs/${uid}`, { uid, status: "QUEUED", requestedAt: now, updatedAt: now, referenceId })]);
    await c.env.DELETION_QUEUE.send({ uid, requestedAt: now, referenceId, attempt: 0 });
    return ok({ status: "DELETING", referenceId }, 202);
  });

  app.get("/v1/diagnostics", async (c) => ok({ environment: c.env.ENVIRONMENT, requestId: c.get("referenceId"), firebaseProjectConfigured: configured(c.env.FIREBASE_PROJECT_ID, "replace-me"), cloudinaryConfigured: configured(c.env.CLOUDINARY_CLOUD_NAME, "replace-me"), webAppConfigured: !c.env.WEB_APP_URL.includes("example.invalid"), user: { uid: c.get("uid") } }));

  app.get("/v1/admin/cases", async (c) => {
    await requireModerator(c.get("db"), c.get("uid"));
    return ok({ cases: await c.get("db").query("moderationCases", [], { orderBy: "updatedAt", descending: true, limit: 100 }) });
  });

  app.post("/v1/admin/cases/:id/action", async (c) => {
    const db = c.get("db"), uid = c.get("uid"), role = await requireModerator(db, uid), id = c.req.param("id"), now = Date.now();
    const body = await input(c.req.raw, z.object({ action: z.enum(["DISMISS", "REMOVE_REPORT", "RESTORE_REPORT", "LOCK_CONVERSATION", "UNLOCK_CONVERSATION", "SUSPEND_USER", "REACTIVATE_USER"]), targetId: z.string().min(1).max(500) }));
    const moderationCase = assertFound(await db.get(`moderationCases/${id}`), "That moderation case is no longer available.");
    if (moderationCase.status !== "OPEN") throw new AppError("CLAIM_CONFLICT", "This moderation case has already been resolved.", 409);
    const expectedTarget = body.action === "LOCK_CONVERSATION" || body.action === "UNLOCK_CONVERSATION"
      ? moderationCase.conversationId
      : body.action === "SUSPEND_USER" || body.action === "REACTIVATE_USER"
        ? moderationCase.subjectUserId
        : moderationCase.targetId;
    if (body.targetId !== expectedTarget) throw new AppError("VALIDATION_FAILED", "This case does not apply to that target.", 400);
    const notificationTargets = new Set<string>([String(moderationCase.reporterId ?? "")]);
    const notificationData: Record<string, string> = { caseId: id, action: body.action };
    if ((body.action === "REMOVE_REPORT" || body.action === "RESTORE_REPORT") && (moderationCase.targetType !== "REPORT" || moderationCase.targetId !== body.targetId)) {
      throw new AppError("VALIDATION_FAILED", "This case does not apply to that report.", 400);
    }
    if (body.action === "REMOVE_REPORT" || body.action === "RESTORE_REPORT") {
      const report = assertFound(await db.get(`items/${body.targetId}`), "That report is no longer available.");
      notificationTargets.add(String(report.userId ?? ""));
      notificationData.itemId = body.targetId;
    }
    if ((body.action === "LOCK_CONVERSATION" || body.action === "UNLOCK_CONVERSATION") && (moderationCase.targetType !== "MESSAGE" || moderationCase.conversationId !== body.targetId)) {
      throw new AppError("VALIDATION_FAILED", "This case does not apply to that conversation.", 400);
    }
    if (body.action === "LOCK_CONVERSATION" || body.action === "UNLOCK_CONVERSATION") {
      const conversation = assertFound(await db.get(`conversations/${body.targetId}`), "That conversation is no longer available.");
      for (const participant of (conversation.participantIds as string[] | undefined) ?? []) notificationTargets.add(participant);
      notificationData.conversationId = body.targetId;
    }
    if (body.action === "SUSPEND_USER" || body.action === "REACTIVATE_USER") {
      let relevantUserIds: string[] = [];
      if (moderationCase.subjectUserId) relevantUserIds = [String(moderationCase.subjectUserId)];
      else if (moderationCase.targetType === "USER") relevantUserIds = [String(moderationCase.targetId)];
      else if (moderationCase.targetType === "MESSAGE") relevantUserIds = ((moderationCase.context as Array<Record<string, unknown>> | undefined) ?? []).filter((message) => message.id === moderationCase.targetId).map((message) => String(message.senderId ?? ""));
      else if (moderationCase.targetType === "REPORT") {
        const report = await db.get(`items/${moderationCase.targetId}`);
        if (report?.userId) relevantUserIds = [String(report.userId)];
      }
      if (!relevantUserIds.includes(body.targetId)) throw new AppError("VALIDATION_FAILED", "This case does not apply to that account.", 400);
      assertFound(await db.get(`users/${body.targetId}`), "That account is no longer available.");
      notificationTargets.add(body.targetId);
      if (body.targetId === uid) throw new AppError("VALIDATION_FAILED", "You cannot change your own suspension status.", 400);
      if (role !== "ADMIN" && await roleFor(db, body.targetId) === "ADMIN") throw new AppError("PERMISSION_DENIED", "Only an administrator can change another administrator's account status.", 403);
    }
    const actionMap: Record<string, { path: string; data: Record<string, unknown>; fields: string[] }> = {
      REMOVE_REPORT: { path: `items/${body.targetId}`, data: { status: "REMOVED", moderatedAt: now, moderatedBy: uid, updatedAt: now }, fields: ["status", "moderatedAt", "moderatedBy", "updatedAt"] },
      RESTORE_REPORT: { path: `items/${body.targetId}`, data: { status: "ACTIVE", moderatedAt: now, moderatedBy: uid, updatedAt: now }, fields: ["status", "moderatedAt", "moderatedBy", "updatedAt"] },
      LOCK_CONVERSATION: { path: `conversations/${body.targetId}`, data: { locked: true, moderatedAt: now, moderatedBy: uid }, fields: ["locked", "moderatedAt", "moderatedBy"] },
      UNLOCK_CONVERSATION: { path: `conversations/${body.targetId}`, data: { locked: false, moderatedAt: now, moderatedBy: uid }, fields: ["locked", "moderatedAt", "moderatedBy"] },
      SUSPEND_USER: { path: `users/${body.targetId}`, data: { status: "SUSPENDED", moderatedAt: now, moderatedBy: uid, updatedAt: now }, fields: ["status", "moderatedAt", "moderatedBy", "updatedAt"] },
      REACTIVATE_USER: { path: `users/${body.targetId}`, data: { status: "ACTIVE", moderatedAt: now, moderatedBy: uid, updatedAt: now }, fields: ["status", "moderatedAt", "moderatedBy", "updatedAt"] },
    };
    const write = actionMap[body.action];
    const writes = [db.setWrite(`moderationCases/${id}`, { status: body.action === "DISMISS" ? "DISMISSED" : "ACTIONED", resolution: body.action, resolvedBy: uid, updatedAt: now }, ["status", "resolution", "resolvedBy", "updatedAt"]), ...(write ? [db.setWrite(write.path, write.data, write.fields)] : []), db.setWrite(`auditLogs/${crypto.randomUUID()}`, { actorId: uid, actorRole: role, action: body.action, caseId: id, targetId: body.targetId, createdAt: now })];
    if (body.action === "SUSPEND_USER" || body.action === "REACTIVATE_USER") {
      writes.push(db.setWrite(`publicProfiles/${body.targetId}`, { status: body.action === "SUSPEND_USER" ? "SUSPENDED" : "ACTIVE", updatedAt: now }, ["status", "updatedAt"]));
    }
    await db.commit(writes);
    const targetUids = [...notificationTargets].filter((targetUid) => targetUid && targetUid !== uid);
    if (targetUids.length) enqueueNotification(c, { kind: "MODERATION", targetUids, title: "Moderation update", body: "A moderator updated a CBU Find report or account action.", data: notificationData, hideSensitiveBody: true });
    return ok({ id, action: body.action });
  });

  app.put("/v1/admin/roles/:uid", async (c) => {
    const db = c.get("db"), actor = c.get("uid"), target = c.req.param("uid");
    await requireAdmin(db, actor);
    const { role } = await input(c.req.raw, z.object({ role: z.enum(["USER", "MODERATOR", "ADMIN"]) }));
    assertFound(await db.get(`users/${target}`), "That account is no longer available.");
    if (target === actor && role !== "ADMIN") {
      const admins = await db.query("roles", [{ field: "role", value: "ADMIN" }]);
      if (admins.length <= 1) throw new AppError("CLAIM_CONFLICT", "The last administrator cannot remove their own administrator role.", 409);
    }
    await db.commit([db.setWrite(`roles/${target}`, { role, updatedAt: Date.now(), updatedBy: actor })]);
    if (target !== actor) enqueueNotification(c, { kind: "MODERATION", targetUids: [target], title: "Account role updated", body: "Your CBU Find account role was updated.", data: { role }, hideSensitiveBody: true });
    return ok({ uid: target, role });
  });
}

export function messageAttachmentPreview(mediaType: string): string {
  return mediaType === "IMAGE" ? "Sent a photo" : mediaType === "VIDEO" ? "Sent a video" : "Sent an attachment";
}

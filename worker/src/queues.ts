import type { DeletionJob, MediaAsset, NotificationJob } from "@cbu-find/contracts";
import { FirestoreRest, type PlainDocument } from "./firestore";
import { googleAccessToken } from "./google";
import { AppError } from "./errors";

export async function processNotification(env: Env, job: NotificationJob): Promise<void> {
  const db = new FirestoreRest(env);
  const recipients = [...new Set(job.targetUids)].filter(Boolean);
  for (const uid of recipients) {
    const [devices, preferences] = await Promise.all([
      db.query("deviceTokens", [{ field: "uid", value: uid }, { field: "enabled", value: true }]),
      db.get(`notificationPreferences/${uid}`),
    ]);
    if (!notificationEnabled(preferences, job.kind)) continue;
    const body = job.kind === "MESSAGE" && (job.hideSensitiveBody || preferences?.showMessagePreview !== true) ? "Open CBU Find to read it." : job.body;
    for (const device of devices) await sendFcm(env, db, device, { ...job, body });
  }
}

function notificationEnabled(preferences: PlainDocument | null, kind: NotificationJob["kind"]): boolean {
  if (!preferences) return true;
  const key = kind === "CLAIM_CREATED" ? "claims" : kind === "CLAIM_DECIDED" ? "claimDecisions" : kind === "MESSAGE" ? "messages" : kind === "REPORT_STATUS" ? "reportUpdates" : "moderation";
  return preferences[key] !== false;
}

async function sendFcm(env: Env, db: FirestoreRest, device: PlainDocument, job: NotificationJob): Promise<void> {
  const accessToken = await googleAccessToken(env);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { token: device.token, notification: { title: job.title, body: job.body }, data: job.data, android: { priority: "high", notification: { channel_id: "cbu_find_updates" } }, webpush: { fcm_options: { link: routeFor(env, job) } } } }),
  });
  if (response.ok) return;
  const error = await response.json<{ error?: { status?: string; details?: Array<{ errorCode?: string }> } }>().catch(() => null);
  const fcmErrorCode = error?.error?.details?.map((detail) => detail.errorCode).find(Boolean);
  if (fcmErrorCode === "UNREGISTERED") {
    await db.remove(`deviceTokens/${device.id}`);
    return;
  }
  throw new Error(`FCM_${response.status}_${error?.error?.status ?? "UNKNOWN"}`);
}

function routeFor(env: Env, job: NotificationJob): string {
  const path = job.data.conversationId ? `/messages/${encodeURIComponent(job.data.conversationId)}`
    : job.data.claimId ? "/claims"
      : job.data.itemId ? `/reports/${encodeURIComponent(job.data.itemId)}`
        : "/";
  try {
    const url = new URL(path, env.WEB_APP_URL);
    if (env.ENVIRONMENT !== "local" && url.protocol !== "https:") throw new Error("HTTPS required");
    return url.toString();
  } catch {
    throw new Error("WEB_APP_URL_INVALID");
  }
}

export async function processDeletion(env: Env, job: DeletionJob): Promise<void> {
  const db = new FirestoreRest(env), now = Date.now();
  let stage = "load-job";
  try {
  const completed = await db.get(`deletionResults/${job.referenceId}`);
  if (completed?.status === "COMPLETED") return;
  const anonymousId = `deleted_${await shortHash(job.uid)}`;
  await db.patch(`deletionJobs/${job.uid}`, { status: "PROCESSING", attempt: job.attempt + 1, updatedAt: now });

  stage = "load-related-data";
  const [privateProfile, items, tokens, ownedClaims, submittedClaims, conversations, ownedBlocks, incomingBlocks, moderationCases, actorLogs, targetLogs] = await Promise.all([
    db.get(`users/${job.uid}`),
    db.query("items", [{ field: "userId", value: job.uid }]),
    db.query("deviceTokens", [{ field: "uid", value: job.uid }]),
    db.query("claims", [{ field: "itemOwnerId", value: job.uid }]),
    db.query("claims", [{ field: "claimantId", value: job.uid }]),
    db.query("conversations", [{ field: "participantIds", op: "ARRAY_CONTAINS", value: job.uid }]),
    db.query("blocks", [{ field: "ownerId", value: job.uid }]),
    db.query("blocks", [{ field: "targetId", value: job.uid }]),
    db.query("moderationCases", [], { limit: 500 }),
    db.query("auditLogs", [{ field: "actorId", value: job.uid }]),
    db.query("auditLogs", [{ field: "targetId", value: job.uid }]),
  ]);
  const messages: PlainDocument[] = [];
  for (const conversation of conversations) {
    messages.push(...await db.query("messages", [{ field: "senderId", value: job.uid }], { parent: `conversations/${conversation.id}` }));
  }

  stage = "delete-media";
  const profileAsset = privateProfile?.photoAsset as MediaAsset | undefined;
  if (profileAsset?.publicId) await destroyCloudinaryAsset(env, profileAsset);

  for (const item of items) {
    const assets = ((item.media as MediaAsset[] | undefined) ?? []).filter((asset) => asset.publicId);
    for (const asset of assets) await destroyCloudinaryAsset(env, asset);
  }
  for (const message of messages) {
    if (typeof message.mediaPublicId === "string" && message.mediaPublicId) {
      await destroyCloudinaryAsset(env, { secureUrl: String(message.mediaUrl ?? ""), publicId: message.mediaPublicId, resourceType: (message.mediaResourceType as MediaAsset["resourceType"]) ?? "auto" });
    }
  }

  const writes: Array<Record<string, unknown>> = [];
  for (const token of tokens) writes.push(db.deleteWrite(`deviceTokens/${token.id}`));
  for (const block of uniqueDocuments([...ownedBlocks, ...incomingBlocks])) writes.push(db.deleteWrite(`blocks/${block.id}`));
  for (const item of items) {
    if (item.status === "RESOLVED") writes.push(db.setWrite(`items/${item.id}`, { userId: anonymousId, contactInfo: "", media: [], imageUrls: [], imageUri: null, updatedAt: now }, ["userId", "contactInfo", "media", "imageUrls", "imageUri", "updatedAt"]));
    else writes.push(db.deleteWrite(`items/${item.id}`));
    writes.push(db.deleteWrite(`reportContacts/${item.id}`));
  }
  for (const claim of uniqueDocuments([...ownedClaims, ...submittedClaims])) {
    const update: Record<string, unknown> = { note: "", updatedAt: now };
    if (claim.itemOwnerId === job.uid) update.itemOwnerId = anonymousId;
    if (claim.claimantId === job.uid) update.claimantId = anonymousId;
    writes.push(db.setWrite(`claims/${claim.id}`, update, Object.keys(update)));
  }
  for (const conversation of conversations) {
    const participantIds = ((conversation.participantIds as string[] | undefined) ?? []).map((value) => value === job.uid ? anonymousId : value);
    const names = { ...((conversation.participantNames as Record<string, string> | undefined) ?? {}) };
    const photos = { ...((conversation.participantPhotoUrls as Record<string, string> | undefined) ?? {}) };
    delete names[job.uid]; delete photos[job.uid]; names[anonymousId] = "Deleted member"; photos[anonymousId] = "";
    const deletedLastSender = conversation.lastSenderId === job.uid;
    writes.push(db.setWrite(`conversations/${conversation.id}`, {
      participantIds, participantNames: names, participantPhotoUrls: photos,
      lastSenderId: deletedLastSender ? anonymousId : conversation.lastSenderId ?? "",
      lastMessage: deletedLastSender ? "Message removed" : conversation.lastMessage ?? "",
      updatedAt: now,
    }, ["participantIds", "participantNames", "participantPhotoUrls", "lastSenderId", "lastMessage", "updatedAt"]));
  }
  for (const message of messages) {
    const path = documentPath(message);
    if (path) writes.push(db.setWrite(path, { senderId: anonymousId, text: "", deleted: true, mediaUrl: "", mediaPublicId: "", mediaResourceType: "", mediaType: "", mediaName: "", mediaSizeBytes: 0 }, ["senderId", "text", "deleted", "mediaUrl", "mediaPublicId", "mediaResourceType", "mediaType", "mediaName", "mediaSizeBytes"]));
  }
  for (const record of moderationCases) {
    const context = ((record.context as Array<Record<string, unknown>> | undefined) ?? []).map((entry) => entry.senderId === job.uid ? { ...entry, senderId: anonymousId, text: "", mediaUrl: "", deleted: true } : entry);
    const reporterDeleted = record.reporterId === job.uid, targetDeleted = record.targetId === job.uid, subjectDeleted = record.subjectUserId === job.uid;
    if (reporterDeleted || targetDeleted || subjectDeleted || context.some((entry, index) => entry !== ((record.context as Array<Record<string, unknown>> | undefined) ?? [])[index])) {
      const update = { reporterId: reporterDeleted ? anonymousId : record.reporterId, targetId: targetDeleted ? anonymousId : record.targetId, subjectUserId: subjectDeleted ? anonymousId : record.subjectUserId ?? "", details: reporterDeleted ? "" : record.details ?? "", context, updatedAt: now };
      writes.push(db.setWrite(`moderationCases/${record.id}`, update, Object.keys(update)));
    }
  }
  for (const record of uniqueDocuments([...actorLogs, ...targetLogs])) {
    const update: Record<string, unknown> = {};
    if (record.actorId === job.uid) update.actorId = anonymousId;
    if (record.targetId === job.uid) update.targetId = anonymousId;
    if (Object.keys(update).length) writes.push(db.setWrite(`auditLogs/${record.id}`, update, Object.keys(update)));
  }
  writes.push(
    db.deleteWrite(`notificationPreferences/${job.uid}`), db.deleteWrite(`roles/${job.uid}`), db.deleteWrite(`users/${job.uid}`), db.deleteWrite(`publicProfiles/${job.uid}`),
    db.setWrite(`publicProfiles/${anonymousId}`, { name: "Deleted member", programme: "", photoUrl: "", status: "DELETED", updatedAt: now }),
  );

  stage = "write-anonymized-records";
  for (let index = 0; index < writes.length; index += 400) await db.commit(writes.slice(index, index + 400));
  stage = "delete-auth-account";
  await deleteFirebaseAccount(env, job.uid);
  stage = "record-result";
  await db.commit([db.setWrite(`deletionResults/${job.referenceId}`, { status: "COMPLETED", completedAt: Date.now(), anonymousActorId: anonymousId }), db.deleteWrite(`deletionJobs/${job.uid}`)]);
  } catch (error) {
    console.error(JSON.stringify({
      event: "deletion.failed",
      referenceId: job.referenceId,
      stage,
      code: error instanceof AppError ? error.code : error instanceof Error ? error.name : "UNKNOWN",
    }));
    throw error;
  }
}

function uniqueDocuments(documents: PlainDocument[]): PlainDocument[] { return [...new Map(documents.map((document) => [document.id, document])).values()]; }

function documentPath(document: PlainDocument): string | null {
  const path = document._path;
  return typeof path === "string" ? path : null;
}

async function shortHash(value: string): Promise<string> {
  const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  return [...bytes.slice(0, 8)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function destroyCloudinaryAsset(env: Env, asset: MediaAsset): Promise<void> {
  if (!asset.publicId) return;
  const timestamp = Math.floor(Date.now() / 1000), resourceType = asset.resourceType === "video" || asset.resourceType === "raw" ? asset.resourceType : "image";
  const signatureBase = cloudinaryDestroySignatureBase(asset.publicId, timestamp, env.CLOUDINARY_API_SECRET);
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(signatureBase));
  const signature = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  const body = new URLSearchParams({ public_id: asset.publicId, timestamp: String(timestamp), api_key: env.CLOUDINARY_API_KEY, signature, signature_algorithm: "sha256", invalidate: "true" });
  const response = await fetch(`https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/${resourceType}/destroy`, { method: "POST", body });
  if (!response.ok) throw new Error(`CLOUDINARY_DELETE_${response.status}`);
}

export function cloudinaryDestroySignatureBase(publicId: string, timestamp: number, apiSecret: string): string {
  return `invalidate=true&public_id=${publicId}&timestamp=${timestamp}${apiSecret}`;
}

async function deleteFirebaseAccount(env: Env, uid: string): Promise<void> {
  const token = await googleAccessToken(env);
  const response = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/accounts:delete`, { method: "POST", headers: { authorization: `Bearer ${token}`, "content-type": "application/json", "x-goog-user-project": env.FIREBASE_PROJECT_ID }, body: JSON.stringify({ localId: uid }) });
  if (!response.ok && response.status !== 404) throw new Error(`AUTH_DELETE_${response.status}`);
}

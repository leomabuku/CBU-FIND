import { AppError } from "./errors";
import { googleAccessToken } from "./google";

/**
 * Student identity documents must never be publicly readable. They live in the
 * Firebase Storage bucket (never a Cloudinary folder) and are only ever fetched
 * by this Worker on behalf of an authorised moderator. No signed URLs are
 * handed to clients, so a leaked link cannot be replayed.
 */
export function verificationBucket(env: Env): string {
  return env.FIREBASE_STORAGE_BUCKET || `${env.FIREBASE_PROJECT_ID}.firebasestorage.app`;
}

export function verificationObjectPath(uid: string, requestId: string, contentType: string): string {
  const extension = contentType === "image/png" ? "png" : contentType === "image/webp" ? "webp" : "jpg";
  return `verification/${uid}/${requestId}.${extension}`;
}

function objectUrl(env: Env, path: string): string {
  return `https://storage.googleapis.com/storage/v1/b/${encodeURIComponent(verificationBucket(env))}/o/${encodeURIComponent(path)}`;
}

async function authorisedFetch(env: Env, url: string, init: RequestInit = {}): Promise<Response> {
  const token = await googleAccessToken(env);
  try {
    return await fetch(url, { ...init, headers: { authorization: `Bearer ${token}`, ...init.headers } });
  } catch {
    throw new AppError("DEPENDENCY_UNAVAILABLE", "CBU Find could not reach document storage. Please retry.", 503, true);
  }
}

export async function uploadPrivateObject(env: Env, path: string, bytes: Uint8Array<ArrayBuffer>, contentType: string): Promise<void> {
  const url = `https://storage.googleapis.com/upload/storage/v1/b/${encodeURIComponent(verificationBucket(env))}/o?uploadType=media&name=${encodeURIComponent(path)}`;
  const response = await authorisedFetch(env, url, {
    method: "POST",
    // Firestore rules do not apply to Storage, so the object is private by
    // default; no ACL is granted and no download token is created.
    headers: { "content-type": contentType, "cache-control": "no-store" },
    body: bytes as BodyInit,
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    console.error(JSON.stringify({ event: "storage.upload_failed", status: response.status, bucket: verificationBucket(env), detail: detail.slice(0, 200) }));
    throw new AppError("DEPENDENCY_UNAVAILABLE", "Your document could not be stored securely. Please retry.", 503, true);
  }
}

export async function downloadPrivateObject(env: Env, path: string): Promise<{ bytes: Uint8Array<ArrayBuffer>; contentType: string } | null> {
  const response = await authorisedFetch(env, `${objectUrl(env, path)}?alt=media`, { headers: { "cache-control": "no-store" } });
  if (response.status === 404) return null;
  if (!response.ok) throw new AppError("DEPENDENCY_UNAVAILABLE", "The document could not be opened. Please retry.", 503, true);
  return { bytes: new Uint8Array(await response.arrayBuffer()), contentType: response.headers.get("content-type") ?? "application/octet-stream" };
}

export async function deletePrivateObject(env: Env, path: string): Promise<void> {
  const response = await authorisedFetch(env, objectUrl(env, path), { method: "DELETE" });
  if (!response.ok && response.status !== 404) throw new Error(`STORAGE_DELETE_${response.status}`);
}

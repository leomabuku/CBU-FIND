"use client";

import { getToken as getAppCheckToken } from "firebase/app-check";
import { signOut } from "firebase/auth";
import type { ApiResponse, ErrorCode, MediaAsset } from "@cbu-find/contracts";
import { appCheck, auth, workerBaseUrl } from "./firebase";

export class ClientError extends Error {
  constructor(readonly code: ErrorCode, message: string, readonly retryable: boolean, readonly referenceId: string, readonly fieldErrors?: Record<string, string>) { super(message); }
}

export function messageRequestBody(text: string, attachment: (MediaAsset & { type: "IMAGE" | "VIDEO" | "FILE"; name: string }) | null): string {
  return JSON.stringify(attachment ? { text, attachment } : { text });
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  return apiAttempt<T>(path, init, false);
}

async function apiAttempt<T>(path: string, init: RequestInit, refreshTokens: boolean): Promise<T> {
  if (!workerBaseUrl) throw new ClientError("CONFIGURATION_REQUIRED", "The CBU Find API URL is not configured.", false, "WEB-CONFIG");
  const user = auth?.currentUser;
  if (!user) throw new ClientError("AUTH_REQUIRED", "Sign in to continue.", false, "WEB-AUTH");
  const check = appCheck();
  if (!check) throw new ClientError("CONFIGURATION_REQUIRED", "App Check is not configured for this web deployment.", false, "WEB-APPCHECK");
  const [idToken, checkToken] = await Promise.all([user.getIdToken(refreshTokens), getAppCheckToken(check, refreshTokens)]);
  const headers = new Headers(init.headers);
  headers.set("authorization", `Bearer ${idToken}`);
  headers.set("x-firebase-appcheck", checkToken.token);
  if (init.body != null && !(init.body instanceof FormData)) headers.set("content-type", "application/json");
  let response: Response;
  try {
    response = await fetch(`${workerBaseUrl}${path}`, { ...init, headers });
  } catch {
    throw new ClientError("DEPENDENCY_UNAVAILABLE", "The secure CBU Find service could not be reached. Check your connection, disable request-blocking for this site, and retry.", true, "WEB-NETWORK");
  }
  const referenceId = response.headers.get("x-reference-id") ?? `WEB-HTTP-${response.status}`;
  let result: ApiResponse<T>;
  try {
    result = JSON.parse(await response.text()) as ApiResponse<T>;
  } catch {
    throw new ClientError("DEPENDENCY_UNAVAILABLE", "The CBU Find service returned an invalid response. Please retry.", response.status >= 500, referenceId);
  }
  if (!result.ok) {
    if (!refreshTokens && (result.error.code === "AUTH_EXPIRED" || result.error.code === "APP_CHECK_REQUIRED")) return apiAttempt<T>(path, init, true);
    if (refreshTokens && result.error.code === "AUTH_EXPIRED" && auth) await signOut(auth).catch(() => undefined);
    throw new ClientError(result.error.code, result.error.message, result.error.retryable, result.error.referenceId || referenceId, result.error.fieldErrors);
  }
  return result.data;
}

export async function uploadSigned(file: File, folder: "reports" | "profiles" | "messages"): Promise<MediaAsset> {
  if (file.size > 20 * 1024 * 1024) throw new ClientError("VALIDATION_FAILED", "Attachments must be 20 MB or smaller.", false, "WEB-FILE-SIZE");
  const signing = await api<{ cloudName: string; apiKey: string; signature: string; signatureAlgorithm: string; timestamp: number; folder: string; context: string; resourceType: string; expiresAt: number }>("/v1/uploads/sign", { method: "POST", body: JSON.stringify({ folder, resourceType: "auto" }) });
  const body = new FormData();
  body.append("file", file); body.append("api_key", signing.apiKey); body.append("timestamp", String(signing.timestamp)); body.append("signature", signing.signature); body.append("signature_algorithm", signing.signatureAlgorithm); body.append("folder", signing.folder); body.append("context", signing.context);
  const response = await fetch(`https://api.cloudinary.com/v1_1/${signing.cloudName}/auto/upload`, { method: "POST", body });
  const result = await response.json() as { secure_url?: string; public_id?: string; resource_type?: "image" | "video" | "raw"; format?: string; bytes?: number; width?: number; height?: number; original_filename?: string; error?: { message?: string } };
  if (!response.ok || !result.secure_url) throw new ClientError("DEPENDENCY_UNAVAILABLE", "The media upload failed. Retry, or continue without an attachment.", true, response.headers.get("x-cld-error") ?? "CLOUDINARY-UPLOAD");
  return { secureUrl: result.secure_url, publicId: result.public_id, resourceType: result.resource_type, format: result.format, bytes: result.bytes, width: result.width, height: result.height, originalName: result.original_filename ?? file.name };
}

export function readableError(error: unknown): string {
  if (error instanceof ClientError) {
    const field = error.fieldErrors ? Object.entries(error.fieldErrors)[0] : undefined;
    const detail = field ? ` ${field[0] === "request" ? "Request" : field[0].replace(/([A-Z])/g, " $1").replace(/^./, (value) => value.toUpperCase())}: ${field[1]}` : "";
    return `${error.message}${detail} Reference: ${error.referenceId}`;
  }
  const code = typeof error === "object" && error && "code" in error ? String(error.code) : "";
  if (code.includes("auth/invalid-credential")) return "The email or password is incorrect. Reference: WEB-AUTH-CREDENTIAL";
  if (code.includes("auth/email-already-in-use")) return "An account already uses that email. Sign in or reset the password. Reference: WEB-AUTH-EMAIL-IN-USE";
  if (code.includes("auth/too-many-requests")) return "Too many attempts. Wait a few minutes, then retry. Reference: WEB-AUTH-RATE-LIMIT";
  if (code.includes("auth/unauthorized-domain")) return "Google sign-in is not authorized for this website. Ask the administrator to add this domain in Firebase Authentication. Reference: WEB-AUTH-DOMAIN";
  if (code.includes("auth/operation-not-allowed")) return "Google sign-in is currently disabled. Use email and password or contact an administrator. Reference: WEB-AUTH-PROVIDER";
  if (code.includes("auth/popup-blocked")) return "Your browser blocked the Google sign-in window. Allow pop-ups for this site and retry. Reference: WEB-AUTH-POPUP-BLOCKED";
  if (code.includes("auth/popup-closed-by-user")) return "Google sign-in was cancelled before it finished. Open it again to continue. Reference: WEB-AUTH-POPUP-CLOSED";
  if (code.includes("auth/network-request-failed")) return "Google sign-in could not reach Firebase. Check your connection and retry. Reference: WEB-AUTH-NETWORK";
  if (code.includes("permission-denied")) return "You do not have permission to view this information. Sign in again if this seems wrong. Reference: WEB-FIRESTORE-PERMISSION";
  if (code.includes("unavailable")) return "CBU Find is offline. Check your connection and retry. Reference: WEB-FIRESTORE-OFFLINE";
  if (code.includes("failed-precondition") && String(error).toLowerCase().includes("index")) return "This view is being prepared. Ask an administrator to deploy the required Firestore index. Reference: WEB-FIRESTORE-INDEX";
  return "Something went wrong. Retry the action. Reference: WEB-UNEXPECTED";
}

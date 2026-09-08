import { createRemoteJWKSet, jwtVerify } from "jose";
import { AppError } from "./errors";

const firebaseKeys = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const appCheckKeys = createRemoteJWKSet(new URL("https://firebaseappcheck.googleapis.com/v1/jwks"));

export interface AuthContext {
  uid: string;
  email: string;
}

function configured(value: string, placeholder: string): boolean {
  return Boolean(value) && value !== placeholder;
}

export function readAuthorizationToken(request: Request): string {
  const value = request.headers.get("Authorization")?.trim() ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(value);
  if (!match?.[1]) throw new AppError("AUTH_REQUIRED", "Sign in again to continue.", 401);
  return match[1].trim();
}

export function readAppCheckToken(request: Request): string {
  const value = request.headers.get("X-Firebase-AppCheck")?.trim() ?? "";
  if (!value) throw new AppError("APP_CHECK_REQUIRED", "This app installation could not be verified. Update the app and retry.", 401);
  // Firebase clients send the App Check token directly in this header. Accept
  // the old Bearer form temporarily so already-installed pilot clients keep
  // working during the cross-platform rollout.
  return value.replace(/^Bearer\s+/i, "").trim();
}

export async function verifyRequest(request: Request, env: Env): Promise<AuthContext> {
  if (!configured(env.FIREBASE_PROJECT_ID, "replace-me") || !configured(env.FIREBASE_PROJECT_NUMBER, "replace-me")) {
    throw new AppError("CONFIGURATION_REQUIRED", "The trusted backend is not configured yet.", 503);
  }

  const token = readAuthorizationToken(request);
  let verified;
  try {
    verified = await jwtVerify(token, firebaseKeys, {
      algorithms: ["RS256"],
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      audience: env.FIREBASE_PROJECT_ID,
    });
  } catch {
    throw new AppError("AUTH_EXPIRED", "Your session expired. Sign in again, then retry.", 401);
  }
  const uid = verified.payload.sub;
  if (!uid || typeof uid !== "string") throw new AppError("AUTH_EXPIRED", "Your session expired. Sign in again, then retry.", 401);

  const appCheck = readAppCheckToken(request);
  let checked;
  try {
    checked = await jwtVerify(appCheck, appCheckKeys, {
      algorithms: ["RS256"],
      issuer: `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
      audience: `projects/${env.FIREBASE_PROJECT_NUMBER}`,
    });
  } catch {
    throw new AppError("APP_CHECK_REQUIRED", "This app installation could not be verified. Update the app and retry.", 401);
  }
  const allowedApps = env.FIREBASE_APP_IDS.split(",").map((value) => value.trim()).filter(Boolean);
  if (!allowedApps.length) throw new AppError("CONFIGURATION_REQUIRED", "The trusted backend has no allowed app IDs configured.", 503);
  if (!checked.payload.sub || !allowedApps.includes(checked.payload.sub)) {
    throw new AppError("APP_CHECK_REQUIRED", "This app installation could not be verified. Update the app and retry.", 401);
  }
  return { uid, email: typeof verified.payload.email === "string" ? verified.payload.email : "" };
}

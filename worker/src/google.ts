import { importPKCS8, SignJWT } from "jose";
import { AppError } from "./errors";

type CachedToken = { value: string; expiresAt: number };
let cachedToken: CachedToken | undefined;
let pendingToken: Promise<CachedToken> | undefined;

const SCOPES = [
  "https://www.googleapis.com/auth/datastore",
  "https://www.googleapis.com/auth/firebase.messaging",
  "https://www.googleapis.com/auth/identitytoolkit",
].join(" ");

export async function googleAccessToken(env: Env): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;
  pendingToken ??= createToken(env).finally(() => { pendingToken = undefined; });
  cachedToken = await pendingToken;
  return cachedToken.value;
}

async function createToken(env: Env): Promise<CachedToken> {
  if (!env.GOOGLE_SERVICE_ACCOUNT_EMAIL || !env.GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY) {
    throw new AppError("CONFIGURATION_REQUIRED", "The trusted backend is not configured yet.", 503, false);
  }
  const now = Math.floor(Date.now() / 1000);
  const privateKey = await importPKCS8(env.GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY.replace(/\\n/g, "\n"), "RS256");
  const assertion = await new SignJWT({ scope: SCOPES })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(env.GOOGLE_SERVICE_ACCOUNT_EMAIL)
    .setSubject(env.GOOGLE_SERVICE_ACCOUNT_EMAIL)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(privateKey);
  const body = new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion });
  const response = await fetch("https://oauth2.googleapis.com/token", { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body });
  const result = await response.json<{ access_token?: string; expires_in?: number; error_description?: string }>();
  if (!response.ok || !result.access_token) throw new AppError("DEPENDENCY_UNAVAILABLE", "CBU Find could not reach its data service. Please retry.", 503, true);
  return { value: result.access_token, expiresAt: Date.now() + (result.expires_in ?? 3600) * 1000 };
}

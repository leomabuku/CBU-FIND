import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("../", import.meta.url);

test("ships the complete cross-platform portal instead of starter content", async () => {
  const [page, portal, api, firebase, worker, wrangler, css, packageJson] = await Promise.all([
    readFile(new URL("app/page.tsx", root), "utf8"), readFile(new URL("app/portal-app.tsx", root), "utf8"),
    readFile(new URL("app/api-client.ts", root), "utf8"), readFile(new URL("app/firebase.ts", root), "utf8"),
    readFile(new URL("worker/index.ts", root), "utf8"), readFile(new URL("wrangler.jsonc", root), "utf8"),
    readFile(new URL("app/globals.css", root), "utf8"), readFile(new URL("package.json", root), "utf8"),
  ]);
  assert.match(page, /<PortalApp/); assert.match(portal, /CreateReportScreen/); assert.match(portal, /ClaimsScreen/); assert.match(portal, /MessagesScreen/); assert.match(portal, /ModerationScreen/); assert.match(portal, /sendPasswordResetEmail/);
  assert.match(api, /X-Firebase-AppCheck|x-firebase-appcheck/i); assert.match(api, /uploads\/sign/); assert.match(css, /@media\(max-width:760px\)/); assert.match(packageJson, /"firebase"/);
  assert.match(firebase, /workerBaseUrl\s*=\s*"\/api\/cbu"/); assert.match(worker, /env\.CBU_API\.fetch/); assert.match(wrangler, /"binding":\s*"CBU_API"/);
  assert.match(portal, /Handover complete\?/); assert.match(portal, /Mark resolved/); assert.match(portal, /status:\s*"RESOLVED"/);
  assert.doesNotMatch(`${page}${portal}`, /Drizzle|D1Database|upload_preset|PhoneAuthProvider/);
});

test("documents every public cross-platform configuration value", async () => {
  const example = await readFile(new URL(".env.example", root), "utf8");
  for (const key of ["NEXT_PUBLIC_FIREBASE_API_KEY", "NEXT_PUBLIC_FIREBASE_PROJECT_ID", "NEXT_PUBLIC_FIREBASE_APP_ID", "NEXT_PUBLIC_FIREBASE_APP_CHECK_SITE_KEY", "NEXT_PUBLIC_FIREBASE_VAPID_KEY", "NEXT_PUBLIC_WORKER_API_URL"]) assert.match(example, new RegExp(`^${key}=`, "m"));
  assert.doesNotMatch(example, /CLOUDINARY_API_SECRET|UPLOAD_PRESET/);
});

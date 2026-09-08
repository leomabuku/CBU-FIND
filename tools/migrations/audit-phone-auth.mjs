import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { getAuth } from "firebase-admin/auth";
import { adminContext } from "./firebase-admin.mjs";

const { projectId } = adminContext();
const auth = getAuth();
let nextPageToken;
const phoneOnly = [];
let total = 0;
do {
  const page = await auth.listUsers(1000, nextPageToken);
  total += page.users.length;
  for (const user of page.users) {
    const providers = new Set(user.providerData.map((entry) => entry.providerId));
    const hasNonPhoneProvider = Boolean(user.email) || [...providers].some((provider) => provider !== "phone");
    if (user.phoneNumber && !hasNonPhoneProvider) phoneOnly.push({ uid: user.uid, createdAt: user.metadata.creationTime, lastSignInAt: user.metadata.lastSignInTime });
  }
  nextPageToken = page.pageToken;
} while (nextPageToken);

const repositoryRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
await mkdir(join(repositoryRoot, "backups", "firestore"), { recursive: true });
const output = join(repositoryRoot, "backups", "firestore", `${projectId}-phone-only-audit-${Date.now()}.json`);
await writeFile(output, JSON.stringify({ projectId, createdAt: new Date().toISOString(), phoneOnly }, null, 2), { encoding: "utf8", mode: 0o600 });
console.log(JSON.stringify({ event: "phone-auth.audit", totalUsers: total, phoneOnlyUsers: phoneOnly.length, output }));

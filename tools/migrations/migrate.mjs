import { adminContext, allDocuments, argument } from "./firebase-admin.mjs";
import { FieldValue } from "firebase-admin/firestore";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const apply = process.argv.includes("--apply");
const removeLegacyContact = process.argv.includes("--remove-legacy-contact");
const { db, projectId } = adminContext();
const operations = [];
const now = Date.now();
const repositoryRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");

if (apply) await verifyBackup(argument("--backup"), projectId);

for (const document of await allDocuments(db.collection("users"))) {
  const data = document.data();
  operations.push({ path: document.ref.path, merge: { status: data.status ?? "ACTIVE", updatedAt: data.updatedAt ?? data.createdAt ?? now } });
  operations.push({ path: `publicProfiles/${document.id}`, merge: { name: data.name || "Campus member", programme: data.programme || "", photoUrl: data.photoUrl || "", status: data.status === "SUSPENDED" ? "SUSPENDED" : "ACTIVE", updatedAt: data.updatedAt ?? now } });
  operations.push({ path: `roles/${document.id}`, createIfMissing: { role: "USER", updatedAt: now } });
}

for (const document of await allDocuments(db.collection("items"))) {
  const data = document.data();
  const hasLegacyContact = Object.prototype.hasOwnProperty.call(data, "contactInfo");
  const legacyUrls = Array.isArray(data.imageUrls) ? data.imageUrls : data.imageUri ? [data.imageUri] : [];
  const media = Array.isArray(data.media) ? data.media : legacyUrls.map((secureUrl) => ({ secureUrl, resourceType: "image" }));
  operations.push({ path: document.ref.path, merge: { status: ["ACTIVE", "MATCHED", "RESOLVED", "REMOVED"].includes(data.status) ? data.status : "ACTIVE", media, updatedAt: data.updatedAt ?? data.date ?? now }, deleteFields: hasLegacyContact && removeLegacyContact ? ["contactInfo"] : [] });
  if (hasLegacyContact && !(await db.doc(`reportContacts/${document.id}`).get()).exists) operations.push({ path: `reportContacts/${document.id}`, merge: { itemId: document.id, ownerId: data.userId, contactInfo: typeof data.contactInfo === "string" ? data.contactInfo : "", updatedAt: now } });
}

for (const document of await allDocuments(db.collection("conversations"))) {
  const data = document.data();
  operations.push({ path: document.ref.path, merge: { locked: data.locked ?? false, updatedAt: data.updatedAt ?? data.createdAt ?? now } });
}

console.log(JSON.stringify({ event: "migration.plan", projectId, mode: apply ? "apply" : "dry-run", removeLegacyContact, operations: operations.length }));
for (const operation of operations.slice(0, 25)) console.log(JSON.stringify({ path: operation.path, action: operation.createIfMissing ? "create-if-missing" : "merge", fields: Object.keys(operation.createIfMissing ?? operation.merge ?? {}), deleteFields: operation.deleteFields ?? [] }));
if (operations.length > 25) console.log(JSON.stringify({ omittedFromConsole: operations.length - 25 }));

if (apply) {
  for (let index = 0; index < operations.length; index += 400) {
    const batch = db.batch();
    for (const operation of operations.slice(index, index + 400)) {
      const reference = db.doc(operation.path);
      if (operation.createIfMissing) {
        const existing = await reference.get();
        if (!existing.exists) batch.create(reference, operation.createIfMissing);
      } else {
        const values = { ...operation.merge };
        for (const field of operation.deleteFields ?? []) values[field] = FieldValue.delete();
        batch.set(reference, values, { merge: true });
      }
    }
    await batch.commit();
  }
  console.log(JSON.stringify({ event: "migration.complete", operations: operations.length }));
} else {
  console.log("Dry run only. Create a backup, review this field-only output, then use npm run migration:apply -- --project <id> --backup <absolute-or-repository-relative-backup.json>. Legacy contact fields remain until --remove-legacy-contact is explicitly supplied after client rollout.");
}

async function verifyBackup(value, expectedProjectId) {
  if (!value) throw new Error("Migration apply is blocked until --backup <snapshot.json> is supplied.");
  const backupPath = isAbsolute(value) ? value : resolve(repositoryRoot, value);
  const [serialized, checksumText] = await Promise.all([readFile(backupPath, "utf8"), readFile(`${backupPath}.sha256`, "utf8")]);
  const snapshot = JSON.parse(serialized);
  const checksum = JSON.parse(checksumText);
  const actual = createHash("sha256").update(serialized).digest("hex");
  if (snapshot.projectId !== expectedProjectId) throw new Error(`Backup project ${snapshot.projectId ?? "unknown"} does not match ${expectedProjectId}.`);
  if (checksum.algorithm !== "sha256" || checksum.digest !== actual) throw new Error("Backup checksum verification failed. Create a new snapshot before applying the migration.");
  console.log(JSON.stringify({ event: "backup.verified", projectId: expectedProjectId, backup: backupPath, sha256: actual }));
}

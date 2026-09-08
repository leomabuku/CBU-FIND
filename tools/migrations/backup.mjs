import { mkdir, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { join } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { adminContext, allDocuments } from "./firebase-admin.mjs";

const { db, projectId } = adminContext();
const collections = await db.listCollections();
const snapshot = { projectId, createdAt: new Date().toISOString(), documents: {} };
async function capture(collection) {
  const documents = await allDocuments(collection);
  for (const document of documents) {
    snapshot.documents[document.ref.path] = document.data();
    for (const nested of await document.ref.listCollections()) await capture(nested);
  }
}
for (const collection of collections) await capture(collection);
const repositoryRoot = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
await mkdir(join(repositoryRoot, "backups", "firestore"), { recursive: true });
const output = join(repositoryRoot, "backups", "firestore", `${projectId}-${Date.now()}.json`);
const serialized = JSON.stringify(snapshot, null, 2);
const digest = createHash("sha256").update(serialized).digest("hex");
await writeFile(output, serialized, { encoding: "utf8", mode: 0o600 });
await writeFile(`${output}.sha256`, JSON.stringify({ algorithm: "sha256", digest, file: output.split(/[\\/]/).at(-1) }, null, 2), { encoding: "utf8", mode: 0o600 });
console.log(JSON.stringify({ event: "backup.complete", projectId, output, checksumFile: `${output}.sha256`, sha256: digest, topLevelCollections: collections.length, documents: Object.keys(snapshot.documents).length }));

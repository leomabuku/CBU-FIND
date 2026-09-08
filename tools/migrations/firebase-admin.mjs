import { applicationDefault, getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

export function adminContext() {
  const projectId = argument("--project") || process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) throw new Error("Pass --project <firebase-project-id> or set GOOGLE_CLOUD_PROJECT.");
  const app = getApps()[0] ?? initializeApp({ credential: applicationDefault(), projectId });
  return { projectId, db: getFirestore(app) };
}

export function argument(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

export async function allDocuments(query) {
  const documents = [];
  let cursor;
  do {
    let page = query.orderBy("__name__").limit(400);
    if (cursor) page = page.startAfter(cursor);
    const snapshot = await page.get();
    documents.push(...snapshot.docs);
    cursor = snapshot.docs.at(-1);
  } while (cursor && documents.length % 400 === 0);
  return documents;
}

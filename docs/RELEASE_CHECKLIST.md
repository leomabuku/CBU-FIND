# Release checklist

Nothing in this list has been executed against production. Items marked **[owner]** need a decision, credential or approval that this work could not supply.

## 1. Before any deployment

- [ ] Decide the membership cutover for existing accounts (`docs/VERIFICATION_WORKFLOW.md` §6). **[owner]**
- [ ] `npm install && npm run build && npm test` — expect 38 tests passing.
- [ ] `npm run lint`.
- [ ] `npm run test:rules` (needs a JVM for the Firestore emulator).
- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` with JDK 17 and SDK 36. **[owner]** — the Android changes in this release were never compiled in the authoring environment.
- [ ] `npm run worker:dry-run`.
- [ ] Review `firestore.rules` and confirm `verificationRequests` / `studentDirectory` are present and deny client writes.

## 2. Configuration

**Worker vars** (`worker/wrangler.jsonc`) per environment:

- [ ] `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`, `FIREBASE_APP_IDS`
- [ ] `FIREBASE_STORAGE_BUCKET`
- [ ] `CLOUDINARY_CLOUD_NAME`
- [ ] `WEB_APP_URL`, `CORS_ORIGINS` — no `example.invalid`
- [ ] `OCR_PROVIDER` (`none` ships safe: everything routes to manual review)
- [ ] `CBU_RECORD_API_URL` (empty until CBU authorises access)

**Worker secrets** (`wrangler secret put`), never committed:

- [ ] `GOOGLE_SERVICE_ACCOUNT_EMAIL`, `GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY` (needs `datastore`, `firebase.messaging`, `identitytoolkit`, `devstorage.read_write`, `cloud-vision`)
- [ ] `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- [ ] `CBU_RECORD_API_SECRET` (only when the record API exists)

**Web (`web/.env.*` or Cloudflare build env):**

- [ ] `NEXT_PUBLIC_FIREBASE_*`, `NEXT_PUBLIC_FIREBASE_APP_CHECK_SITE_KEY`, `NEXT_PUBLIC_FIREBASE_VAPID_KEY`

**Firebase console:**

- [ ] Authorized domains include `cbu-find-web-staging.leokmabuku.workers.dev` and `cbu-find-web-production.leokmabuku.workers.dev`
- [ ] App Check enforcement enabled for Firestore, Storage and the API
- [ ] Storage rules do not grant public read to `verification/*`
- [ ] Firestore indexes deployed (`firebase deploy --only firestore:indexes`) — 16 indexes including 5 for verification
- [ ] Firestore rules deployed (`firebase deploy --only firestore:rules`) — **never** deploy `CBU-FIND-WEB/firestore.rules`

## 3. Staging verification

- [ ] Two real accounts: report, claim, accept, chat, resolve; confirm Android ↔ web parity.
- [ ] Full verification journey (student submit → moderator approve) on both clients.
- [ ] Unverified account is blocked from reporting/claiming/messaging on both clients and by direct API call.
- [ ] Moderator can open the private document, and a non-moderator gets 403.
- [ ] Suspended account is blocked everywhere except deletion.
- [ ] Deletion removes ID documents and verification records; check the bucket directly.
- [ ] Push notifications arrive for claim, message and verification decisions.
- [ ] Public deletion page `/delete-account` completes without the app.

## 4. Google Play

- [ ] `applicationId com.campus.lostandfound`, `versionCode` bumped, `versionName` set
- [ ] Release keystore outside the repository; Play App Signing enrolled
- [ ] Play Integrity / App Check production credentials configured; debug tokens absent from release
- [ ] Data Safety form: declares account data, student ID images, private messages, and deletion
- [ ] Privacy policy URL live and linked in-app and in Play
- [ ] Account deletion URL: `/delete-account` on the production web host
- [ ] Content rating questionnaire completed
- [ ] Store listing: title, short/full description, screenshots, feature graphic, icon
- [ ] App access instructions for reviewers (sample verified account + how verification works)
- [ ] Internal testing track → closed testing → production, with approval at each gate **[owner]**

## 5. Post-deploy monitoring

- [ ] Worker logs: `storage.upload_failed`, `ocr.failed`, `ocr.provider_error`, `verification.document_delete_failed`, `deletion.failed`
- [ ] Queue depth for `cbu-find-notifications` and `cbu-find-deletions`, including DLQs
- [ ] Firestore quota and Cloudinary usage
- [ ] First-week review of `auditLogs` for verification decisions

## 6. Rollback

- Worker: redeploy the previous version; membership fields are additive, so old code keeps working.
- Rules: `firebase deploy` the previous `firestore.rules`. Do not roll back to the legacy repository's ruleset.
- Uploaded documents: retained in the bucket; a rollback does not orphan them.

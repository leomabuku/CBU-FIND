# CBU Find — production readiness audit

**Date:** 2026-11-02
**Repositories:** `leomabuku/CBU-FIND` (Android, Worker, web client, rules, tooling) and `leomabuku/CBU-FIND-WEB` (superseded standalone web client)
**Baseline commit:** `edd3399` · **Work branch:** `arena/9b4e25bf-cbu-find`

Findings are classified **P0** (security, privacy, verification or release blocker), **P1** (important functionality), **P2** (non-blocking), **P3** (future expansion). Every finding cites the code it was verified against.

---

## 1. What already worked (preserved, not rebuilt)

| Area | Evidence |
| --- | --- |
| Trusted-Worker architecture — all mutations server-side, Firestore rules deny client writes | `firestore.rules` (17 `if false` write rules), `worker/src/index.ts` |
| Firebase Auth with App Check verification on every `/v1/*` call | `worker/src/auth.ts` |
| Deterministic claims; owner must accept before chat opens | `worker/src/domain.ts`, `worker/src/schemas.ts` |
| Private contact separation (`reportContacts` never public) | `worker/src/domain.ts` (`contactInfo: undefined` on item creation) |
| Signed Cloudinary uploads with ownership prefixes | `POST /v1/uploads/sign`, `assertOwnedMedia()` |
| Rate limits, queues with dead-letter handling, FCM | `worker/wrangler.jsonc`, `worker/src/queues.ts` |
| Queued account deletion with anonymisation | `processDeletion()` in `worker/src/queues.ts` |
| Moderation with ±2 message context window | `moderationMessageContext()`, `firestore.rules` |
| Feature parity between Android and web | `docs/PLATFORM_PARITY.md` |

Tests at audit time: 13 Worker, 4 web (rendered + unit), 0 Android instrumentation runnable without a device.

---

## 2. P0 findings

### P0-1 — No student membership verification existed (FIXED)

There was no verification model, no student-ID handling, and no membership gate. Any Google or email/password account could report, claim and message.

**Implemented:** membership status (`UNVERIFIED`/`PENDING`/`VERIFIED`/`REJECTED`) on `users/{uid}`; verification requests with the full state machine; server-side gate in `worker/src/domain.ts` (`requireVerifiedMember`) applied to `POST /v1/reports`, `/v1/claims`, `/v1/claims/:id/action`, `/v1/conversations/:id/messages`, and report uploads. Moderators and admins are exempt so the service stays operable. Clients hide the same actions, but the API is the gate.

### P0-2 — Student ID images had no safe home (FIXED)

The only media pipeline was Cloudinary with public delivery URLs (`assertOwnedMedia`). Storing an ID there would make it world-readable.

**Implemented:** `worker/src/storage.ts` writes identity documents to the Firebase Storage bucket at `verification/{uid}/{requestId}.{ext}` with no ACL and no download token. Moderators never receive a URL: `GET /v1/admin/verifications/:id/document` proxies the bytes after role checks and logs an audit entry. Student numbers are kept out of public collections by `studentDirectory` (deny-all rules).

### P0-3 — No protection against duplicate or replayed documents (FIXED, partial)

**Implemented:** unique `studentDirectory/{normalisedStudentNumber}` claim written in a transaction-free but conflict-checked sequence, plus a SHA-256 of each uploaded document stored on the request (`documentHash`) for future reuse detection. Duplicate numbers are rejected with an explicit reason and routed to resubmission.

**Outstanding:** cross-account document-hash comparison is stored but not yet blocked on. Deliberately deferred so legitimate re-enrolment is not silently blocked.

### P0-4 — OCR and university record access do not exist (CONFIGURED, NOT PROVISIONED)

No OCR provider and no CBU record API are available.

**Implemented:** pluggable adapters. `OCR_PROVIDER=vision` uses Google Cloud Vision `DOCUMENT_TEXT_DETECTION`; the default `none` returns no text rather than inventing fields, so requests fall to manual review. `CBU_RECORD_API_URL` empty → matcher reports `UNAVAILABLE`; a configured URL uses bearer `CBU_RECORD_API_SECRET` and treats any failure as `UNAVAILABLE`, never `NO_MATCH`. Auto-approval requires a configured provider, `MATCHED`, ≥0.9 confidence and a ≥0.8 student-number read — OCR alone can never approve.

**External dependency:** CBU authorisation for record access; a Vision service account with the API enabled. Both documented in `docs/VERIFICATION_WORKFLOW.md`.

### P0-5 — Production web configuration contained placeholders (FIXED)

`worker/wrangler.jsonc` production carried `WEB_APP_URL`/`CORS_ORIGINS` = `https://example.invalid`, which breaks password-reset links, notification deep links and every browser origin. Now `https://cbu-find-web-production.leokmabuku.workers.dev`; types regenerated. See `docs/WEB_APP_CONNECTION.md`.

### P0-6 — The standalone web repository ships a conflicting security model (DOCUMENTED)

`CBU-FIND-WEB` performs 8 direct Firestore writes and ships a 246-line ruleset that would reopen client writes. Deploying either breaks the trusted-Worker model. Decision and evidence: `docs/WEB_CONNECTION_AUDIT.md`. **Do not deploy `CBU-FIND-WEB/firestore.rules` or `storage.rules`.**

### P0-7 — Existing accounts become unverified at cutover (DECISION REQUIRED)

Every account created before this change has no `membershipStatus` and is therefore read as `UNVERIFIED`, losing the ability to report, claim or message.

**Required before release:** either (a) grandfather existing accounts to `VERIFIED` with a one-time admin script, or (b) require everyone to verify. This is a product decision with privacy implications and was **not** taken silently. Procedure in `docs/VERIFICATION_WORKFLOW.md` §6.

---

## 3. P1 findings

| # | Finding | Status |
| --- | --- | --- |
| P1-1 | Android sources changed but **not compiled** — this environment has no JDK or Android SDK | Requires `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` before release |
| P1-2 | Firestore rules tests not executed — the emulator needs a JVM | `npm run test:rules` must run in CI or on a workstation |
| P1-3 | Public browsing without sign-in is a stated product decision, but `items` read still requires `signedIn()` | Not implemented; needs field-level review before exposing reads anonymously |
| P1-4 | Android claims/chat do not show a dedicated "verify first" screen; they surface the API's `MEMBERSHIP_REQUIRED` message | Acceptable short-term; web has explicit gates |
| P1-5 | `web/worker/index.ts:42` has a pre-existing type mismatch between the vinext handler and `Cloudflare.Env` | Build passes; type-only |
| P1-6 | No CI pipeline runs lint/build/test on push | Recommend GitHub Actions |
| P1-7 | Document retention is defined in policy but not automated | Manual purge documented; automation is P2 |

---

## 4. P2 / P3

- **P2** Automated retention sweep for rejected verification documents.
- **P2** Accessibility audit of both clients (contrast, screen-reader labels, focus order).
- **P2** Blocked-account list shows raw UIDs on both clients.
- **P3** Institution model: `institutionId` is persisted on users and verification requests, but collections are not yet scoped per institution. Multi-university support needs tenant scoping in rules and queries before a second institution is onboarded.

---

## 5. Test coverage added

| Suite | Location | Count |
| --- | --- | --- |
| Verification parsing, document validation, auto-approval policy, membership projection | `worker/tests/verification.test.ts` | 11 |
| Worker health + error envelope | `worker/tests/worker.test.ts` | 13 |
| Cross-platform constant drift | `web/tests/platform-parity.test.ts` | 4 |
| Membership gating | `web/tests/membership-gating.test.ts` | 3 |
| Image compression maths | `web/tests/media-compression.test.ts` | 3 |
| UI state notices | `web/tests/state-notice.test.tsx` | 4 |

Total **38** automated tests, all passing as of this audit. Tests caught two real parser defects during development ("STUDENT IDENTITY CARD" parsed as a student number, and un-normalised names), both fixed.

---

## 6. Recommended sequence from here

1. **Decide P0-7** (grandfather or verify existing accounts).
2. Run the Android build and the rules tests on a machine with JDK 17 + Android SDK 36; fix anything they surface.
3. Provision OCR (Vision) and, if authorised, the CBU record endpoint; then set `OCR_PROVIDER`/`CBU_RECORD_API_URL` per environment.
4. Deploy the Worker and web app to staging; run the two-account journey in `docs/DEPLOYMENT.md` §staging.
5. Complete `docs/RELEASE_CHECKLIST.md`, then request approval for production and Play Store submission.

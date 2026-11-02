# Student verification workflow

CBU Find uses **hybrid** membership verification: automated matching against an authorised university record source when one is available, with manual moderator review whenever that is unavailable, inconclusive or low-confidence. OCR output is never accepted as proof on its own.

## 1. States

Stored on `verificationRequests/{id}.status`:

| State | Meaning | Who moves it |
| --- | --- | --- |
| `DRAFT` | Document stored, fields extracted, waiting for the student to confirm them | Student scan step |
| `EXTRACTION_FAILED` | No readable fields; the student types them manually | Student scan step |
| `PENDING_RECORD_MATCH` | Reserved for a configured record source | Confirm step |
| `PENDING_REVIEW` | Waiting for a moderator | Confirm step |
| `APPROVED` | Membership granted (automatic match or moderator) | Confirm step / moderator |
| `REJECTED` | Membership refused, reason required | Moderator |
| `NEEDS_RESUBMISSION` | Student must upload a clearer document | Moderator, or duplicate student number |

Membership on the user document is the projection clients read: `UNVERIFIED` → `PENDING` (submitted) → `VERIFIED` (approved) or `REJECTED`. Only the Worker writes these fields; `firestore.rules` denies all client writes to `users`, `verificationRequests` and `studentDirectory`.

A record-system outage never rejects a student: matcher failures return `UNAVAILABLE` and route to `PENDING_REVIEW`.

## 2. Student journey

1. Open **Get verified** (web sidebar or the Android home banner).
2. Photograph the CBU student ID. Accepted: JPEG, PNG, WebP, ≤ 5 MB, ≥ 1 KB. The content type is verified from the file's magic bytes, not the browser's claim.
3. The Worker stores the image privately, runs OCR (if configured) and returns any fields it read **with per-field confidence**.
4. The student corrects the fields and submits.
5. The Worker checks the student number against `studentDirectory`. A number already linked to another account is rejected with an explanation and the request moves to `NEEDS_RESUBMISSION`.
6. If a record source is configured and returns a strong match (≥0.9) with a confidently read student number (≥0.8), membership is approved automatically. Everything else goes to `PENDING_REVIEW`.
7. The student is notified with FCM when the decision is made.

## 3. Moderator journey

**Web:** Moderation → *Student ID verification queue*. **Android:** Moderation → *Student ID verification*.

For each request a moderator sees: full name, student number, programme, academic year, OCR provider and confidence, record-match outcome, account UID and submission time.

1. Open the ID document. The image is fetched through `GET /v1/admin/verifications/:id/document`, which checks the moderator role, streams the bytes and writes an `auditLogs` entry (`VERIFICATION_DOCUMENT_VIEWED`). No URL is ever issued, so a copied link cannot be replayed.
2. Compare the document with the confirmed fields. A different student number on the card, an expired card, or a name mismatch are grounds for rejection.
3. Decide:
   - **Approve** → membership `VERIFIED`, student number claimed in `studentDirectory`, audit entry `VERIFICATION_APPROVE`.
   - **Reject** → reason required, membership `REJECTED`, student notified.
   - **Request new document** → membership returns to `PENDING`, reason shown to the student.

Approving when the number is already claimed fails safely with an explicit error; the moderator is told to reject and ask the student to contact support.

Only `MODERATOR` and `ADMIN` roles can list requests, view documents or decide. There is no client-side path to any of these operations.

## 4. Privacy and retention

- ID images live in the Firebase Storage bucket, never in Cloudinary, never in a public Firestore field.
- Clients receive field values and confidences only — never `documentPath`, `documentHash` or bytes.
- Account deletion purges verification requests, private objects and `studentDirectory` entries *before* deleting the Firebase Auth account (`purgeVerificationData` in `worker/src/verification/index.ts`, invoked by `processDeletion`).
- Retention policy: keep the document while the request is undecided; after a decision keep it for 30 days to support appeals, then delete. **Automation of the 30-day sweep is not implemented yet** (P2 in the audit); until then it is a manual operator task.
- Do not add facial recognition or document liveness checks in this version.

## 5. Configuration

| Binding | Where | Purpose |
| --- | --- | --- |
| `FIREBASE_STORAGE_BUCKET` | `worker/wrangler.jsonc` vars | Private document bucket |
| `OCR_PROVIDER` | vars (`none` or `vision`) | `vision` requires the Cloud Vision API enabled for the service account |
| `CBU_RECORD_API_URL` | vars, empty when unauthorised | Authorised record-matching endpoint |
| `CBU_RECORD_API_SECRET` | `wrangler secret put` | Bearer token for that endpoint |
| Google scopes | `worker/src/google.ts` | `devstorage.read_write`, `cloud-vision` |

Record API contract expected by the adapter (`worker/src/verification/matcher.ts`):

```http
POST {CBU_RECORD_API_URL}
Authorization: Bearer {CBU_RECORD_API_SECRET}
{ "studentNumber": "2021123456", "fullName": "Chanda Mulenga", "programme": "BSc Computer Science" }
→ 200 { "outcome": "MATCHED" | "NO_MATCH" | "AMBIGUOUS", "confidence": 0.97 }
```

Any non-200, timeout or malformed body is treated as `UNAVAILABLE`.

## 6. Cutover for accounts that predate verification

Accounts created before this release have no `membershipStatus`, so they read as `UNVERIFIED` and cannot report, claim or message until verified. Choose one path before release:

**A. Grandfather existing members (faster, less strict).** For each pre-cutover account, set:

```
users/{uid}: membershipStatus = "VERIFIED", membershipUpdatedAt = <now>, institutionId = "cbu"
```

Run this from a trusted admin environment (Firebase Admin SDK), never from a client. Record the UID list in the release notes. This asserts that existing accounts are CBU students without documentary evidence — a deliberate, documented exception.

**B. Require everyone to verify (strict).** Ship as-is and announce the change. Existing users keep browsing and can verify in under a minute.

Either way, verify the result on staging with one grandfathered and one new account before production.

## 7. Manual tests to run before release

- Valid CBU card → fields extracted → submit → moderator approves → student can report.
- Blurry card → `EXTRACTION_FAILED` → manual entry still works.
- PDF renamed to `.jpg` → rejected on magic bytes.
- 6 MB image → rejected with the size message.
- Duplicate student number on a second account → rejected, request marked for resubmission.
- Moderator rejects with a reason → student sees the reason and can resubmit.
- Unverified student calls `POST /v1/reports` directly → `MEMBERSHIP_REQUIRED` 403.
- Suspended user calls any protected endpoint → `ACCOUNT_SUSPENDED` 403.
- Account deletion removes the ID document, verification records and directory entry.

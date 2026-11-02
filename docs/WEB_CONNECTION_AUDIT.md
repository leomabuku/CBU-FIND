# Repository connection audit — CBU-FIND ↔ CBU-FIND-WEB

**Date:** 2026-11-02
**Scope:** Establishing how the Android/backend repository (`leomabuku/CBU-FIND`) and the standalone web repository (`leomabuku/CBU-FIND-WEB`) relate, and whether they can operate as one system.
**Method:** Source inspection of both repositories at their current `main` heads. No deployments were performed.

---

## 1. Connection status

| Repository | Access | Head | Working state |
| --- | --- | --- | --- |
| `leomabuku/CBU-FIND` | Checked out at `/home/user/CBU-FIND` | `edd3399` (branch `arena/9b4e25bf-cbu-find`) | Dependencies installed; `npm test` → **17/17 passing** |
| `leomabuku/CBU-FIND-WEB` | Cloned at `/home/user/CBU-FIND-WEB` | `47b5844` (`main`, merge of PR #2) | Source read only; not installed or built in this audit |

Both repositories are reachable with the authenticated GitHub identity `leomabuku`. Neither is archived; `CBU-FIND-WEB` has no open pull requests or issues.

## 2. What already connects

The two repositories share one runtime identity, because both `.firebaserc` files declare the same Firebase project:

```json
{ "projects": { "default": "cbu-lost-and-found" } }
```

They therefore share Firebase Authentication users, the `users` and `items` collections, `conversations`/`messages`, and the Cloudinary cloud `campuslostandfound`. `CBU-FIND-WEB` documents this contract in `docs/ANDROID_WEB_CONNECTION.md`.

**Data-level connection: present.**

## 3. What does not connect — the blocking conflict

`CBU-FIND` and `CBU-FIND-WEB` each ship **their own, mutually exclusive** `firestore.rules` for the same project. Only one can be deployed at a time, and each one breaks the other client.

### 3.1 The monorepo rules forbid all client-side writes

`CBU-FIND/firestore.rules` denies every direct client mutation (17 `if false` write rules, e.g. lines 24, 33, 41, 45, 53, 58, 62, 67, 73). All business changes must pass through the trusted Worker at `/v1/*` (`worker/src/domain.ts`).

### 3.2 The legacy web client is a direct-write client

`CBU-FIND-WEB` performs 8 direct Firestore mutations from the browser:

| File | Line | Operation |
| --- | --- | --- |
| `components/ReportForm.tsx` | 43 | `addDoc(collection(db, "items"))` — create report |
| `components/ItemDetails.tsx` | 23 | `updateDoc(items…)` — mark returned |
| `components/AuthScreen.tsx` | 28 | `setDoc(users…)` — profile bootstrap |
| `components/ProfilePanel.tsx` | 46 | `setDoc(users…)` — profile edit |
| `app/CampusFindApp.tsx` | 73 | `setDoc(…)` — profile setup |
| `lib/chat.ts` | 22 | `setDoc(conversations…)` — open conversation |
| `components/MessagingPanel.tsx` | 94 | `updateDoc(lastReadAt…)` — read receipt |
| `components/MessagingPanel.tsx` | 5 | `writeBatch` import — message history |

**Consequence:** against the current, deployed-shape monorepo rules, every write above fails with `permission-denied`. The legacy web app can read but cannot report an item, edit a profile, start a conversation, or mark an item returned.

### 3.3 The legacy ruleset reopens what the monorepo closed

`CBU-FIND-WEB/firestore.rules` (246 lines) permits exactly those writes:

- line 135 — `users` create/update by owner
- line 144 — `items` create by any signed-in user
- line 170 — `conversations` create by any signed-in user
- line 234 — `messages` create by any participant

Deploying it would disable the monorepo's trusted-Worker model (report creation, claims, claims acceptance, moderation, suspension) and re-enable direct client writes that bypass server validation, rate limits and App Check.

### 3.4 Unsigned media upload

`CBU-FIND-WEB/lib/media.ts:33-35` uploads to Cloudinary using a browser-exposed **unsigned upload preset** (`NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET`). `CBU-FIND/README.md` states unsigned Cloudinary uploads are intentionally unsupported; the monorepo signs uploads server-side via `POST /v1/uploads/sign`. The legacy path can be replayed by anyone holding the preset, independent of Firebase auth.

### 3.5 Divergent business logic

`lib/chat.ts:22` creates a conversation directly whenever a browser wants one. The monorepo requires an **accepted claim** first: `firestore.rules:16` defines `acceptedClaim()`, `firestore.rules:43` denies conversation creation, and the Worker gates `POST /v1/conversations/:id/messages`. The legacy web client has no claim concept at all — there is no `claims` route in `CBU-FIND-WEB/app`, while the monorepo exposes `web/app/claims`.

## 4. Client coverage comparison

| Capability | `CBU-FIND/web` (current) | `CBU-FIND-WEB` (legacy) |
| --- | --- | --- |
| Public feed, search, filters | Yes | Yes |
| Report creation | Worker-signed | Direct Firestore + unsigned upload |
| Claims workflow | Yes (`app/claims`) | No |
| Private messaging | Claim-gated, Worker | Direct Firestore |
| Moderation tools | Yes (`app/moderation`) | No |
| Profile & notification settings | Yes (`app/settings`) | Partial |
| Account deletion | Yes (queued Worker job) | No |
| Tests | Vitest + rendered HTML + Playwright | Rendered HTML smoke test only |

`CBU-FIND/web` is a functional superset of `CBU-FIND-WEB`. The standalone repository is the earlier iteration of the same client, left behind when the trusted-Worker architecture was introduced (`docs/PROJECT_CHANGELOG.md`, 2026-08-10 entry).

## 5. Additional production blocker observed

`worker/wrangler.jsonc` production profile still carries placeholders:

```
"WEB_APP_URL":   "https://example.invalid",
"CORS_ORIGINS":  "https://example.invalid"
```

Any production deploy in this state will emit broken links and reject every browser origin. The concrete production web origin must be supplied by the owner — it is not inferable from source and must not be invented.

## 6. Options

**A. Consolidate (recommended).** `CBU-FIND` remains the system of record. `CBU-FIND-WEB` is retired: archive it on GitHub and leave a README/redirect pointing at the monorepo web client. Removes the conflicting ruleset, the unsigned upload preset and the duplicate client. No code migration cost.

**B. Re-home the legacy client.** Rewrite `CBU-FIND-WEB`'s data layer onto the monorepo `/v1/*` Worker API (signed uploads, claims, moderation), delete its `firestore.rules`/`storage.rules`, and add its origin to `CORS_ORIGINS`. Produces two supported frontends against one backend — more code to maintain, and it duplicates `CBU-FIND/web`.

**C. Keep both, side by side, unchanged.** Valid only for local comparison. Cannot be deployed simultaneously against the shared project.

**D. Workspace-only connection.** Both repositories are available in this session; no architectural change. Proceed with the production-readiness work in `CBU-FIND`.

## 7. Decision (2026-11-02)

**The web app stays live.** `CBU-FIND/web` is designated the production web client, serving desktop and iOS users alongside the native Android app, connected to the backend through the API Worker.

Consequences:

- `worker/wrangler.jsonc` production `WEB_APP_URL` and `CORS_ORIGINS` were set to `https://cbu-find-web-production.leokmabuku.workers.dev` (resolves §5).
- `CBU-FIND-WEB` is treated as superseded, not as a second supported frontend. Its live hostname should continue serving users only as a redirect to production; its `firestore.rules` and `storage.rules` must never be deployed to `cbu-lost-and-found`.
- Implementation and deployment details are in `docs/WEB_APP_CONNECTION.md`.

Option B remains available if a second independent frontend later becomes a real requirement; it would require porting the legacy client onto `/v1/*`, deleting its rules files, and adding its origin to `CORS_ORIGINS`.

# CBU Find

Cross-platform lost-and-found service for Copperbelt University. The native Android app and responsive Vinext web client share Firebase Authentication and real-time Firestore reads. Sensitive and multi-document changes go through a trusted Cloudflare Worker so the deployment stays compatible with Firebase Spark.

## Current features

- Email/password and Google sign-in, password recovery, and race-safe profile setup
- Lost/found report feed, search, filters, signed media, private contact records, and status updates
- Deterministic claims; the report owner must accept before a private conversation opens
- Real-time inbox/chat with unread state and signed image/video/file attachments
- Push notifications, notification privacy preferences, two-way blocks, and abuse reports
- Moderator case management on Android and web with only the reported message ±2 adjacent messages
- Report removal/restoration, chat locking, user suspension/reactivation, admin role management, and last-admin protection
- Queued account deletion that removes private data/active content and anonymizes shared history

Phone-number/SMS authentication and unsigned Cloudinary uploads are not supported. Optional phone numbers remain private contact/profile data.

## Architecture

- `app/` — Android, Kotlin, Jetpack Compose, API 36, JDK 17
- `web/` — responsive Vinext/React client hosted on Cloudflare Workers
- `worker/` — Hono/Zod/JOSE trusted API, rate limits, queues, FCM, signed Cloudinary operations
- `packages/contracts/` — shared TypeScript response and data contracts
- `firestore.rules` / `firestore.indexes.json` — real-time read authorization and indexes
- `tools/migrations/` — ignored snapshots, phone-only-account audit, dry-run additive migration
- `tools/firestore-tests/` — Firebase Emulator security tests

## Local prerequisites

- JDK 17 and Android SDK 36 for Android; JDK 21+ for the current Firestore Emulator CLI
- Node.js 22.13 or newer
- Firebase project on Spark, a Cloudflare account, and the existing Cloudinary account

Install JavaScript workspaces with `npm install`. Copy `web/.env.example` to `web/.env.local`. Add the non-secret Worker URL to `local.properties`:

```properties
WORKER_API_BASE_URL=https://cbu-find-api-staging.<account>.workers.dev
```

Configure trusted values with interactive `wrangler secret put`; never commit or print them. Update non-secret project/app/origin placeholders in `worker/wrangler.jsonc`, then run `npm run worker:types` and `npm run worker:dry-run`.

## Verification

```powershell
npm test
npm run test:rules
npm run worker:dry-run
npm run build
$env:JAVA_HOME='C:\path\to\jdk-17'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

See `docs/DEPLOYMENT.md` for migration, staging, deployment, quota monitoring, and provider-disable order.

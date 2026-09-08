# Deployment and migration runbook

## Preflight and backup

Use Application Default Credentials for a least-privilege operator. Never commit the snapshot.

```powershell
npm run backup:firestore -- --project <firebase-project-id>
npm run audit:phone-auth -- --project <firebase-project-id>
npm run migration:dry-run -- --project <firebase-project-id>
```

Resolve phone-only accounts through admin-assisted email/Google linking. Review the dry-run and snapshot before proceeding.

## Worker staging

Update staging values in `worker/wrangler.jsonc`, create notification/deletion queues, add secrets interactively, generate types, and dry-run before deploying explicitly with `--env staging`. Never deploy the root Worker accidentally. Verify health, Firebase ID/App Check verification, signed upload, FCM, structured logs, and rate limits.

Grant the Worker service account only Firestore read/write, FCM send, and Firebase Authentication account-deletion permissions. Store its email/private key and the Cloudinary key/secret only as Worker secrets:

```powershell
Set-Location worker
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_EMAIL --env staging
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY --env staging
npx wrangler secret put CLOUDINARY_API_KEY --env staging
npx wrangler secret put CLOUDINARY_API_SECRET --env staging
Set-Location ..
npm run worker:types
npm run worker:dry-run
```

Replace every `replace-me`/`example.invalid` value before deployment. The Firebase project number, allowed Android/web App IDs, Cloudinary cloud name, public web app URL, and CORS origins are non-secret bindings.

## Rules and additive migration

Run Emulator tests, deploy indexes and wait until ready, apply the reviewed migration, then deploy rules. The migration preserves every existing user/report/conversation, creates sanitized public profiles/private contacts, and retains legacy image URL fields.

```powershell
# Firebase CLI 15 requires JDK 21+ for emulator/rule commands.
npm run test:rules
firebase deploy --only firestore:indexes --project <firebase-project-id>
npm run migration:apply -- --project <firebase-project-id> --backup <backups/firestore/snapshot.json>
firebase deploy --only firestore:rules --project <firebase-project-id>
```

## Client acceptance

Deploy web staging and an Android internal build. With two real accounts, test claim acceptance, cross-client messaging/unread, attachments, push, contact access, blocks, abuse reports, moderator context, suspension, and deletion retry. Deploy production web and Android before tightening final rules. Disable Phone Authentication only after every audited account is resolved.

## Free quotas

Monitor Cloudflare Worker requests/CPU/queue operations and Firebase Firestore daily reads/writes. Configure alerts before 100,000 Worker requests/day, 50,000 Firestore reads/day, or 20,000 Firestore writes/day. Quota exhaustion must return visible retryable `CAPACITY_EXCEEDED`, never an empty state.

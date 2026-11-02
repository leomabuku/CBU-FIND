# Web app connection and deployment

The web client in `web/` is the desktop and iOS-facing CBU Find app. Android has its own native app; both are clients of the same backend. This document records how the web app stays connected, what it needs to run, and how the superseded standalone web repository relates to it.

## 1. How the web app reaches the backend

```
browser ──▶ web Worker (cbu-find-web-production)
              │  /api/cbu/*  ──service binding──▶  API Worker (cbu-find-api-production)
              └─ everything else ─▶ Vinext/React app
```

- `web/app/firebase.ts` sets `workerBaseUrl = "/api/cbu"`, so the browser only ever talks to its own origin.
- `web/worker/index.ts` rewrites `/api/cbu/*` onto an internal hostname and forwards the request through the `CBU_API` service binding.
- `web/wrangler.jsonc` binds `CBU_API` to `cbu-find-api-staging` for staging and `cbu-find-api-production` for production.

Because the API call is same-origin, it is not subject to the API Worker's `CORS_ORIGINS` check and is not blocked by browser extensions that reject second `workers.dev` hostnames. `CORS_ORIGINS` still governs any client calling the API Worker directly.

Authentication and reads use Firebase directly (`web/app/firebase.ts`); every mutation, signed upload and moderation action goes through `/api/cbu`.

## 2. Configuration the web app needs

Non-secret public values go in `web/.env.local` locally, and in the Cloudflare build environment for deployments. Copy `web/.env.example`:

| Variable | Source |
| --- | --- |
| `NEXT_PUBLIC_FIREBASE_API_KEY` | Firebase Console → Project settings → Your apps → Web app |
| `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | same |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | `cbu-lost-and-found` |
| `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID` | `844398747634` |
| `NEXT_PUBLIC_FIREBASE_APP_ID` | `1:844398747634:web:a763726255f3db478e556b` |
| `NEXT_PUBLIC_FIREBASE_APP_CHECK_SITE_KEY` | reCAPTCHA Enterprise site key registered for App Check |
| `NEXT_PUBLIC_FIREBASE_VAPID_KEY` | FCM Web Push certificate |

Without these the app renders the "Deployment setup" notice instead of the portal — that is the expected fail-closed state, not a bug.

Secrets (Cloudinary API secret, FCM service account, App Check debug tokens) belong only in Wrangler via `wrangler secret put`, never in `NEXT_PUBLIC_*`.

## 3. Firebase console settings

For each deployed hostname, add it under **Authentication → Settings → Authorized domains**, otherwise Google sign-in fails with `auth/unauthorized-domain`:

- `cbu-find-web-staging.leokmabuku.workers.dev`
- `cbu-find-web-production.leokmabuku.workers.dev`

Register the matching reCAPTCHA Enterprise site keys for App Check on both hostnames.

## 4. Production backend values

`worker/wrangler.jsonc` production now carries the real origin:

```jsonc
"WEB_APP_URL":  "https://cbu-find-web-production.leokmabuku.workers.dev",
"CORS_ORIGINS": "https://cbu-find-web-production.leokmabuku.workers.dev"
```

`WEB_APP_URL` is used to build notification and password-reset links (`worker/src/queues.ts`); `CORS_ORIGINS` is the exact-match allowlist in `worker/src/index.ts`. Regenerate types after any change:

```bash
npm run worker:types
npm run worker:dry-run
```

## 5. Deploying

```bash
npm install
npm run build                      # contracts → worker → web
npm run test                       # 13 worker + 4 web tests
npm run test:rules                 # Firestore emulator rules tests

# API worker
npm run deploy:dry-run -w @cbu-find/worker

# Web client
npm run deploy:staging -w cbu-find-web
npm run deploy:production -w cbu-find-web
```

Deployment is a release action and requires owner approval. Nothing in this document was deployed while producing it.

## 6. Local development

```bash
cp web/.env.example web/.env.local     # fill in the values from §2
npm run dev -w cbu-find-web
```

The dev server binds `0.0.0.0` and serves on port 3000. If you open it through a proxied hostname (hosted preview, tunnel), Vite's host guard rejects the request with "Blocked request. This host is not allowed." List the host explicitly:

```bash
CBU_DEV_ALLOWED_HOSTS=".e2b.app,.trycloudflare.com" npm run dev -w cbu-find-web
```

The variable is read in `web/vite.config.ts`, affects the dev server only, and is empty by default — no deployment hostname is committed for this purpose.

## 7. Status of the standalone `CBU-FIND-WEB` repository

`leomabuku/CBU-FIND-WEB` is the earlier web client. It is **not** a second supported frontend:

- It writes directly to Firestore (`ReportForm.tsx:43`, `ItemDetails.tsx:23`, `ProfilePanel.tsx:46`, `lib/chat.ts:22`, `MessagingPanel.tsx:94`, …). The current `firestore.rules` denies all client writes, so those paths fail with `permission-denied`.
- It ships its own 246-line `firestore.rules` that would re-open direct client writes if deployed.
- It uploads to Cloudinary with an unsigned preset (`lib/media.ts:33`), which the current architecture removed.
- It has no claims or moderation workflow, both of which the API Worker enforces.

It therefore cannot run against the current backend, and deploying its rules would break the security model. Its deployed hostname should keep serving users only as a redirect to the production web app. Cloudflare Workers supports this with a route-level redirect rule, or by replacing that Worker with:

```js
export default {
  fetch(request) {
    const target = new URL(request.url);
    target.hostname = "cbu-find-web-production.leokmabuku.workers.dev";
    return Response.redirect(target.toString(), 301);
  },
};
```

Do **not** deploy `CBU-FIND-WEB/firestore.rules` or `CBU-FIND-WEB/storage.rules` to `cbu-lost-and-found`. The rules in this repository are the only supported set.

See `docs/WEB_CONNECTION_AUDIT.md` for the full evidence and options.

# CBU Find web

Responsive Vinext/React client for reports, claims, private messaging, profiles, notification/privacy settings, and moderation. Firebase supplies Auth and real-time Firestore reads. All business mutations and signed Cloudinary uploads go through the trusted Cloudflare Worker in `../worker`.

## Local setup

1. Copy `.env.example` to `.env.local` and add only Firebase public configuration, the App Check site key, VAPID public key, and Worker URL.
2. Configure Worker secrets with Wrangler; never place a service-account key or Cloudinary API secret in `NEXT_PUBLIC_*`.
3. Run `npm install` from the repository root, then `npm run dev -w cbu-find-web`.

## Verification

- `npm run lint -w cbu-find-web`
- `npm run test -w cbu-find-web`
- `npm run test:e2e -w cbu-find-web` (after installing the Playwright browser)
- `npm run deploy:dry-run -w cbu-find-web`

Phone-number sign-in and unsigned Cloudinary upload presets are intentionally unsupported.

# Cloudinary signed-upload setup

CBU Find never exposes the Cloudinary API secret to Android or web. Both clients request short-lived parameters from the authenticated Worker, upload directly to Cloudinary, and store the returned secure URL, public ID, resource type, format, and size. Legacy URL-only records continue to render.

Set `CLOUDINARY_CLOUD_NAME` as a non-secret value in `worker/wrangler.jsonc`. Add the API key and secret with interactive `wrangler secret put` for staging and production. Rotate the secret if an old value has appeared in source, screenshots, chat, or logs.

The signing endpoint includes a timestamp, user-scoped folder, and context, uses SHA-256, and expires after one hour. The deletion queue uses stored public IDs/resource types to remove known assets. Do not configure an unsigned upload preset.

Verify report and chat uploads from both clients, confirm structured media fields in Firestore, confirm sanitized logs, then delete a staging account and verify queue-driven media cleanup.

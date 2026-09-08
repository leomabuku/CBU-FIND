// Wrangler generates platform and configured binding types. Secret names are
// declared separately because secret values must never appear in wrangler.jsonc.
interface Env {
  GOOGLE_SERVICE_ACCOUNT_EMAIL: string;
  GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY: string;
  CLOUDINARY_API_KEY: string;
  CLOUDINARY_API_SECRET: string;
}

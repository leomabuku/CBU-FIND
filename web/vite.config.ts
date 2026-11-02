import vinext from "vinext";
import { defineConfig } from "vite";

// macOS Seatbelt blocks FSEvents, so Codex previews need polling for HMR.
const isCodexSeatbeltSandbox = process.env.CODEX_SANDBOX === "seatbelt";

// Hosted dev previews (Arena, E2B, tunnels) reach the dev server through a
// proxied hostname that Vite's DNS-rebinding guard rejects by default. Listing
// hosts through an environment variable keeps deployment hostnames out of the
// repository while letting a preview run: CBU_DEV_ALLOWED_HOSTS=".e2b.app".
const devAllowedHosts = (process.env.CBU_DEV_ALLOWED_HOSTS ?? "")
  .split(",")
  .map((value) => value.trim())
  .filter(Boolean);

const localBindingConfig = {
  main: "./worker/index.ts",
};

export default defineConfig(async () => {
  // Keep Wrangler and Miniflare state project-local. These are non-secret tool
  // settings; application environment belongs in ignored `.env*` files.
  process.env.WRANGLER_WRITE_LOGS ??= "false";
  process.env.WRANGLER_LOG_PATH ??= ".wrangler/logs";
  process.env.MINIFLARE_REGISTRY_PATH ??= ".wrangler/registry";

  // Wrangler snapshots its log path while the Cloudflare plugin is imported.
  const { cloudflare } = await import("@cloudflare/vite-plugin");

  return {
    server: {
      ...(isCodexSeatbeltSandbox ? { watch: { useFsEvents: false, usePolling: true } } : {}),
      ...(devAllowedHosts.length ? { allowedHosts: devAllowedHosts } : {}),
    },
    plugins: [
      vinext(),
      cloudflare({
        viteEnvironment: { name: "rsc", childEnvironments: ["ssr"] },
        config: localBindingConfig,
      }),
    ],
  };
});

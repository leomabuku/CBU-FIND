import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/e2e",
  webServer: { command: "wrangler dev --config dist/server/wrangler.json --port 3100 --local", port: 3100, reuseExistingServer: false },
  use: { baseURL: "http://127.0.0.1:3100", trace: "retain-on-failure" },
});

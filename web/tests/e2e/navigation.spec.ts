import { expect, test } from "@playwright/test";

test("shows the authentication entry point or actionable deployment setup", async ({ page }) => {
  await page.goto("/");

  const signIn = page.getByRole("heading", { name: "Sign in" });
  const setup = page.getByRole("heading", { name: "Connect CBU Find" });
  await expect(signIn.or(setup)).toBeVisible({ timeout: 15_000 });

  if (await setup.isVisible()) {
    await expect(page.getByText(/App Check site key/)).toBeVisible();
    return;
  }

  await expect(page.getByLabel("Email")).toBeVisible();
  await expect(page.getByLabel("Password")).toBeVisible();
  await expect(page.getByRole("button", { name: "Continue with Google" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Forgot password?" })).toBeVisible();
});

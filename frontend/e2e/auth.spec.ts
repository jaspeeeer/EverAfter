import { test, expect } from "@playwright/test";
import { apiRegister, uniqueEmail, uiRegister } from "./helpers";

test("unauthenticated access to the dashboard redirects to login", async ({ page }) => {
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole("button", { name: "Log in" })).toBeVisible();
});

test("role-based UI: planner gets a create action, couple does not", async ({ page }) => {
  // Planner
  await uiRegister(page, uniqueEmail("planner"), "ROLE_PLANNER");
  await expect(page.getByRole("heading", { name: "Your projects" })).toBeVisible();
  await expect(page.getByRole("button", { name: "New project" }).first()).toBeVisible();

  // Log out
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);

  // Couple
  await uiRegister(page, uniqueEmail("couple"), "ROLE_USER");
  await expect(page.getByRole("heading", { name: "Your wedding" })).toBeVisible();
  await expect(page.getByRole("button", { name: "New project" })).toHaveCount(0);
});

test("login with wrong password shows an error", async ({ page, request }) => {
  // Create the account via the API so the browser starts unauthenticated (no logout race).
  const email = uniqueEmail("wrongpw");
  await apiRegister(request, email, "ROLE_PLANNER");

  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("not-the-password");
  await page.getByRole("button", { name: "Log in" }).click();

  await expect(page.getByText(/invalid email or password/i)).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

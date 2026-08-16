import { test, expect } from "@playwright/test";
import { apiCreateProject, apiRegister, uiLogin, uniqueEmail } from "./helpers";

test("planner adds, reorders, and removes entourage members in settings", async ({ page }) => {
  const plannerEmail = uniqueEmail("entourage-planner");
  const plannerToken = await apiRegister(page.request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(page.request, plannerToken, "Entourage Settings Wedding");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await page.getByLabel("Role", { exact: true }).fill("Best Man");
  await page.getByLabel("Name", { exact: true }).fill("Juan Dela Cruz");
  await page.getByRole("button", { name: "Add to entourage" }).click();
  await expect(page.getByText("Juan Dela Cruz")).toBeVisible();

  await page.getByLabel("Role", { exact: true }).fill("Maid of Honor");
  await page.getByLabel("Name", { exact: true }).fill("Maria Santos");
  await page.getByRole("button", { name: "Add to entourage" }).click();
  await expect(page.getByText("Maria Santos")).toBeVisible();

  const items = page.locator("li", { hasText: "—" });
  await expect(items).toHaveCount(2);
  await expect(items.nth(0)).toContainText("Juan Dela Cruz");
  await expect(items.nth(1)).toContainText("Maria Santos");

  // Move the second entry up — it swaps with the first.
  await page.getByRole("button", { name: "Move Maria Santos up" }).click();
  await expect(items.nth(0)).toContainText("Maria Santos");
  await expect(items.nth(1)).toContainText("Juan Dela Cruz");

  await page.getByRole("button", { name: "Remove Juan Dela Cruz from the entourage" }).click();
  await expect(page.getByText("Removed \"Juan Dela Cruz\" from the entourage")).toBeVisible();
  await expect(items).toHaveCount(1);
  await expect(page.getByText("Juan Dela Cruz")).toHaveCount(0);
});

import { test, expect } from "@playwright/test";
import {
  apiCreateGuestWithRole,
  apiCreateGuestWithRoles,
  apiCreateProject,
  apiGuestRoleId,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

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

test("planner imports entourage members from the guest list and re-import is a no-op", async ({
  page,
}) => {
  const plannerEmail = uniqueEmail("entourage-import-planner");
  const plannerToken = await apiRegister(page.request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(page.request, plannerToken, "Entourage Import Wedding");

  const bestManRoleId = await apiGuestRoleId(page.request, plannerToken, "BEST_MAN");
  await apiCreateGuestWithRole(
    page.request,
    plannerToken,
    projectId,
    "Import Candidate",
    bestManRoleId,
  );

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await expect(page.getByText("Best Man (1)")).toBeVisible();
  await page.getByLabel("Import Candidate", { exact: true }).check();
  await page.getByRole("button", { name: "Import selected" }).click();
  await expect(page.getByText("Added 1.")).toBeVisible();

  const items = page.locator("li", { hasText: "—" });
  await expect(items).toHaveCount(1);
  await expect(items.nth(0)).toContainText("Import Candidate");
  await expect(items.nth(0)).toContainText("Best Man");

  // Re-checking the same guest and importing again is a no-op, not a duplicate row.
  await page.getByLabel("Import Candidate", { exact: true }).check();
  await page.getByRole("button", { name: "Import selected" }).click();
  await expect(page.getByText("Skipped 1 already in the entourage.")).toBeVisible();
  await expect(items).toHaveCount(1);
});

test("a guest with two eligible roles appears in both groups; checking both imports two rows", async ({
  page,
}) => {
  const plannerEmail = uniqueEmail("entourage-multirole-planner");
  const plannerToken = await apiRegister(page.request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(page.request, plannerToken, "Entourage Multi-role Wedding");

  const groomsmanRoleId = await apiGuestRoleId(page.request, plannerToken, "GROOMSMAN");
  const candleRoleId = await apiGuestRoleId(page.request, plannerToken, "CANDLE");
  await apiCreateGuestWithRoles(page.request, plannerToken, projectId, "Kevin Multi", [
    groomsmanRoleId,
    candleRoleId,
  ]);

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await expect(page.getByText("Groomsman (1)")).toBeVisible();
  await expect(page.getByText("Candle (1)")).toBeVisible();

  const groomsmanGroup = page.locator("details", { hasText: "Groomsman" });
  const candleGroup = page.locator("details", { hasText: "Candle" });
  await groomsmanGroup.getByLabel("Kevin Multi", { exact: true }).check();
  await candleGroup.getByLabel("Kevin Multi", { exact: true }).check();
  await page.getByRole("button", { name: "Import selected" }).click();
  await expect(page.getByText("Added 2.")).toBeVisible();

  const items = page.locator("li", { hasText: "—" });
  await expect(items).toHaveCount(2);
  await expect(items.filter({ hasText: "Groomsman" })).toHaveCount(1);
  await expect(items.filter({ hasText: "Candle" })).toHaveCount(1);
});

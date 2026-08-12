import { test, expect } from "@playwright/test";
import { apiCreateGuest, apiCreateProject, apiRegister, uiLogin, uniqueEmail } from "./helpers";

test("deleting a guest shows an Undo toast that brings the row back", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("undo-guest");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Undo Wedding");
  await apiCreateGuest(request, token, projectId, "Restorable Guest");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/guests`);

  await expect(page.getByText("Restorable Guest")).toBeVisible();

  await page.getByRole("button", { name: "Delete guest" }).click();
  await expect(page.getByText("Guest removed")).toBeVisible();
  await expect(page.getByText("Restorable Guest")).toHaveCount(0);

  // Click Undo in the toast before it auto-dismisses.
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.getByText("Guest restored")).toBeVisible();
  await expect(page.getByText("Restorable Guest")).toBeVisible();
});

test("undo toast disappears once clicked and does not fire twice", async ({ page, request }) => {
  const email = uniqueEmail("undo-once");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Undo Once Wedding");
  await apiCreateGuest(request, token, projectId, "One Time Guest");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/guests`);

  await page.getByRole("button", { name: "Delete guest" }).click();
  await expect(page.getByText("Guest removed")).toBeVisible();

  const undoButton = page.getByRole("button", { name: "Undo" });
  await undoButton.click();
  await expect(page.getByText("Guest restored")).toBeVisible();
  // The toast (and its Undo button) is gone — clicking it again isn't possible.
  await expect(undoButton).toHaveCount(0);
});

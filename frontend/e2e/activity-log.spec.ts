import { test, expect } from "@playwright/test";
import {
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("mutations show up on the project's Activity tab", async ({ page, request }) => {
  const email = uniqueEmail("activity");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Audit Wedding");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");

  // Create a task via the UI so the actor is the logged-in planner (not the API token).
  await page.goto(`/projects/${projectId}/checklist`);
  await page.getByRole("button", { name: "Add task" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Title").fill("Book florist");
  await dialog.getByRole("button", { name: "Add task" }).click();
  await expect(page.getByText("Book florist")).toBeVisible();

  // Visit the Activity tab and confirm the row is there, attributed to this planner.
  await page.goto(`/projects/${projectId}/activity`);
  await expect(page.getByRole("heading", { name: "Activity" })).toBeVisible();
  await expect(page.getByText('Added task "Book florist"')).toBeVisible();
  // The project's own CREATE row (recorded by ProjectService.create) should also be present.
  await expect(page.getByText('Created project "Audit Wedding"')).toBeVisible();
  // Actor email appears in the metadata row for at least one entry.
  await expect(page.getByText(email).first()).toBeVisible();
});

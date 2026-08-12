import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("planner enables party-size toggle with a cap; guest submits within it and it sticks", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("partysize-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Party Size Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Sam Guest");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await page.getByLabel("Allow guests to set their own party size").check();
  await page.getByLabel("Max party size per guest").fill("4");
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Settings saved")).toBeVisible();

  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);
  const partySizeInput = page.getByLabel("Party size (including you)");
  await expect(partySizeInput).toBeVisible();
  await partySizeInput.fill("3");
  await page.getByRole("radio", { name: "Joyfully accepts" }).check();
  await page.getByRole("button", { name: "Send RSVP" }).click();
  await expect(page.getByText("Thank you")).toBeVisible();

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/guests`);
  await expect(page.getByText("Party of 3")).toBeVisible();
});

test("with the toggle off, the RSVP form has no party-size field", async ({ page, request }) => {
  const plannerEmail = uniqueEmail("partysize-off-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "No Party Size Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Jamie Guest");

  await page.goto(`/rsvp/${rsvpToken}`);
  await expect(page.getByLabel("Party size (including you)")).toHaveCount(0);
});

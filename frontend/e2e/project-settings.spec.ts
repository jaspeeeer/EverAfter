import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("planner edits venue + times in settings, guest RSVP page shows them", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("settings-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Venue Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Alex Guest");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await page.getByLabel("Venue name").fill("Manila Cathedral");
  await page
    .getByLabel("Venue address")
    .fill("Cabildo St, Intramuros, Manila");
  await page.getByLabel("Ceremony time").fill("15:00");
  await page.getByLabel("Reception time").fill("18:30");
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Settings saved")).toBeVisible();

  // Guest opens the public invitation page — no auth cookie is required for /rsvp/*.
  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);
  await expect(page.getByText("Manila Cathedral")).toBeVisible();
  await expect(page.getByText("Cabildo St, Intramuros, Manila")).toBeVisible();
  await expect(page.getByText(/Ceremony/)).toBeVisible();
  await expect(page.getByText(/Reception/)).toBeVisible();

  const directions = page.getByRole("link", { name: /Get directions/ });
  await expect(directions).toBeVisible();
  const href = await directions.getAttribute("href");
  expect(href).toContain("maps.google.com");
  expect(href).toContain(encodeURIComponent("Cabildo St, Intramuros, Manila"));
});

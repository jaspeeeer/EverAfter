import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("attire, invitation extras, and entourage render on the public invitation page when set", async ({
  page,
}) => {
  const plannerEmail = uniqueEmail("attire-rsvp-planner");
  const plannerToken = await apiRegister(page.request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(page.request, plannerToken, "Attire RSVP Wedding");
  const rsvpToken = await apiCreateGuest(page.request, plannerToken, projectId, "Alex Guest");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await page.getByLabel("Dress code").fill("Garden party formal");
  await page.getByLabel("Notes for men").fill("Barong or long-sleeve, dark trousers");
  await page.getByLabel("Notes for women").fill("Cocktail-length or long dress");
  await page.getByLabel("RSVP by").fill("2027-05-01");
  await page.getByLabel("Social hashtag").fill("AttireWedding2027");
  await page.getByLabel("Kids policy").fill("Adults-only celebration");
  await page.getByRole("button", { name: "Add color" }).click();
  await page.getByLabel("Palette color 1", { exact: true }).fill("#f4a5a5");
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Settings saved")).toBeVisible();

  await page.getByLabel("Role", { exact: true }).fill("Best Man");
  await page.getByLabel("Name", { exact: true }).fill("Juan Dela Cruz");
  await page.getByRole("button", { name: "Add to entourage" }).click();
  await expect(page.getByText("Juan Dela Cruz")).toBeVisible();

  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);

  await expect(page.getByRole("heading", { name: "Attire", exact: true })).toBeVisible();
  await expect(page.getByText("Garden party formal")).toBeVisible();
  await expect(page.getByText("Barong or long-sleeve, dark trousers")).toBeVisible();
  await expect(page.getByText("Cocktail-length or long dress")).toBeVisible();
  await expect(page.getByLabel("Suggested color #f4a5a5")).toBeVisible();

  await expect(page.getByRole("heading", { name: "Entourage", exact: true })).toBeVisible();
  await expect(page.getByText("Best Man")).toBeVisible();
  await expect(page.getByText("Juan Dela Cruz")).toBeVisible();

  await expect(page.getByText("Please RSVP by")).toBeVisible();
  await expect(page.getByText("Adults-only celebration")).toBeVisible();
  await expect(page.getByText("AttireWedding2027")).toBeVisible();
});

test("with none of it set, the invitation page shows no attire or entourage sections", async ({
  page,
}) => {
  const plannerEmail = uniqueEmail("no-attire-rsvp-planner");
  const plannerToken = await apiRegister(page.request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(page.request, plannerToken, "No Attire RSVP Wedding");
  const rsvpToken = await apiCreateGuest(page.request, plannerToken, projectId, "Sam Guest");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);

  await expect(page.getByRole("heading", { name: "Attire", exact: true })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Entourage", exact: true })).toHaveCount(0);
});

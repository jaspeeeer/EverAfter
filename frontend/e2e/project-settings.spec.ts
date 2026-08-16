import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("planner edits ceremony + reception venues and times in settings, guest RSVP page shows them", async ({
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

  await page.getByLabel("Ceremony venue name").fill("Manila Cathedral");
  await page
    .getByLabel("Ceremony venue address")
    .fill("Cabildo St, Intramuros, Manila");
  await page.getByLabel("Ceremony time").fill("15:00");
  await page.getByLabel("Reception venue name").fill("Grand Hall");
  await page.getByLabel("Reception venue address").fill("Hall Ave, Manila");
  await page.getByLabel("Reception time").fill("18:30");
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Settings saved")).toBeVisible();

  // Guest opens the public invitation page — no auth cookie is required for /rsvp/*.
  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);

  const ceremonySection = page.locator("section", { hasText: "Ceremony" });
  const receptionSection = page.locator("section", { hasText: "Reception" });
  await expect(ceremonySection.getByText("Manila Cathedral")).toBeVisible();
  await expect(ceremonySection.getByText("Cabildo St, Intramuros, Manila")).toBeVisible();
  await expect(receptionSection.getByText("Grand Hall")).toBeVisible();
  await expect(receptionSection.getByText("Hall Ave, Manila")).toBeVisible();

  // Each section gets its own embedded map (the no-key embed only supports one query, hence
  // two separate maps rather than one combined one) and its own directions link.
  await expect(ceremonySection.locator("iframe")).toHaveCount(1);
  await expect(receptionSection.locator("iframe")).toHaveCount(1);

  const ceremonyDirections = ceremonySection.getByRole("link", { name: /Get directions/ });
  await expect(ceremonyDirections).toBeVisible();
  expect(await ceremonyDirections.getAttribute("href")).toContain(
    encodeURIComponent("Cabildo St, Intramuros, Manila"),
  );

  const receptionDirections = receptionSection.getByRole("link", { name: /Get directions/ });
  await expect(receptionDirections).toBeVisible();
  expect(await receptionDirections.getAttribute("href")).toContain(
    encodeURIComponent("Hall Ave, Manila"),
  );
});

test("with only a ceremony set, the invitation page shows no reception section at all", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("settings-ceremony-only-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Solo Venue Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Solo Guest");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await page.getByLabel("Ceremony venue name").fill("St. Peter's Chapel");
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Settings saved")).toBeVisible();

  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);

  await expect(page.getByRole("heading", { name: "Ceremony", exact: true })).toBeVisible();
  await expect(page.getByText("St. Peter's Chapel")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Reception", exact: true })).toHaveCount(0);
});

test("planner edits attire and invitation extras in settings, values persist on reload", async ({
  page,
}) => {
  const plannerEmail = uniqueEmail("attire-planner");
  const plannerToken = await apiRegister(page.request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(page.request, plannerToken, "Attire Settings Wedding");

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

  await page.reload();
  await expect(page.getByLabel("Dress code")).toHaveValue("Garden party formal");
  await expect(page.getByLabel("Notes for men")).toHaveValue(
    "Barong or long-sleeve, dark trousers",
  );
  await expect(page.getByLabel("RSVP by")).toHaveValue("2027-05-01");
  await expect(page.getByLabel("Social hashtag")).toHaveValue("AttireWedding2027");
  await expect(page.getByLabel("Kids policy")).toHaveValue("Adults-only celebration");
  await expect(page.getByLabel("Palette color 1", { exact: true })).toHaveValue("#f4a5a5");
});

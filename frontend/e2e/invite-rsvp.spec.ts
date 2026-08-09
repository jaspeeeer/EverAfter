import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateInvitation,
  apiCreateProject,
  apiGetGuests,
  apiRegister,
  uniqueEmail,
} from "./helpers";

test("an invited couple registers via the invite link and owns the project", async ({
  page,
  request,
}) => {
  // Planner sets everything up via the API.
  const plannerToken = await apiRegister(request, uniqueEmail("inv-planner"), "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Invitation Wedding");
  const coupleEmail = uniqueEmail("invited-couple");
  const inviteToken = await apiCreateInvitation(request, plannerToken, projectId, coupleEmail);

  // The couple opens the invite link: prefilled, role locked to couple.
  await page.goto(`/register?invite=${inviteToken}`);
  await expect(page.getByText("You're invited!")).toBeVisible();
  await expect(page.getByText("Invitation Wedding")).toBeVisible();
  await expect(page.getByLabel("Email")).toHaveValue(coupleEmail);

  await page.getByLabel("First name").fill("Invited");
  await page.getByLabel("Last name").fill("Couple");
  await page.getByLabel("Password").fill("password123");
  await page.getByRole("button", { name: "Create account" }).click();

  // Registration lands on the couple dashboard with the invited project attached.
  await page.waitForURL("**/dashboard");
  await expect(page.getByRole("heading", { name: "Your wedding" })).toBeVisible();
  await expect(page.getByText("Invitation Wedding")).toBeVisible();

  // And the couple can open it.
  await page.getByText("Invitation Wedding").click();
  await expect(
    page.getByRole("heading", { name: "Invitation Wedding" }),
  ).toBeVisible();
});

test("a guest can RSVP through the public link without an account", async ({
  page,
  request,
}) => {
  const plannerToken = await apiRegister(request, uniqueEmail("rsvp-planner"), "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Public RSVP Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Sam Guest");

  // Fresh browser context: no cookies, no login — just the tokenized link.
  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);

  await expect(page.getByText("Public RSVP Wedding")).toBeVisible();
  await expect(page.getByText("Hello, Sam Guest")).toBeVisible();

  await page.getByLabel("Regretfully declines").check();
  await page.getByRole("button", { name: "Send RSVP" }).click();

  await expect(page.getByText("Thank you, Sam Guest!")).toBeVisible();

  // The planner sees the updated status.
  const guests = await apiGetGuests(request, plannerToken, projectId);
  expect(
    guests.find((g) => g.firstName === "Sam" && g.lastName === "Guest")?.rsvpStatus,
  ).toBe("DECLINED");
});

test("an unknown RSVP token shows not-found, not someone's data", async ({ page }) => {
  const response = await page.goto("/rsvp/00000000-0000-0000-0000-000000000000");
  expect(response?.status()).toBe(404);
});

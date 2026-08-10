import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateProject,
  apiCreateTimelineEvent,
  apiCreateVendor,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("day-of packet renders the run sheet with no app chrome and never exposes an RSVP token", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("dayof");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Day-Of Wedding");

  const vendorId = await apiCreateVendor(request, token, projectId, "Bloom & Co Florist");
  await apiCreateTimelineEvent(request, token, projectId, "Ceremony", "15:00:00", [vendorId]);
  const rsvpToken = await apiCreateGuest(request, token, projectId, "Alex Rivera");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");

  // Reachable from the project header.
  await page.goto(`/projects/${projectId}`);
  await page.getByRole("link", { name: "Day-of sheet" }).click();
  await page.waitForURL(`**/projects/${projectId}/day-of`);

  // No app shell — the (print) route group has no header/nav/tabs.
  await expect(page.getByRole("link", { name: "All projects" })).toHaveCount(0);
  await expect(page.getByRole("navigation")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Notifications" })).toHaveCount(0);

  // All three sections, populated from the data just seeded.
  await expect(page.getByRole("heading", { name: "Run sheet" })).toBeVisible();
  await expect(page.getByText("Ceremony")).toBeVisible();
  await expect(page.getByText("Bloom & Co Florist").first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Vendor contacts" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Seating & dietary" })).toBeVisible();
  await expect(page.getByText("Alex Rivera")).toBeVisible();

  // The guest's public RSVP write-primitive must never reach a shareable printed artifact.
  await expect(page.locator("body")).not.toContainText(rsvpToken);
  const html = await page.content();
  expect(html).not.toContain(rsvpToken);
});

test("day-of packet still requires authentication", async ({ page, request }) => {
  const token = await apiRegister(request, uniqueEmail("dayof-auth"), "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Auth Check Wedding");

  await page.context().clearCookies();
  await page.goto(`/projects/${projectId}/day-of`);
  await page.waitForURL("**/login**");
});

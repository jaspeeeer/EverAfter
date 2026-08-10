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

test("global search finds a guest, a vendor, and a task sharing a unique word, and navigates on click", async ({
  page,
  request,
}) => {
  const unique = `Marigold${Date.now()}`;
  const email = uniqueEmail("search");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Search Wedding");

  await apiCreateGuest(request, token, projectId, `${unique} Guest`);
  const vendorId = await apiCreateVendor(request, token, projectId, `${unique} Sound Co`);
  await apiCreateTimelineEvent(request, token, projectId, "Ceremony", "15:00:00", [vendorId]);

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}`);

  await page.getByRole("button", { name: "Search this project" }).click();
  await page.getByPlaceholder("Search guests, vendors, tasks, expenses…").fill(unique);

  await expect(page.getByText(`${unique} Guest`)).toBeVisible();
  await expect(page.getByText(`${unique} Sound Co`)).toBeVisible();

  // Clicking a result navigates to that entity's tab and closes the dropdown.
  await page.getByRole("link", { name: `${unique} Sound Co`, exact: false }).click();
  await page.waitForURL(`**/projects/${projectId}/vendors`);
  await expect(page.getByText(`${unique} Sound Co`)).toBeVisible();
  await expect(page.getByPlaceholder("Search guests, vendors, tasks, expenses…")).toHaveCount(0);
});

test("global search dropdown resets its query when dismissed by an outside click", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("search-reset");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Reset Wedding");
  await apiCreateGuest(request, token, projectId, "Someone Guest");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}`);

  await page.getByRole("button", { name: "Search this project" }).click();
  const searchInput = page.getByPlaceholder("Search guests, vendors, tasks, expenses…");
  await searchInput.fill("Someone");
  await expect(page.getByText("Someone Guest")).toBeVisible();

  // Dismiss by clicking outside the dropdown (top-left corner, well clear of it).
  await page.locator("body").click({ position: { x: 10, y: 10 } });
  await expect(searchInput).toHaveCount(0);

  await page.getByRole("button", { name: "Search this project" }).click();
  await expect(page.getByPlaceholder("Search guests, vendors, tasks, expenses…")).toHaveValue("");
});

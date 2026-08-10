import { test, expect } from "@playwright/test";
import {
  apiCreateGuest,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("guest table: pagination, live search, and sorting", async ({ page, request }) => {
  const email = uniqueEmail("tbl-planner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Table Wedding");

  // 12 guests → 2 pages at the default page size of 10. Zero-padded names keep the order stable.
  for (let i = 1; i <= 12; i += 1) {
    await apiCreateGuest(request, token, projectId, `Guest ${String(i).padStart(2, "0")}`);
  }

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/guests`);

  // --- Default page: name ascending, first 10 only ---
  await expect(page.getByText("Guest 01")).toBeVisible();
  await expect(page.getByText("Guest 11")).toHaveCount(0);
  await expect(page.getByText(/of 12/)).toBeVisible();

  // --- Sort: flip to descending → Guest 12 leads, Guest 01 falls off page 1 ---
  await page.getByRole("button", { name: /Sorted/ }).click();
  await expect(page.getByText("Guest 12")).toBeVisible();
  await expect(page.getByText("Guest 01")).toHaveCount(0);
  // Flip back to ascending.
  await page.getByRole("button", { name: /Sorted/ }).click();
  await expect(page.getByText("Guest 01")).toBeVisible();
  await expect(page.getByText("Guest 12")).toHaveCount(0);

  // --- Pagination: Next reveals the tail ---
  await page.getByRole("button", { name: "Next page" }).click();
  await expect(page.getByText("Guest 11")).toBeVisible();
  await expect(page.getByText("Guest 01")).toHaveCount(0);

  // --- Rows per page: 25 collapses everything onto one page ---
  await page.getByLabel("Rows per page").selectOption("25");
  await expect(page.getByText("Guest 01")).toBeVisible();
  await expect(page.getByText("Guest 11")).toBeVisible();

  // --- Live search: filters as you type, no submit ---
  await page.getByPlaceholder("Search guests…").fill("Guest 07");
  await expect(page.getByText("Guest 07")).toBeVisible();
  await expect(page.getByText("Guest 01")).toHaveCount(0);

  // Clearing the box restores the full list.
  await page.getByPlaceholder("Search guests…").fill("");
  await expect(page.getByText("Guest 01")).toBeVisible();
});

test("admin user table: search narrows the list by email", async ({ page, request }) => {
  // Two couples exist platform-wide; their emails are unique and searchable.
  const wanted = uniqueEmail("tbl-find");
  const other = uniqueEmail("tbl-other");
  await apiRegister(request, wanted, "ROLE_USER");
  await apiRegister(request, other, "ROLE_USER");

  await uiLogin(page, "admin@wedding.test", "admin12345");
  await page.waitForURL("**/dashboard");
  await page.goto("/admin");

  const search = page.getByPlaceholder("Search users…");
  await expect(search).toBeVisible();

  await search.fill(wanted);
  await expect(page.getByText(wanted)).toBeVisible();
  await expect(page.getByText(other)).toHaveCount(0);
});

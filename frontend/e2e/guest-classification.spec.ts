import { test, expect } from "@playwright/test";
import { apiCreateProject, apiRegister, uiLogin, uniqueEmail } from "./helpers";

test("admin adds a guest role; planner classifies a guest and filters by priority", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("gc-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Classified Wedding");

  // --- Admin: add a guest role ---
  const uniqueRole = `Usher ${Date.now()}`;
  await uiLogin(page, "admin@wedding.test", "admin12345");
  await page.waitForURL("**/dashboard");
  await page.goto("/admin/guest-roles");
  await page.getByLabel("New role").fill(uniqueRole);
  await page.getByRole("button", { name: "Add role" }).click();
  await expect(page.getByText("Role added")).toBeVisible();
  // The seeded catalog has enough rows that a "U"-named role can land on page 2 — search for it.
  await page.getByPlaceholder("Search roles…").fill(uniqueRole);
  await expect(page.getByText(uniqueRole)).toBeVisible();

  // --- Planner: add a guest with the full classification ---
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");

  await page.goto(`/projects/${projectId}/guests`);
  await page.getByRole("button", { name: "Add guest" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("First name").fill("Priya");
  await dialog.getByLabel("Last name").fill("Sponsor");
  await dialog.locator("#priority").selectOption("A");
  await dialog.locator("#relatedTo").selectOption({ label: "Groom" });
  await dialog.locator("#relationship").selectOption({ label: "Close friend" });
  await dialog.locator("#roleId").selectOption({ label: uniqueRole });
  await dialog.getByRole("button", { name: "Add guest" }).click();
  await expect(page.getByText("Guest added")).toBeVisible();

  // The row shows the Priority + Role badges and the relationship/side in the meta line.
  await expect(page.getByText("Priya Sponsor")).toBeVisible();
  await expect(page.getByText("Priority A")).toBeVisible();
  // Target the badge (a span) — the role name is also present as text inside the role-filter
  // <option>, so a bare getByText would be ambiguous.
  await expect(page.locator("span").filter({ hasText: uniqueRole })).toBeVisible();
  await expect(page.getByText(/Groom's side/)).toBeVisible();
  await expect(page.getByText("Close friend")).toBeVisible();

  // A second, unclassified guest for the priority filter to exclude.
  await page.getByRole("button", { name: "Add guest" }).click();
  const dialog2 = page.getByRole("dialog");
  await dialog2.getByLabel("First name").fill("Plain");
  await dialog2.getByLabel("Last name").fill("Guest");
  await dialog2.getByRole("button", { name: "Add guest" }).click();
  await expect(page.getByText("Plain Guest")).toBeVisible();

  // Filtering by Priority A narrows to just the classified guest.
  await page.getByRole("button", { name: "A", exact: true }).click();
  await expect(page.getByText("Priya Sponsor")).toBeVisible();
  await expect(page.getByText("Plain Guest")).toHaveCount(0);

  // --- Role filter ---
  // Reset the priority chip (second "All" — the first is the RSVP row).
  await page.getByRole("button", { name: "All", exact: true }).nth(1).click();
  await expect(page.getByText("Priya Sponsor")).toBeVisible();
  await expect(page.getByText("Plain Guest")).toBeVisible();

  const roleFilter = page.locator("#role-filter");
  // Narrow to just the sponsored role.
  await roleFilter.selectOption({ label: uniqueRole });
  await expect(page.getByText("Priya Sponsor")).toBeVisible();
  await expect(page.getByText("Plain Guest")).toHaveCount(0);

  // "No role" surfaces guests with no role assigned.
  await roleFilter.selectOption("NONE");
  await expect(page.getByText("Plain Guest")).toBeVisible();
  await expect(page.getByText("Priya Sponsor")).toHaveCount(0);

  // Reset shows everyone.
  await roleFilter.selectOption("ALL");
  await expect(page.getByText("Priya Sponsor")).toBeVisible();
  await expect(page.getByText("Plain Guest")).toBeVisible();
});

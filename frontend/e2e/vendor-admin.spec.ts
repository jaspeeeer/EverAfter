import { test, expect } from "@playwright/test";
import {
  apiCategoryId,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uiRegister,
  uniqueEmail,
} from "./helpers";

const API = process.env.API_BASE_URL ?? "http://localhost:8080";

test("admin manages categories, directory, and reports; planner uses them", async ({
  page,
  request,
}) => {
  // A planner + project exist (via the API) for the planner-side checks.
  const plannerEmail = uniqueEmail("va-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Catalog Wedding");

  // --- Admin: add a category ---
  await uiLogin(page, "admin@wedding.test", "admin12345");
  await page.waitForURL("**/dashboard");

  const uniqueCategory = `Officiant ${Date.now()}`;
  await page.goto("/admin/vendor-categories");
  await page.getByLabel("New category").fill(uniqueCategory);
  await page.getByRole("button", { name: "Add category" }).click();
  await expect(page.getByText("Category added")).toBeVisible();
  await expect(page.getByText(uniqueCategory)).toBeVisible();

  // --- Admin: add a directory vendor ---
  await page.goto("/admin/vendor-directory");
  await page.getByRole("button", { name: "New vendor" }).click();
  const dirDialog = page.getByRole("dialog");
  await dirDialog.getByLabel("Vendor name").fill("Everbloom Florals");
  await dirDialog.locator("#dir-category").selectOption({ label: "Florist" });
  await dirDialog.getByLabel("Typical price").fill("45000");
  await dirDialog.getByRole("button", { name: "Add vendor" }).click();
  await expect(page.getByText("Everbloom Florals")).toBeVisible();

  // --- Admin: reports render ---
  await page.goto("/admin/reports");
  await expect(page.getByRole("heading", { name: "Vendors by category" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "In-demand vendors" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Booking conversion" })).toBeVisible();

  // --- Planner: add the directory vendor + set an agreed price → budget line ---
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");

  await page.goto(`/projects/${projectId}/vendors`);
  await page.getByRole("button", { name: "Add from directory" }).click();
  const pickDialog = page.getByRole("dialog");
  await pickDialog.getByText("Everbloom Florals").click();
  await pickDialog.getByRole("button", { name: "Add vendor" }).click();
  await expect(page.getByText("Everbloom Florals")).toBeVisible();

  // Edit it to set the full amount to be paid.
  await page.getByRole("button", { name: "Edit vendor" }).click();
  const editDialog = page.getByRole("dialog");
  await editDialog.getByLabel("Full amount to be paid").fill("50000");
  await editDialog.getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByText("Vendor updated")).toBeVisible();

  // The agreed price shows as a "from vendor" budget line.
  await page.goto(`/projects/${projectId}/budget`);
  await expect(page.getByText("Everbloom Florals")).toBeVisible();
  await expect(page.getByText("From vendor")).toBeVisible();
  await expect(page.getByText("₱50,000").first()).toBeVisible();
});

test("an admin-added category shows up in the Add Expense dropdown (one synced lookup)", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("sync-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Sync Wedding");

  // Admin adds a brand-new category.
  const uniqueCategory = `Officiant ${Date.now()}`;
  await uiLogin(page, "admin@wedding.test", "admin12345");
  await page.waitForURL("**/dashboard");
  await page.goto("/admin/vendor-categories");
  await page.getByLabel("New category").fill(uniqueCategory);
  await page.getByRole("button", { name: "Add category" }).click();
  await expect(page.getByText("Category added")).toBeVisible();

  // The planner opens Add Expense — the new category is in the (shared) dropdown.
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");

  await page.goto(`/projects/${projectId}/budget`);
  await page.getByRole("button", { name: "Add expense" }).click();
  await expect(page.getByRole("dialog").locator("#category")).toContainText(uniqueCategory);
});

test("an expense can be mapped to a vendor and stays editable", async ({ page, request }) => {
  const email = uniqueEmail("map-planner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Mapping Wedding");

  // A vendor to map the expense to.
  const categoryId = await apiCategoryId(request, token, "CATERING");
  const vendorRes = await request.post(`${API}/api/projects/${projectId}/vendors`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: "Sweet Cakes Co", categoryId, booked: false },
  });
  expect(vendorRes.ok()).toBeTruthy();

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/budget`);

  await page.getByRole("button", { name: "Add expense" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Description").fill("Cake deposit");
  await dialog.getByLabel("Amount").fill("8000");
  await dialog.locator("#vendorId").selectOption({ label: "Sweet Cakes Co" });
  await dialog.getByRole("button", { name: "Add expense" }).click();

  await expect(page.getByText("Expense added")).toBeVisible();
  // The row shows the mapped vendor, and (unlike a read-only agreed-price line) stays editable.
  await expect(page.getByText("Cake deposit")).toBeVisible();
  await expect(page.getByText("Sweet Cakes Co")).toBeVisible();
  await expect(page.getByRole("button", { name: "Edit expense" })).toBeVisible();
});

test("vendor payments track installments against the full amount and feed the budget", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("pay-planner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Payments Wedding");

  // A vendor with a full amount of ₱100,000.
  const categoryId = await apiCategoryId(request, token, "VENUE");
  const vendorRes = await request.post(`${API}/api/projects/${projectId}/vendors`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: "Grand Hall", categoryId, booked: true, agreedPrice: 100000 },
  });
  expect(vendorRes.ok()).toBeTruthy();

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/vendors`);

  // Record a ₱40,000 installment.
  await page.getByRole("button", { name: "Payments" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("Full amount")).toBeVisible();
  await dialog.getByLabel("Amount").fill("40000");
  await dialog.getByRole("button", { name: "Record payment" }).click();
  await expect(page.getByText("Payment recorded")).toBeVisible();
  // Balance drops to ₱60,000.
  await expect(dialog.getByText("₱60,000")).toBeVisible();

  // The vendor row shows the running progress.
  await page.keyboard.press("Escape");
  await expect(page.getByText("₱40,000 / ₱100,000 paid")).toBeVisible();

  // The budget's managed line is partially paid.
  await page.goto(`/projects/${projectId}/budget`);
  await expect(page.getByText("₱40,000 / ₱100,000")).toBeVisible();
});

test("a package vendor bundles items under one price and shows as one budget line", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("pkg-planner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Package Wedding");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/vendors`);

  // Add the package itself with a bundled price.
  await page.getByRole("button", { name: "Add vendor" }).click();
  const pkgDialog = page.getByRole("dialog");
  await pkgDialog.getByLabel("Vendor name").fill("All-In Coordination Package");
  await pkgDialog.locator("#categoryId").selectOption({ label: "Other" });
  await pkgDialog.getByLabel("Full amount to be paid").fill("300000");
  await pkgDialog.getByRole("button", { name: "Add vendor" }).click();
  await expect(page.getByText("Vendor added")).toBeVisible();
  await expect(page.getByText("All-In Coordination Package")).toBeVisible();

  // Bundle an item under it — a distinct modal, no price field.
  await page.getByRole("button", { name: "Add item" }).click();
  const itemDialog = page.getByRole("dialog");
  await expect(itemDialog.getByRole("heading", { name: "Add package item" })).toBeVisible();
  await itemDialog.getByLabel("Item name").fill("Catering (bundled)");
  await itemDialog.locator("#categoryId").selectOption({ label: "Catering" });
  await itemDialog.getByRole("button", { name: "Add item" }).click();
  await expect(page.getByText("Item added")).toBeVisible();

  // The package shows a "Package" badge; the item renders nested underneath it.
  await expect(page.getByText("Package", { exact: true })).toBeVisible();
  await expect(page.getByText("Catering (bundled)")).toBeVisible();

  // Only the package's bundled price appears on the budget — the item adds no line of its own.
  await page.goto(`/projects/${projectId}/budget`);
  await expect(page.getByText("All-In Coordination Package")).toBeVisible();
  await expect(page.getByText("Catering (bundled)")).toHaveCount(0);
  await expect(page.getByText("₱300,000").first()).toBeVisible();
});

test("a couple cannot reach the admin vendor pages", async ({ page }) => {
  await uiRegister(page, uniqueEmail("va-couple"), "ROLE_USER");

  for (const path of ["/admin/vendor-categories", "/admin/vendor-directory", "/admin/reports"]) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/dashboard/);
  }
});

test("the vendor category picker is data-driven and rejects couples' writes", async ({
  request,
}) => {
  // The public category list is available to any authenticated user…
  const couple = await apiRegister(request, uniqueEmail("va-cat-couple"), "ROLE_USER");
  const listed = await request.get(`${API}/api/vendor-categories`, {
    headers: { Authorization: `Bearer ${couple}` },
  });
  expect(listed.ok()).toBeTruthy();
  expect((await listed.json()).length).toBeGreaterThan(0);

  // …but only admins may create categories.
  const created = await request.post(`${API}/api/admin/vendor-categories`, {
    headers: { Authorization: `Bearer ${couple}` },
    data: { name: "Sneaky" },
  });
  expect(created.status()).toBe(403);
});

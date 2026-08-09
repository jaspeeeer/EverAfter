import { test, expect } from "@playwright/test";
import { apiCreateProject, apiRegister, uiLogin, uniqueEmail } from "./helpers";

test("planner attaches, downloads, and removes a file on a vendor", async ({ page, request }) => {
  const email = uniqueEmail("attach");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Attachment Wedding");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");

  // Create a vendor via the UI (the category select already defaults to the first option).
  await page.goto(`/projects/${projectId}/vendors`);
  await page.getByRole("button", { name: "Add vendor" }).click();
  const createDialog = page.getByRole("dialog");
  await createDialog.getByLabel("Vendor name").fill("Bloom Florist");
  await createDialog.getByRole("button", { name: "Add vendor" }).click();
  await expect(page.getByText("Vendor added")).toBeVisible();
  await expect(page.getByText("Bloom Florist")).toBeVisible();

  // Re-open the vendor for editing — the "Files" section only renders for an existing vendor.
  await page.getByRole("button", { name: "Edit vendor" }).click();
  const editDialog = page.getByRole("dialog");
  await expect(editDialog.getByText("Files", { exact: true })).toBeVisible();
  await expect(editDialog.getByText("No files attached yet.")).toBeVisible();

  // Upload a small in-memory PDF. The hidden <input type="file"> accepts a Playwright virtual
  // file directly — no need to click the visible "Attach a file" button first.
  await editDialog.locator('input[type="file"]').setInputFiles({
    name: "contract.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4 fake contract bytes"),
  });
  await expect(page.getByText("File attached")).toBeVisible();

  const fileLink = editDialog.getByRole("link", { name: "contract.pdf" });
  await expect(fileLink).toBeVisible();

  // Download through the authenticated proxy route and confirm it's the exact PDF bytes.
  const href = await fileLink.getAttribute("href");
  expect(href).toBeTruthy();
  const downloadResponse = await page.request.get(href!);
  expect(downloadResponse.ok()).toBeTruthy();
  expect(downloadResponse.headers()["content-type"]).toContain("application/pdf");
  expect(await downloadResponse.text()).toBe("%PDF-1.4 fake contract bytes");

  // Delete it and confirm the row disappears.
  await editDialog.getByRole("button", { name: "Delete contract.pdf" }).click();
  await expect(page.getByText("Attachment removed")).toBeVisible();
  await expect(editDialog.getByText("No files attached yet.")).toBeVisible();
});

test("planner attaches and removes a receipt on an expense", async ({ page, request }) => {
  const email = uniqueEmail("attach-expense");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Expense Attachment Wedding");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");

  // Create an expense via the UI (category select defaults to the first option).
  await page.goto(`/projects/${projectId}/budget`);
  await page.getByRole("button", { name: "Add expense" }).click();
  const createDialog = page.getByRole("dialog");
  await createDialog.getByLabel("Description").fill("Catering deposit");
  await createDialog.getByLabel("Amount").fill("1500");
  await createDialog.getByRole("button", { name: "Add expense" }).click();
  await expect(page.getByText("Expense added")).toBeVisible();
  await expect(page.getByText("Catering deposit")).toBeVisible();

  // Re-open the expense for editing — the "Files" section only renders for an existing expense.
  await page.getByRole("button", { name: "Edit expense" }).click();
  const editDialog = page.getByRole("dialog");
  await expect(editDialog.getByText("Files", { exact: true })).toBeVisible();
  await expect(editDialog.getByText("No files attached yet.")).toBeVisible();

  await editDialog.locator('input[type="file"]').setInputFiles({
    name: "receipt.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4 fake receipt bytes"),
  });
  await expect(page.getByText("File attached")).toBeVisible();
  await expect(editDialog.getByRole("link", { name: "receipt.pdf" })).toBeVisible();

  await editDialog.getByRole("button", { name: "Delete receipt.pdf" }).click();
  await expect(page.getByText("Attachment removed")).toBeVisible();
  await expect(editDialog.getByText("No files attached yet.")).toBeVisible();
});

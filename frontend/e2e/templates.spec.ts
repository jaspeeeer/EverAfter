import { test, expect } from "@playwright/test";
import {
  apiCreateProject,
  apiGetTasks,
  apiRegister,
  uiLogin,
  uiRegister,
  uniqueEmail,
} from "./helpers";

test("admin creates a checklist template through the editor", async ({ page }) => {
  await uiLogin(page, "admin@wedding.test", "admin12345");
  await page.waitForURL("**/dashboard");

  await page.goto("/admin/templates");
  await expect(
    page.getByRole("heading", { name: "Templates", exact: true }),
  ).toBeVisible();

  // Open the checklist editor and build a two-task template via the dynamic rows.
  await page
    .getByRole("button", { name: "New template" })
    .first()
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Template name").fill("E2E Starter Checklist");
  // Item titles are unique so assertions can't collide with the seeded starter's preview.
  await dialog.getByLabel("Task 1 title").fill("Reserve the ice sculpture");
  await dialog.getByLabel("Task 1 days before wedding").fill("300");
  await dialog.getByRole("button", { name: "Add task" }).click();
  await dialog.getByLabel("Task 2 title").fill("Hire the string quartet");
  await dialog.getByRole("button", { name: "Create template" }).click();

  await expect(page.getByText("Template created")).toBeVisible();
  await expect(page.getByText("E2E Starter Checklist")).toBeVisible();
  await expect(page.getByText("Reserve the ice sculpture (300d before)")).toBeVisible();
  await expect(page.getByText("Hire the string quartet")).toBeVisible();
});

test("planner applies the seeded checklist template and tasks appear", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("tpl-planner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Templated Wedding");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/checklist`);

  await page.getByRole("button", { name: "Use template" }).click();
  const dialog = page.getByRole("dialog");
  // Pick the seeded starter template and confirm.
  await dialog.getByText("Classic Wedding Checklist").click();
  await dialog.getByRole("button", { name: "Add tasks" }).click();

  await expect(page.getByText(/Added \d+ tasks/)).toBeVisible();
  await expect(page.getByText("Book venue")).toBeVisible();
  await expect(page.getByText("Book photographer")).toBeVisible();

  // Every template item became a task on the project.
  const tasks = await apiGetTasks(request, token, projectId);
  expect(tasks.length).toBeGreaterThanOrEqual(10);
  expect(tasks.every((t) => t.status === "TODO")).toBeTruthy();
});

test("a couple sees no template actions on their checklist", async ({
  page,
  request,
}) => {
  // A planner creates a project owned by a fresh couple account.
  const plannerToken = await apiRegister(request, uniqueEmail("tpl-owner"), "ROLE_PLANNER");
  const coupleEmail = uniqueEmail("tpl-couple");
  await uiRegister(page, coupleEmail, "ROLE_USER");

  const res = await request.post(
    `${process.env.API_BASE_URL ?? "http://localhost:8080"}/api/projects`,
    {
      headers: { Authorization: `Bearer ${plannerToken}` },
      data: { name: "Couple Checklist Wedding", ownerEmail: coupleEmail },
    },
  );
  expect(res.ok()).toBeTruthy();
  const projectId = (await res.json()).id as string;

  await page.goto(`/projects/${projectId}/checklist`);
  await expect(page.getByRole("button", { name: "Add task" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Use template" })).toHaveCount(0);
});

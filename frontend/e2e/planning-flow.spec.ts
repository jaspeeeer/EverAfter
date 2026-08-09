import { test, expect } from "@playwright/test";
import {
  apiCreateProject,
  apiGetTasks,
  apiRegister,
  dragTo,
  uiLogin,
  uniqueEmail,
} from "./helpers";

test("planner works a project across checklist, budget and guests", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("flow");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Flow Wedding");
  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");

  // --- Checklist: add a task, then DRAG it to Done ---
  await page.goto(`/projects/${projectId}/checklist`);
  await page.getByRole("button", { name: "Add task" }).click();
  const taskDialog = page.getByRole("dialog");
  await taskDialog.getByLabel("Title").fill("Book venue");
  await taskDialog.getByRole("button", { name: "Add task" }).click();

  await expect(page.getByText("Book venue")).toBeVisible();

  await dragTo(
    page,
    page.getByRole("button", { name: "Drag Book venue" }),
    page.locator("h4", { hasText: "Done" }),
  );

  // Optimistic move lands instantly; the server confirms via toast + API state.
  await expect(page.getByText("Task moved")).toBeVisible();
  await expect
    .poll(async () => {
      const tasks = await apiGetTasks(request, token, projectId);
      return tasks.find((t) => t.title === "Book venue")?.status;
    })
    .toBe("DONE");

  // --- Budget: add an expense; roll-up and category chart update ---
  await page.goto(`/projects/${projectId}/budget`);
  await page.getByRole("button", { name: "Add expense" }).click();
  const expenseDialog = page.getByRole("dialog");
  await expenseDialog.getByLabel("Description").fill("Venue deposit");
  await expenseDialog.getByLabel("Amount").fill("5000");
  await expenseDialog.locator("#category").selectOption({ label: "Venue" });
  await expenseDialog.getByRole("button", { name: "Add expense" }).click();

  await expect(page.getByText("Venue deposit")).toBeVisible();
  await expect(page.getByText("₱5,000").first()).toBeVisible();
  // Spend-by-category chart appears once there is at least one expense.
  await expect(page.getByText("Spend by category")).toBeVisible();

  // --- Guests: add an attending party of 2 with a table assignment ---
  await page.goto(`/projects/${projectId}/guests`);
  await page.getByRole("button", { name: "Add guest" }).click();
  const guestDialog = page.getByRole("dialog");
  await guestDialog.getByLabel("First name").fill("Alex");
  await guestDialog.getByLabel("Last name").fill("Jamie");
  await guestDialog.locator("#rsvpStatus").selectOption("ATTENDING");
  await guestDialog.getByLabel("Party size").fill("2");
  await guestDialog.getByLabel("Table").fill("7");
  await guestDialog.getByRole("button", { name: "Add guest" }).click();

  await expect(page.getByText("Alex Jamie")).toBeVisible();
  await expect(page.getByText("Party of 2")).toBeVisible();
  await expect(page.getByText("Table 7")).toBeVisible();
});

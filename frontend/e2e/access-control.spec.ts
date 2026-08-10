import { test, expect } from "@playwright/test";
import {
  apiCreateProject,
  apiRegister,
  uiLogin,
  uiRegister,
  uniqueEmail,
} from "./helpers";

test("a planner cannot open another planner's project", async ({ page, request }) => {
  // Owner + project created via the API.
  const ownerToken = await apiRegister(request, uniqueEmail("owner"), "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, ownerToken, "Private Wedding");

  // A different planner logs in through the UI and tries the URL directly.
  await uiRegister(page, uniqueEmail("intruder"), "ROLE_PLANNER");
  const response = await page.goto(`/projects/${projectId}`);

  expect(response?.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "Private Wedding" })).toHaveCount(0);
});

test("a couple cannot open a project they don't own", async ({ page, request }) => {
  const ownerToken = await apiRegister(request, uniqueEmail("owner"), "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, ownerToken, "Not Your Wedding");

  await uiRegister(page, uniqueEmail("stranger"), "ROLE_USER");
  const response = await page.goto(`/projects/${projectId}`);

  expect(response?.status()).toBe(404);
});

test("the managing planner can open their own project", async ({ page, request }) => {
  const email = uniqueEmail("owner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Owner Visible Wedding");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");

  const response = await page.goto(`/projects/${projectId}`);
  expect(response?.status()).toBe(200);
  await expect(
    page.getByRole("heading", { name: "Owner Visible Wedding" }),
  ).toBeVisible();
});

test("an admin can open any project", async ({ page, request }) => {
  const token = await apiRegister(request, uniqueEmail("owner"), "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Admin Visible Wedding");

  // Seeded bootstrap admin (created on backend startup).
  await uiLogin(page, "admin@wedding.test", "admin12345");
  await page.waitForURL("**/dashboard");

  const response = await page.goto(`/projects/${projectId}`);
  expect(response?.status()).toBe(200);
  await expect(
    page.getByRole("heading", { name: "Admin Visible Wedding" }),
  ).toBeVisible();
});

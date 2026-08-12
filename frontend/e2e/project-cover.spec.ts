import { test, expect } from "@playwright/test";
import path from "path";
import fs from "fs";
import os from "os";
import {
  apiCreateGuest,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uniqueEmail,
} from "./helpers";

/** A tiny valid JPEG so the upload passes the backend's content-type/byte check. */
function writeTinyJpeg(): string {
  // 1x1 white JPEG, base64-encoded.
  const base64 =
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMDAwMDAwQEBAQFBQUGBgYGBgcHBwgICAkJCQsLCwwMDA0NDQ4ODg8PDxAQEBEREhISExMUFBQVFRUWFhYXFxgYGRkaGhsbHBwdHR4eHx8gICH/2wBDAQMDAwMDBAQEBAUFBQYGBgYGBwcHCAgICQkJCwsLDAwMDQ0NDg4ODw8PEBAQERESEhITExQUFBUVFRYWFhcXGBgZGRoaGxscHR0eHh8fIP/AABEIAAEAAQMBIgACEQEDEQH/xAAVAAEBAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVP/Z";
  const filePath = path.join(os.tmpdir(), `cover-${Date.now()}.jpg`);
  fs.writeFileSync(filePath, Buffer.from(base64, "base64"));
  return filePath;
}

test("planner uploads a project cover; it shows on the public invitation page and can be removed", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("cover-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Cover Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Cover Guest");
  const jpegPath = writeTinyJpeg();

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  await page.getByRole("button", { name: "Upload photo" }).click();
  await page.locator('input[name="file"]').setInputFiles(jpegPath);
  await expect(page.getByText("Cover photo updated")).toBeVisible();
  await expect(page.getByRole("button", { name: "Replace photo" })).toBeVisible();

  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);
  const banner = page.locator(`img[src="/api/public/rsvp/${rsvpToken}/cover"]`);
  await expect(banner).toBeVisible();

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);
  await page.getByRole("button", { name: "Remove" }).click();
  await expect(page.getByText("Cover photo removed")).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload photo" })).toBeVisible();

  await page.context().clearCookies();
  await page.goto(`/rsvp/${rsvpToken}`);
  await expect(page.locator(`img[src="/api/public/rsvp/${rsvpToken}/cover"]`)).toHaveCount(0);

  fs.unlinkSync(jpegPath);
});

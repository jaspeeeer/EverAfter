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
function writeTinyJpeg(name: string): string {
  const base64 =
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMDAwMDAwQEBAQFBQUGBgYGBgcHBwgICAkJCQsLCwwMDA0NDQ4ODg8PDxAQEBEREhISExMUFBQVFRUWFhYXFxgYGRkaGhsbHBwdHR4eHx8gICH/2wBDAQMDAwMDBAQEBAUFBQYGBgYGBwcHCAgICQkJCwsLDAwMDQ0NDg4ODw8PEBAQERESEhITExQUFBUVFRYWFhcXGBgZGRoaGxscHR0eHh8fIP/AABEIAAEAAQMBIgACEQEDEQH/xAAVAAEBAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVP/Z";
  const filePath = path.join(os.tmpdir(), `${name}-${Date.now()}.jpg`);
  fs.writeFileSync(filePath, Buffer.from(base64, "base64"));
  return filePath;
}

test("planner uploads separate ceremony and reception photos independently of the cover", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("venue-photo-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Venue Photo Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Venue Photo Guest");
  const ceremonyJpeg = writeTinyJpeg("ceremony");
  const receptionJpeg = writeTinyJpeg("reception");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  const ceremonyForm = page.locator("form", {
    has: page.getByRole("button", { name: "Upload ceremony photo" }),
  });
  await page.getByRole("button", { name: "Upload ceremony photo" }).click();
  await ceremonyForm.locator('input[name="file"]').setInputFiles(ceremonyJpeg);
  await expect(page.getByText("Ceremony photo updated")).toBeVisible();
  await expect(page.getByRole("button", { name: "Replace ceremony photo" })).toBeVisible();

  // Reception photo is independent — uploading it doesn't touch the ceremony one.
  const receptionForm = page.locator("form", {
    has: page.getByRole("button", { name: "Upload reception photo" }),
  });
  await page.getByRole("button", { name: "Upload reception photo" }).click();
  await receptionForm.locator('input[name="file"]').setInputFiles(receptionJpeg);
  await expect(page.getByText("Reception photo updated")).toBeVisible();
  await expect(page.getByRole("button", { name: "Replace ceremony photo" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Replace reception photo" })).toBeVisible();

  // Neither upload created/replaced the (untouched) cover slot.
  await expect(page.getByRole("button", { name: "Upload cover photo" })).toBeVisible();

  // Both photos are already servable from the public (no-auth) proxy routes, independently of
  // whether the RSVP page renders them yet — that full visual redesign is a separate phase.
  await page.context().clearCookies();
  const ceremonyRes = await page.request.get(`/api/public/rsvp/${rsvpToken}/ceremony-photo`);
  expect(ceremonyRes.ok()).toBeTruthy();
  expect(ceremonyRes.headers()["content-type"]).toBe("image/jpeg");
  const receptionRes = await page.request.get(`/api/public/rsvp/${rsvpToken}/reception-photo`);
  expect(receptionRes.ok()).toBeTruthy();
  expect(receptionRes.headers()["content-type"]).toBe("image/jpeg");

  fs.unlinkSync(ceremonyJpeg);
  fs.unlinkSync(receptionJpeg);
});

test("planner uploads men's and women's attire photos; both render on the public invitation page", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("attire-photo-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "Attire Photo Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Attire Photo Guest");
  const menJpeg = writeTinyJpeg("men-attire");
  const womenJpeg = writeTinyJpeg("women-attire");

  await uiLogin(page, plannerEmail);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/settings`);

  const menForm = page.locator("form", {
    has: page.getByRole("button", { name: "Upload men's attire photo" }),
  });
  await page.getByRole("button", { name: "Upload men's attire photo" }).click();
  await menForm.locator('input[name="file"]').setInputFiles(menJpeg);
  await expect(page.getByText("Men's attire photo updated")).toBeVisible();
  await expect(page.getByRole("button", { name: "Replace men's attire photo" })).toBeVisible();

  const womenForm = page.locator("form", {
    has: page.getByRole("button", { name: "Upload women's attire photo" }),
  });
  await page.getByRole("button", { name: "Upload women's attire photo" }).click();
  await womenForm.locator('input[name="file"]').setInputFiles(womenJpeg);
  await expect(page.getByText("Women's attire photo updated")).toBeVisible();

  // Both photos serve from the public proxy routes.
  await page.context().clearCookies();
  const menRes = await page.request.get(`/api/public/rsvp/${rsvpToken}/attire-men-photo`);
  expect(menRes.ok()).toBeTruthy();
  expect(menRes.headers()["content-type"]).toBe("image/jpeg");
  const womenRes = await page.request.get(`/api/public/rsvp/${rsvpToken}/attire-women-photo`);
  expect(womenRes.ok()).toBeTruthy();
  expect(womenRes.headers()["content-type"]).toBe("image/jpeg");

  // On the public RSVP page, both images render inside the Attire section.
  await page.goto(`/rsvp/${rsvpToken}`);
  const attire = page.locator("section", {
    has: page.getByRole("heading", { name: "Attire", exact: true }),
  });
  await expect(attire.locator(`img[src$="/attire-men-photo"]`)).toBeVisible();
  await expect(attire.locator(`img[src$="/attire-women-photo"]`)).toBeVisible();

  fs.unlinkSync(menJpeg);
  fs.unlinkSync(womenJpeg);
});

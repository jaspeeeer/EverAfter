import { test, expect } from "@playwright/test";
import { apiCreateGuest, apiCreateProject, apiRegister, uniqueEmail } from "./helpers";

const API = process.env.API_BASE_URL ?? "http://localhost:8080";

test("RSVP page carries og:/twitter: meta tags, with no og:image when the project has no cover", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("og-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "OG Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Og Guest");

  await page.goto(`/rsvp/${rsvpToken}`);

  await expect(page).toHaveTitle("You're invited to OG Wedding");
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute(
    "content",
    "You're invited to OG Wedding",
  );
  await expect(page.locator('meta[property="og:description"]')).toHaveCount(1);
  await expect(page.locator('meta[property="og:image"]')).toHaveCount(0);
  await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute("content", "summary");
});

test("with a cover photo set, the RSVP page's og:image is an absolute URL that serves the image", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("og-cover-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "OG Cover Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Og Cover Guest");

  const jpegBase64 =
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMDAwMDAwQEBAQFBQUGBgYGBgcHBwgICAkJCQsLCwwMDA0NDQ4ODg8PDxAQEBEREhISExMUFBQVFRUWFhYXFxgYGRkaGhsbHBwdHR4eHx8gICH/2wBDAQMDAwMDBAQEBAUFBQYGBgYGBwcHCAgICQkJCwsLDAwMDQ0NDg4ODw8PEBAQERESEhITExQUFBUVFRYWFhcXGBgZGRoaGxscHR0eHh8fIP/AABEIAAEAAQMBIgACEQEDEQH/xAAVAAEBAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVP/Z";
  const putRes = await request.post(`${API}/api/projects/${projectId}/cover`, {
    headers: { Authorization: `Bearer ${plannerToken}` },
    multipart: {
      file: {
        name: "cover.jpg",
        mimeType: "image/jpeg",
        buffer: Buffer.from(jpegBase64, "base64"),
      },
    },
  });
  expect(putRes.ok()).toBeTruthy();

  await page.goto(`/rsvp/${rsvpToken}`);

  const imageMeta = page.locator('meta[property="og:image"]');
  await expect(imageMeta).toHaveCount(1);
  const imageUrl = await imageMeta.getAttribute("content");
  expect(imageUrl).toMatch(/^https?:\/\/.+\/api\/public\/rsvp\/.+\/cover$/);

  const imageRes = await page.request.get(imageUrl!);
  expect(imageRes.ok()).toBeTruthy();
  expect(imageRes.headers()["content-type"]).toBe("image/jpeg");
});

import { test, expect } from "@playwright/test";
import { apiCreateGuest, apiCreateProject, apiRegister, uniqueEmail } from "./helpers";

const API = process.env.API_BASE_URL ?? "http://localhost:8080";

test("guest sees an add-to-calendar link that downloads a valid .ics file", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("ics-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "ICS Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "Ics Guest");

  // Give the project a wedding date directly via the API — the backend rejects a calendar
  // request for a project with none, and settings-form E2E coverage already exercises the UI
  // path for setting it.
  const putRes = await request.put(`${API}/api/projects/${projectId}`, {
    headers: { Authorization: `Bearer ${plannerToken}` },
    data: { name: "ICS Wedding", weddingDate: "2027-09-18" },
  });
  expect(putRes.ok()).toBeTruthy();

  await page.goto(`/rsvp/${rsvpToken}`);
  const link = page.getByRole("link", { name: "Add to calendar" });
  await expect(link).toBeVisible();
  await expect(link).toHaveAttribute("href", `/api/public/rsvp/${rsvpToken}/calendar.ics`);

  const downloadPromise = page.waitForEvent("download");
  await link.click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("wedding.ics");
  const stream = await download.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(chunk as Buffer);
  const body = Buffer.concat(chunks).toString("utf-8");
  expect(body).toContain("BEGIN:VCALENDAR");
  expect(body).toContain("SUMMARY:ICS Wedding");
});

test("with no wedding date set, the RSVP page has no add-to-calendar link", async ({
  page,
  request,
}) => {
  const plannerEmail = uniqueEmail("ics-nodate-planner");
  const plannerToken = await apiRegister(request, plannerEmail, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, plannerToken, "No Date ICS Wedding");
  const rsvpToken = await apiCreateGuest(request, plannerToken, projectId, "No Date Guest");

  await page.goto(`/rsvp/${rsvpToken}`);
  await expect(page.getByRole("link", { name: "Add to calendar" })).toHaveCount(0);
});

import { test, expect, type APIRequestContext } from "@playwright/test";
import {
  apiCategoryId,
  apiCreateProject,
  apiRegister,
  uiLogin,
  uiRegister,
  uniqueEmail,
} from "./helpers";

const API = process.env.API_BASE_URL ?? "http://localhost:8080";

async function apiCreateBeautyVendor(
  request: APIRequestContext,
  token: string,
  projectId: string,
  name: string,
): Promise<string> {
  const categoryId = await apiCategoryId(request, token, "BEAUTY");
  const res = await request.post(`${API}/api/projects/${projectId}/vendors`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name, categoryId, booked: true },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).id as string;
}

async function apiGetTimeline(
  request: APIRequestContext,
  token: string,
  projectId: string,
): Promise<Array<{ title: string; startTime: string }>> {
  const res = await request.get(`${API}/api/projects/${projectId}/timeline`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as Array<{ title: string; startTime: string }>;
}

test("planner quick-starts the day, links a supplier, and sees it in the slot", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("tlv-planner");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Run Sheet Wedding");
  await apiCreateBeautyVendor(request, token, projectId, "Glam Studio");

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/timeline`);

  // Quick-start populates the calendar grid.
  await page.getByRole("button", { name: "Add a typical day" }).click();
  await expect(page.getByText(/Added \d+ events/)).toBeVisible();
  await expect(page.getByText("Ceremony", { exact: true })).toBeVisible();
  await expect(page.getByText("After-party")).toBeVisible();

  // Add a custom event linked to the supplier.
  await page.getByRole("button", { name: "Add event" }).click();
  const form = page.getByRole("dialog");
  await form.getByLabel("Title").fill("Makeup touch-up");
  await form.getByLabel("Starts").fill("14:00");
  await form.getByLabel("Ends (optional)").fill("14:30");
  await form.getByText("Glam Studio").click();
  await form.getByRole("button", { name: "Add event" }).click();
  await expect(page.getByText("Event added")).toBeVisible();

  // Click the slot → the supplier panel shows the vendor.
  await page.getByRole("button", { name: /Makeup touch-up at 2:00 PM/ }).click();
  const detail = page.getByRole("dialog");
  await expect(detail.getByText("Suppliers involved")).toBeVisible();
  await expect(detail.getByText("Glam Studio")).toBeVisible();
  await expect(detail.getByText("Beauty")).toBeVisible();
  await expect(detail.getByText("Booked")).toBeVisible();
});

test("planner drags an event to a new time slot", async ({ page, request }) => {
  const email = uniqueEmail("tlv-drag");
  const token = await apiRegister(request, email, "ROLE_PLANNER");
  const projectId = await apiCreateProject(request, token, "Drag Wedding");

  // One event at 10:00 via the API.
  const created = await request.post(`${API}/api/projects/${projectId}/timeline`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { title: "Ceremony rehearsal", startTime: "10:00", endTime: "11:00" },
  });
  expect(created.ok()).toBeTruthy();

  await uiLogin(page, email);
  await page.waitForURL("**/dashboard");
  await page.goto(`/projects/${projectId}/timeline`);

  const block = page.getByRole("button", { name: /Ceremony rehearsal/ });
  await expect(block).toBeVisible();

  // Center the block in the viewport first — near the edges dnd-kit's auto-scroll would
  // scroll the page mid-drag and inflate the drop delta.
  await block.evaluate((el) => el.scrollIntoView({ block: "center" }));

  // Drag down 60px = 60 minutes at PX_PER_MIN = 1.
  const box = await block.boundingBox();
  if (!box) throw new Error("block not visible");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2 + 60, { steps: 12 });
  await page.mouse.up();

  await expect(page.getByText("Moved to 11:00 AM")).toBeVisible();
  await expect
    .poll(async () => {
      const events = await apiGetTimeline(request, token, projectId);
      return events.find((e) => e.title === "Ceremony rehearsal")?.startTime;
    })
    .toBe("11:00:00");
});

test("the couple sees the timeline read-only", async ({ page, request }) => {
  const plannerToken = await apiRegister(request, uniqueEmail("tlv-owner"), "ROLE_PLANNER");
  const coupleEmail = uniqueEmail("tlv-couple");
  await uiRegister(page, coupleEmail, "ROLE_USER");

  const res = await request.post(`${API}/api/projects`, {
    headers: { Authorization: `Bearer ${plannerToken}` },
    data: { name: "Couple Timeline Wedding", ownerEmail: coupleEmail },
  });
  expect(res.ok()).toBeTruthy();
  const projectId = (await res.json()).id as string;

  const event = await request.post(`${API}/api/projects/${projectId}/timeline`, {
    headers: { Authorization: `Bearer ${plannerToken}` },
    data: { title: "Ceremony", startTime: "11:00", endTime: "12:00" },
  });
  expect(event.ok()).toBeTruthy();

  await page.goto(`/projects/${projectId}/timeline`);
  await expect(page.getByText("Ceremony", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Add event" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Add a typical day" })).toHaveCount(0);
});

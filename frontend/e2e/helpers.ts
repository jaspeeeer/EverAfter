import { expect, type APIRequestContext, type Page } from "@playwright/test";

const API = process.env.API_BASE_URL ?? "http://localhost:8080";
const PASSWORD = "password123";

type RegisterableRole = "ROLE_PLANNER" | "ROLE_USER";

let counter = 0;

/** A unique email per call, so tests don't collide on the users table. */
export function uniqueEmail(prefix: string): string {
  counter += 1;
  return `${prefix}-${Date.now()}-${counter}@e2e.test`;
}

// --- Backend API helpers (fast test setup, bypassing the UI) ---

export async function apiRegister(
  request: APIRequestContext,
  email: string,
  role: RegisterableRole,
): Promise<string> {
  const res = await request.post(`${API}/api/auth/register`, {
    data: { firstName: "E2E", lastName: "User", email, password: PASSWORD, role },
  });
  expect(res.ok(), `register ${email}`).toBeTruthy();
  return (await res.json()).token as string;
}

export async function apiCreateProject(
  request: APIRequestContext,
  token: string,
  name: string,
): Promise<string> {
  const res = await request.post(`${API}/api/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name },
  });
  expect(res.ok(), `create project ${name}`).toBeTruthy();
  return (await res.json()).id as string;
}

/** Looks up a seeded vendor-category id by slug (categories are data now). */
export async function apiCategoryId(
  request: APIRequestContext,
  token: string,
  slug: string,
): Promise<string> {
  const res = await request.get(`${API}/api/vendor-categories`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok(), "list vendor categories").toBeTruthy();
  const categories = (await res.json()) as Array<{ id: string; slug: string }>;
  const match = categories.find((c) => c.slug === slug);
  if (!match) throw new Error(`No seeded category with slug ${slug}`);
  return match.id;
}

/** Creates a booked vendor under the seeded first category and returns its id. */
export async function apiCreateVendor(
  request: APIRequestContext,
  token: string,
  projectId: string,
  name: string,
): Promise<string> {
  const catRes = await request.get(`${API}/api/vendor-categories`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(catRes.ok(), "list vendor categories").toBeTruthy();
  const categoryId = (await catRes.json())[0].id as string;

  const res = await request.post(`${API}/api/projects/${projectId}/vendors`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name,
      categoryId,
      contactEmail: "vendor@e2e.test",
      phone: "+63 900 000 0000",
      booked: true,
    },
  });
  expect(res.ok(), `create vendor ${name}`).toBeTruthy();
  return (await res.json()).id as string;
}

/** Creates a timeline event, optionally linking suppliers, and returns its id. */
export async function apiCreateTimelineEvent(
  request: APIRequestContext,
  token: string,
  projectId: string,
  title: string,
  startTime: string,
  vendorIds: string[] = [],
): Promise<string> {
  const res = await request.post(`${API}/api/projects/${projectId}/timeline`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { title, startTime, vendorIds },
  });
  expect(res.ok(), `create timeline event ${title}`).toBeTruthy();
  return (await res.json()).id as string;
}

export async function apiCreateInvitation(
  request: APIRequestContext,
  token: string,
  projectId: string,
  email: string,
): Promise<string> {
  const res = await request.post(`${API}/api/projects/${projectId}/invitations`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { email },
  });
  expect(res.ok(), `create invitation for ${email}`).toBeTruthy();
  return (await res.json()).token as string;
}

/** Creates a guest (name split on the first space into firstName/lastName) and returns its public RSVP token. */
export async function apiCreateGuest(
  request: APIRequestContext,
  token: string,
  projectId: string,
  name: string,
): Promise<string> {
  const [firstName, ...rest] = name.split(" ");
  const lastName = rest.length > 0 ? rest.join(" ") : null;
  const res = await request.post(`${API}/api/projects/${projectId}/guests`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { firstName, lastName, rsvpStatus: "PENDING", partySize: 1 },
  });
  expect(res.ok(), `create guest ${name}`).toBeTruthy();
  return (await res.json()).rsvpToken as string;
}

/** Looks up a seeded guest role's id by slug (e.g. "BEST_MAN"). */
export async function apiGuestRoleId(
  request: APIRequestContext,
  token: string,
  slug: string,
): Promise<string> {
  const res = await request.get(`${API}/api/guest-roles`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok(), "list guest roles").toBeTruthy();
  const roles = (await res.json()) as Array<{ id: string; slug: string }>;
  const match = roles.find((r) => r.slug === slug);
  if (!match) throw new Error(`No seeded guest role with slug ${slug}`);
  return match.id;
}

/** Creates a guest carrying the given role and returns the guest's own id (not the RSVP token). */
export async function apiCreateGuestWithRole(
  request: APIRequestContext,
  token: string,
  projectId: string,
  name: string,
  roleId: string,
): Promise<string> {
  return apiCreateGuestWithRoles(request, token, projectId, name, [roleId]);
}

/** Creates a guest carrying several roles at once and returns the guest's own id. */
export async function apiCreateGuestWithRoles(
  request: APIRequestContext,
  token: string,
  projectId: string,
  name: string,
  roleIds: string[],
): Promise<string> {
  const [firstName, ...rest] = name.split(" ");
  const lastName = rest.length > 0 ? rest.join(" ") : null;
  const res = await request.post(`${API}/api/projects/${projectId}/guests`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { firstName, lastName, rsvpStatus: "PENDING", partySize: 1, roleIds },
  });
  expect(res.ok(), `create guest ${name} with roles`).toBeTruthy();
  return (await res.json()).id as string;
}

export async function apiGetTasks(
  request: APIRequestContext,
  token: string,
  projectId: string,
): Promise<Array<{ title: string; status: string }>> {
  const res = await request.get(`${API}/api/projects/${projectId}/tasks`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok(), "list tasks").toBeTruthy();
  return (await res.json()) as Array<{ title: string; status: string }>;
}

export async function apiGetGuests(
  request: APIRequestContext,
  token: string,
  projectId: string,
): Promise<
  Array<{ firstName: string; lastName: string | null; rsvpStatus: string; partySize: number | null }>
> {
  const res = await request.get(`${API}/api/projects/${projectId}/guests`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok(), "list guests").toBeTruthy();
  return (await res.json()) as Array<{
    firstName: string;
    lastName: string | null;
    rsvpStatus: string;
    partySize: number | null;
  }>;
}

/** Real pointer drag (works with dnd-kit's PointerSensor + distance activation). */
export async function dragTo(
  page: Page,
  source: ReturnType<Page["locator"]>,
  target: ReturnType<Page["locator"]>,
): Promise<void> {
  const from = await source.boundingBox();
  const to = await target.boundingBox();
  if (!from || !to) throw new Error("drag source/target not visible");
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 15 });
  await page.mouse.up();
}

// --- UI helpers (exercise the real auth flow so the cookie is set) ---

export async function uiRegister(
  page: Page,
  email: string,
  role: RegisterableRole,
): Promise<void> {
  await page.goto("/register");
  await page.getByLabel("First name").fill("E2E");
  await page.getByLabel("Last name").fill("User");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.locator("#role").selectOption(role);
  await page.getByRole("button", { name: "Create account" }).click();
  await page.waitForURL("**/dashboard");
}

export async function uiLogin(
  page: Page,
  email: string,
  password: string = PASSWORD,
): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
}

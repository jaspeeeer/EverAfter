# Wedding Planner System

A SaaS-style wedding planner with role-based access control (Admin / Planner / Couple).

- **Backend:** Java 17 + Spring Boot (Web, Data JPA, Security) — `/backend`
- **Database:** Supabase (managed PostgreSQL; schema managed by Flyway)
- **Frontend:** Next.js + Tailwind CSS — `/frontend` _(added in Phase 3)_

## Roles

| Role           | Scope                                              |
| -------------- | -------------------------------------------------- |
| `ROLE_ADMIN`   | All projects and users across the platform         |
| `ROLE_PLANNER` | Only the projects they own (one planner → many)    |
| `ROLE_USER`    | Their single wedding project (one couple → one)    |

## Prerequisites

- Java 17 (`java -version`)
- A Supabase project (managed Postgres) for running the app
- Docker (only for the Testcontainers-backed test suite — not needed to run the app)
- No global Maven needed — use the bundled wrapper `./mvnw`

## Database configuration (Supabase)

The app connects to Supabase via the **Session Pooler** (port 5432, IPv4-friendly, Flyway-safe).
Credentials are read from a local `.env` (never committed).

```bash
cd backend
cp .env.example .env
# edit .env with your Supabase Session Pooler URL, username (postgres.<project-ref>) and password
# from Supabase Dashboard -> Project Settings -> Database -> Connection pooling -> Session mode
```

## Phase 1 — Backend persistence foundation

JPA entities, relational mappings, repositories, Flyway schema, and Testcontainers mapping tests.

## Phase 2 — Core API & Security

Stateless **JWT** authentication, method-level **`@PreAuthorize`** RBAC data isolation, a service
layer (including budget roll-ups), REST controllers, and comprehensive JUnit 5 / Mockito /
MockMvc security tests.

### Auth

- `POST /api/auth/register` — self-register as `ROLE_PLANNER` or `ROLE_USER` (admin is seeded, not
  self-registrable); returns a bearer token.
- `POST /api/auth/login` — returns a bearer token. Seeded admin: `admin@wedding.test` /
  `admin12345` (override via `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`).
- `GET /api/auth/me` — current user.

Send the token as `Authorization: Bearer <token>` on every other endpoint.

### Resources

| Endpoint | Access rule |
| --- | --- |
| `GET /api/projects` | role-scoped: admin=all, planner=own, couple=their one |
| `POST /api/projects` | `ADMIN` or `PLANNER` |
| `GET/PUT /api/projects/{id}` | admin, managing planner, or owning couple |
| `DELETE /api/projects/{id}` | admin or managing planner |
| `GET /api/projects/{id}/budget` | same as project access |
| `/api/projects/{id}/tasks\|vendors\|expenses` | inherits project access |

### Run the tests (Testcontainers spins up its own Postgres)

```bash
cd backend
./mvnw clean verify
```

### Run the app against Supabase

```bash
cd backend
# with backend/.env populated, boot the backend;
# Flyway applies migrations to the Supabase `public` schema and Hibernate validates the entities
./mvnw spring-boot:run
```

Inspect the resulting schema in the **Supabase SQL editor** (or `psql` with your pooler URL):

```sql
select table_name from information_schema.tables where table_schema = 'public';
```

## Phase 3 — Frontend scaffolding & UI components

**Next.js 16 (App Router) + React 19 + Tailwind CSS v4.** A wedding-themed design system
(dusty rose / sage / champagne tokens, Playfair Display + Inter fonts, light & dark tokens) and a
set of reusable UI components. Not yet wired to the API — that's Phase 4.

Components in `frontend/components/`: `Button`, `Card` (+ header/title/content/footer), `Input`,
`Textarea`, `Label`, `Badge`, `Modal` (portal, Escape/backdrop close, scroll lock), `Spinner`,
and the checklist board (`KanbanColumn`, `KanbanCard`). The home page (`app/page.tsx`) is a live
showcase of the whole system.

## Phase 4 — Feature integration (MVP)

The frontend is wired to the Spring API with **JWT auth in an httpOnly cookie**. All API calls run
**server-side** (Server Components for reads, Server Actions for mutations), so the browser never
calls Spring directly and **no CORS config is needed**. Edge `proxy.ts` (Next 16's renamed
middleware) guards routes.

- **Auth:** `/login`, `/register` (couples & planners) — a server action proxies Spring's
  `/api/auth/*`, stores the token cookie, and redirects.
- **Role-based UI:** the dashboard renders per role — admin sees *All projects*, planners see
  *Your projects* + a **New project** action, couples see *Your wedding* with no create action.
- **Screens:** Dashboard (role-scoped project grid), project Overview (budget roll-up + counts),
  Checklist (Kanban with add / move / delete), Vendors (add / book / delete).

### Run the full app (backend + frontend)

```bash
# 1. Backend (needs backend/.env → Supabase); serves on :8080
cd backend && ./mvnw spring-boot:run

# 2. Frontend (in another shell); serves on :3000, proxies to the backend server-side
cd frontend && npm install && npm run dev
```

Seeded admin login: `admin@wedding.test` / `admin12345` (override via env). The frontend reads the
backend URL from `API_BASE_URL` (default `http://localhost:8080`).

## Phase 5 — Advanced features & E2E testing

- **Budget Tracker** (frontend over the existing expense API): full budget roll-up + expense CRUD
  with paid/unpaid toggling, on the project **Budget** tab.
- **Guest List CRM** (new end-to-end feature): a `guests` table (Flyway `V2`), `Guest` entity +
  project-scoped service/controller (same `@PreAuthorize` isolation), and a **Guests** tab with
  RSVP tracking, party sizes, dietary notes, and attending/pending/declined summaries.
- **Playwright E2E** (`frontend/e2e/`): auth, role-based UI, cross-tenant access control (a planner
  / couple gets a **404** on someone else's project; owner and admin get **200**), and a full
  planning flow across checklist, budget and guests.

### Run the E2E suite

```bash
scripts/e2e.sh          # spins up an ephemeral Postgres + backend, then runs Playwright
```

E2E runs against a throwaway local Postgres (never Supabase), so it stays isolated and offline.
The backend suite (`cd backend && ./mvnw verify`) remains on Testcontainers.

## Post-MVP features

- **Drag-and-drop Kanban** — checklist cards move between columns via `@dnd-kit` with optimistic
  updates (grip handle, drop-target highlight, drag overlay).
- **Couple invitation flow** — a planner issues a token invite (`/register?invite=<token>`); the
  couple registers through it and is atomically attached as the project's owning couple.
  Invitations are single-use and manage-gated (`invitations` table, Flyway `V3`).
- **Budget breakdown chart** — spend-by-category stacked bars (paid vs outstanding) on the Budget
  tab, with CVD-validated series colors per theme (`--chart-*` tokens).
- **Guest extras** — CSV import/export, table/seating assignments, a dietary-needs rollup for
  caterers, and per-guest RSVP links.
- **Admin dashboard** — `/admin` (admin-only): platform stats, role breakdown, and user
  enable/disable (self-disable is blocked).
- **Public RSVP page** — `/rsvp/<token>`: guests view the invite and submit/update their RSVP,
  party size and dietary needs with no account; keyed by an unguessable per-guest token.
- **Templates** — admins author checklist/vendor presets (`/admin/templates`); planners apply
  them to a project in one click ("Use template" / "Add from template"), with due dates counted
  back from the wedding date. Starter templates are seeded automatically.
- **Wedding-day timeline** — a Google-Calendar-style day grid (Timeline tab): planners map
  events into time slots from the makeup call to the after-party, link the suppliers involved,
  and drag blocks to reschedule (15-min snapping); clicking a slot shows its suppliers. Couples
  view the run sheet read-only. A "typical day" quick-start seeds the standard schedule.
- **Vendor catalog & reports** — admin-managed vendor **categories** (a lookup table, not a hard
  enum; delete deactivates when in use), a **global vendor directory** planners add from, a
  vendor **agreed price** that feeds the Budget tab, and admin **reports** (vendors by category,
  in-demand vendors over a date range, booking conversion) with CSV export. See
  [docs/vendor-catalog.md](docs/vendor-catalog.md).
- **Attachments** — contracts, receipts, and quotes (PDF or image, up to 10 MB) hung off a
  vendor, a vendor payment, or an expense, stored outside Postgres and gated by the same
  project-access rule as the owner resource. See [docs/attachments.md](docs/attachments.md).

### UX polish

Toast notifications on every mutation, inline **edit** modals for vendors / guests / expenses, a
wedding-day **countdown** in the project header, **overdue-task** highlighting on the Kanban, a
persisted **dark-mode** toggle, guest **RSVP filters + name search** (and vendor search), and
**₱ (PHP)** currency throughout.

## Project layout

```
.
├─ scripts/e2e.sh         # ephemeral-Postgres + backend + Playwright runner
├─ backend/               # Spring Boot app + Maven wrapper
│  ├─ .env.example        # template for Supabase credentials (copy to .env)
│  └─ src/main/java/com/wedding/planner
│     ├─ domain/          # JPA entities + enums (User, Project, Task, Vendor, Expense, Guest)
│     └─ repository/      # Spring Data JPA repositories
└─ frontend/              # Next.js 16 App Router app
   ├─ proxy.ts            # edge route guard (renamed middleware)
   ├─ e2e/                # Playwright specs: auth, access-control, planning-flow
   ├─ app/
   │  ├─ (auth)/          # login + register (public)
   │  ├─ (app)/           # authenticated shell: dashboard, projects/[id]/{checklist,vendors,budget,guests}
   │  └─ actions/         # server actions: auth, projects, tasks, vendors, expenses, guests
   ├─ components/         # ui/, kanban/, auth/, projects/, checklist/, vendors/, budget/, guests/
   └─ lib/                # api (server fetch), session (cookie/JWT), data, types, utils
```

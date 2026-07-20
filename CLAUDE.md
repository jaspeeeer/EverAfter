# Wedding Planner System ("Ever After") — Technical Guide

SaaS-style wedding planner with strict role-based access control. Monorepo: Spring Boot API
(`backend/`) + Next.js App Router frontend (`frontend/`), backed by Supabase (managed Postgres).

## Stack

| Layer | Tech |
| --- | --- |
| Backend | Java 17, Spring Boot 3 (Web, Data JPA, Security), JJWT 0.12 (HS256) |
| Database | Supabase Postgres via **Session Pooler** (runtime); schema owned by **Flyway** (`V1`–`V3`) |
| Backend tests | JUnit 5 + Mockito + MockMvc, **Testcontainers** (`postgres:16-alpine`) |
| Frontend | Next.js 16 (App Router, RSC, Server Actions), React 19, Tailwind CSS v4, `@dnd-kit/core` |
| E2E | Playwright (Chromium), orchestrated by `scripts/e2e.sh` |

## Commands

```bash
# Backend tests (needs Docker running for Testcontainers)
cd backend && ./mvnw clean verify

# Run backend against Supabase (needs backend/.env — see backend/.env.example)
cd backend && ./mvnw spring-boot:run          # :8080

# Frontend
cd frontend && npm run dev                     # :3000
cd frontend && npm run build                   # compile + typecheck

# Full E2E (ephemeral local Postgres on :5433 + backend + Playwright; never touches Supabase)
scripts/e2e.sh                                 # pass-through args, e.g. scripts/e2e.sh --headed
```

There is **no global Maven/Gradle/psql** on this machine — always use `./mvnw`; use a throwaway
`postgres:16-alpine` docker container for ad-hoc psql. Docker Desktop is often stopped; start it
(`open -a Docker`) before Testcontainers/E2E runs.

## Architecture

### RBAC model (the core invariant)
Three roles; data isolation is enforced **server-side**, never only in the UI:
- `ROLE_ADMIN` — sees/manages everything (seeded account, cannot self-register).
- `ROLE_PLANNER` — only projects where they are `projects.planner_id`.
- `ROLE_USER` (couple) — only the single project where they are `projects.owner_id`.

Enforcement lives in `backend/.../security/ProjectSecurity.java`, referenced from
`@PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")` (view/edit) and
`canManage` (delete/invitations: admin or managing planner only). Child resources
(tasks/vendors/expenses/guests) are gated on their `projectId` path segment, and services verify
the child actually belongs to that project so IDs can't be swapped across tenants. Listing is
role-scoped in `ProjectService.listVisible`.

### Auth flow
JWT (subject = email, `uid` claim) issued by `POST /api/auth/{register,login}`. The **frontend
stores it in an httpOnly cookie** (`wp_token`) and every Spring call happens **server-side**
(Server Components read via `lib/data.ts`; mutations via Server Actions in `app/actions/*`).
The browser never calls :8080 directly → no CORS config exists. `frontend/proxy.ts` (Next 16's
renamed middleware) does coarse cookie-presence routing; real validation is `requireUser()` →
`GET /api/auth/me`. Do NOT redirect token-bearing requests away from `/login` in the proxy — the
proxy can't verify tokens and an expired cookie would loop.

Public, tokenized endpoints live under `/api/public/**` (permitAll): per-guest RSVP
(`guests.rsvp_token`) and invitation preview (`invitations.token`). They expose no internal IDs.

### Database
Flyway owns the schema (`backend/src/main/resources/db/migration/`); Hibernate runs
`ddl-auto: validate`. Tables: `roles`, `users`, `user_roles`, `projects`, `tasks`, `vendors`,
`expenses`, `guests` (+`table_number`, `rsvp_token`), `invitations`, the template catalog
(`checklist_templates`/`_items`, `vendor_templates`/`_items` — admin-authored, applied by
planners; see `docs/templates.md`), and the wedding-day timeline (`timeline_events` +
`timeline_event_vendors` M2M with DB-level `ON DELETE CASCADE`; times before 04:00 sort as
after-midnight — see `docs/timeline.md`). New schema change = new `V<n>__*.sql`, never edit an
applied migration. `DataInitializer` seeds the 3 roles + admin account + starter templates on
boot (idempotent).

Supabase specifics: connect via the **Session Pooler**
(`aws-1-ap-northeast-2.pooler.supabase.com:5432`, user `postgres.<project-ref>`,
`sslmode=require`) — the direct host is IPv6-only and unreachable here. Credentials come from
gitignored `backend/.env` (loaded by `spring-dotenv`). Keep the Hikari pool small (5).

## Conventions

- **Backend:** DTOs are Java records with `from(entity)` mappers; controllers stay thin;
  services own transactions; errors → RFC-7807 via `GlobalExceptionHandler`. New child resources
  copy the Task/Vendor/Guest pattern (repo `findByProjectId`, project-scoped service, controller
  gated with `@projectSecurity`).
- **Frontend:** design tokens in `app/globals.css` (Tailwind v4 `@theme`, light + `.dark`);
  reusable UI in `components/ui/`; `cn()` from `lib/utils.ts`; types mirroring backend DTOs in
  `lib/types.ts`; money formatting is **Philippine Peso** via `formatMoney` in `lib/format.ts`
  (single source). Form mutations use `useActionState` + server actions returning
  `{ error?, ok? }`; success closes modals and fires a toast (`components/ui/toast.tsx`).
- **Charts:** series colors must come from the validated `--chart-*` tokens (light reuses brand
  tokens; dark has its own steps). Identity never color-alone (legend + text labels).

## Testing

- Unit/integration: 65 backend tests. `AbstractIntegrationTest` (full HTTP, MockMvc, own
  Postgres container) vs `AbstractPostgresContainerTest` (`@DataJpaTest` slice, separate pristine
  container — do not share containers; the seeded roles break uniqueness tests).
- E2E: `frontend/e2e/*.spec.ts`, sequential (one shared backend), unique emails per test.
  Kanban drag uses `dragTo()` in `e2e/helpers.ts` (mouse-level, works with dnd-kit's
  PointerSensor distance-5 activation).
- Test users for manual poking: see `testuser.txt` (root).

## Gotchas / environment quirks

- Docker 29 rejects the old docker-java API default — surefire pins `-Dapi.version=1.41` in
  `backend/pom.xml`; don't remove it.
- Next 16 renamed `middleware.ts` → `proxy.ts` (exported function `proxy`). Its bundled docs are
  authoritative: `frontend/node_modules/next/dist/docs/` (see `frontend/AGENTS.md`).
- `PUT` endpoints replace the whole resource — when adding a field, thread it through every
  frontend update call site or it silently nulls (this bit `guests.tableNumber` once).
- E2E and backend suites never touch Supabase; only `spring-boot:run` (via `.env`) does.

## Docs

Per-feature documentation lives in `docs/` (one file per feature).

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

Public, tokenized endpoints live under `/api/public/**` (permitAll): per-guest RSVP/invitation
(`guests.rsvp_token`, `docs/rsvp.md`) and the couple onboarding invite preview
(`invitations.token`, `docs/couple-onboarding-invites.md` — a different "invitation" from the
guest-facing one). They expose no internal IDs.

### Database
Flyway owns the schema (`backend/src/main/resources/db/migration/`); Hibernate runs
`ddl-auto: validate`. Tables: `roles`, `users`, `user_roles`, `projects`, `tasks`, `vendors`,
`expenses`, `guests` (+`table_number`, `rsvp_token`, plus planner-internal classification —
`priority` char(1) A/B/C, `related_to` GROOM/BRIDE, `relationship` (8 values), all nullable
enums like `rsvp_status`, `V12` — **never exposed on the public RSVP DTOs**), `guest_roles`
(admin-managed lookup for `guests.role_id`, same shape as `vendor_categories`, `V12`),
`invitations`, the template catalog
(`checklist_templates`/`_items`, `vendor_templates`/`_items` — admin-authored, applied by
planners; see `docs/templates.md`), the wedding-day timeline (`timeline_events` +
`timeline_event_vendors` M2M with DB-level `ON DELETE CASCADE`; times before 04:00 sort as
after-midnight — see `docs/timeline.md`), and the vendor catalog: `vendor_categories`
(admin-managed lookup — **`VendorCategory` is now an @Entity, not an enum** — vendors &
template items reference it by FK; delete deactivates if in use), `vendor_directory` (global
admin catalog), plus `vendors.agreed_price` (the vendor's full amount)/`directory_id` and
`expenses.vendor_id` (a vendor mapping; the `managed` line is the agreed-price→budget link — `V9`
made it `ON DELETE SET NULL` so manual mappings survive vendor deletion); see
`docs/vendor-catalog.md`. **`expenses` also reference `vendor_categories` by FK (`category_id`,
`V8`) — expense and vendor categories are one admin-managed lookup, so the old `ExpenseCategory`
enum is gone** (`V8` backfilled it: `FLOWERS`→Florist, `GIFTS`→Other, else same slug). Vendor
payments/installments live in `vendor_payments` (`V10`); a managed expense's `expenses.paid_amount`
= the sum of its vendor's payments, and the budget's paid total is `sum(paid_amount)` (partial, not
all-or-nothing). **`vendors.parent_id`** (`V11`, self-FK `ON DELETE CASCADE`) makes a vendor a
"package item" nested under another (top-level) vendor — a package holds the one bundled
price/payments; an item has none of its own (`VendorService.syncVendorExpense` skips items), and
the admin vendor reports + `totalVendors` stat filter `parent is null` so items aren't double
counted. New schema change = new `V<n>__*.sql`, never edit an applied migration. `DataInitializer`
seeds the 3 roles + admin account + starter templates on boot (idempotent).

**Soft delete (`V18`).** `vendors`/`guests`/`expenses`/`tasks` carry a nullable `deleted_at`
timestamp, and each of those four entities has `@SQLRestriction("deleted_at is null")` — **every
read against them is implicitly filtered**, including derived finders, JPQL, and even
`JpaRepository.count()`; you do not add `AndDeletedAtIsNull` to a method name yourself. A new
finder on one of these four needs no special handling to respect this — the one thing to get
right is anything that reaches a soft-deletable row through an **implicit association join**
(e.g. `VendorPaymentRepository` reaching `Vendor` via `p.vendor`), where the restriction's
propagation isn't guaranteed and the safe move is an **explicit** `...deletedAt is null` predicate
in the query, not an assumption. The five FKs from tasks/vendors/expenses/guests/timeline_events
back to `projects` are `ON DELETE CASCADE` as of `V18` specifically so that a project with
soft-deleted children still purges cleanly — `@SQLRestriction` hides tombstones from JPA's own
`CascadeType.ALL` collection traversal, so the DB cascade is what actually removes them now.
`{Vendor,Guest,Expense,Task}Service.delete` stamps `deletedAt` instead of removing the row, and
`POST …/{id}/restore` reverses it via a **native** `@Modifying` query (native bypasses
`@SQLRestriction`, which is exactly why a plain `findById`-based restore can't work — the row is
invisible to it). The frontend surfaces this as an **Undo** action in the delete confirmation
toast, no separate trash UI. A soft-deletable entity referenced elsewhere via a nullable
`@ManyToOne` needs its own explicit unmap on delete if that reference used to clear itself for
free via a DB-level `ON DELETE SET NULL` — a soft delete never fires that trigger (the row still
physically exists); see `docs/undo-delete.md` for the real bug this caused
(`Expense.vendor` → a soft-deleted `Vendor`) before it was caught. See `docs/undo-delete.md` first,
then `docs/guests.md`, `docs/vendors.md`, `docs/budget.md`, `docs/checklist.md` for the per-entity
notes.

**Invitation-page metadata (`V19`, venue split into ceremony/reception in `V20`).** `projects`
carries **two** locations — `ceremony_venue_name`/`ceremony_venue_address` and
`reception_venue_name`/`reception_venue_address` (`V20`; `V19`'s original single
`venue_name`/`venue_address` was dropped after a backfill into the ceremony columns), plus
`ceremony_time`/`reception_time` (`V19`, unchanged by the split — already per-event) — so the
public RSVP page can show more than a bare date. Edited via the **Settings** tab
(`docs/project-settings.md`) under the existing `canAccess` rule (no new permission check:
admin/managing planner/owning couple could already `PUT` a project). `V19` also added
`allow_guest_party_size`/`max_party_size`/`cover_attachment_id`; `V20` added
`ceremony_photo_attachment_id`/`reception_photo_attachment_id`; `V23` added
`attire_men_photo_attachment_id`/`attire_women_photo_attachment_id` — five independent
single-photo slots sharing one `AttachmentOwnerType.PROJECT` (see `docs/project-photos.md`). `V21` added seven
more nullable scalars (`dress_code`, `attire_notes_men`, `attire_notes_women`, `attire_palette` —
a comma-separated hex list, `rsvp_deadline`, `kids_policy`, `social_hashtag`) plus a genuinely new
child table, `entourage_members` (`id`, `project_id` FK `ON DELETE CASCADE`, `role`, `name`,
`sort_order`, no soft delete) — the wedding party is an ordered list, not a scalar, so unlike the
other `V21` additions it gets its own table/service/controller under
`/api/projects/{projectId}/entourage`, following the `Guest`/`Task` child-resource shape rather
than the admin-managed-lookup shape (`guest_roles`/`vendor_categories`). `V22` added
`guest_roles.entourage_eligible` (default false, backfilled true for the eight seeded
wedding-party roles) — an **admin-managed** flag, not a couple-facing setting, controlling which
roles' guests appear in the Entourage card's "import from guests" picker
(`POST .../entourage/import-from-guests`, dedup by name, admin toggles it on the existing Guest
Roles page). `V24` added `guest_roles.parent_id` (nullable self-FK, `ON DELETE SET NULL`) —
one level of sub-role nesting, mirroring `Vendor.parent_id`'s package-item pattern (`V11`), used
to organize Secondary Sponsor into eight sub-roles (Candle, Veil, Cord, Ring Bearer, Arrhae
Bearer, Rosary Bearer, Bible Bearer, Flower Girls) — the last two reparent the existing
`RING_BEARER`/`FLOWER_GIRL` rows rather than duplicating them. The admin Guest Roles page's
create/edit forms ask which top-level role (if any) a new role is a sub-role of. See
`docs/attire-and-entourage.md`. See `docs/rsvp.md` for the full guest-facing feature.

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

- Unit/integration: 142 backend tests. `AbstractIntegrationTest` (full HTTP, MockMvc, own
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

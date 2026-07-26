# Admin Dashboard

Platform administration for `ROLE_ADMIN`: live stats and user management.

## API (`/api/admin/**`, class-level `@PreAuthorize("hasRole('ADMIN')")`)

- `GET /api/admin/stats` — totals (users, projects, tasks, vendors, expenses, guests) plus a
  `usersByRole` breakdown (single grouped JPQL query).
- `GET /api/admin/users` — all users with roles + enabled flag, sorted by email.
- `PUT /api/admin/users/{id}/enabled` — `{ enabled: boolean }`. Disabled accounts fail both
  login (`DisabledException` → 401) and bearer-token auth (the JWT filter checks
  `isEnabled()`). **Self-disable is rejected** (400) so an admin can't lock themselves out.

Non-admins get 403 on all of these (covered by integration tests).

## Frontend (`/admin`)

- Admin-only **Admin** link in the app header; the page itself also redirects non-admins to
  `/dashboard` (defense in depth — the API is the real gate).
- Six stat cards + role breakdown line.
- User table: name, email, role badges, Disabled badge, "You" marker; per-row Enable/Disable
  button (disabled on your own row) with toasts via `setUserEnabledAction`.

Admins also see every project on the regular dashboard (role-scoped listing) and can access any
project, its tabs, and its invitations.

## Admin sub-pages

Linked as cards from `/admin`:

- `/admin/templates` — checklist/vendor template catalog (see [templates.md](templates.md)).
- `/admin/vendor-categories` — manage the vendor-category lookup table.
- `/admin/vendor-directory` — manage the global vendor directory.
- `/admin/reports` — vendors-by-category, in-demand-vendors, and booking-conversion reports with
  CSV export.

The last three are covered in [vendor-catalog.md](vendor-catalog.md); each backend endpoint sits
under `/api/admin/**` (`hasRole('ADMIN')`), and the pages redirect non-admins to `/dashboard`.

## Key files

- `backend/.../service/AdminService.java`, `web/AdminController.java`, `dto/AdminDtos.java`
- `frontend/app/(app)/admin/page.tsx`, `components/admin/user-table.tsx`,
  `app/actions/admin.ts`
- Tests: `InvitationRsvpAdminIntegrationTest` (authz, disable-login, self-disable guard, stats)

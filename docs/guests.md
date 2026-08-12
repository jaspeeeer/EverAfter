# Guest List CRM

Invitee tracking per project: RSVP status, party sizes, contact details, dietary needs, seating
assignments, CSV import/export, and per-guest public RSVP links.

## Data model

`guests` table (Flyway `V2`, extended in `V3`): `first_name`, `last_name?`, `title?`, `gender?`
(`V17` — see below), `email?`, `phone?`, `rsvp_status` (PENDING / ATTENDING / DECLINED / MAYBE),
`party_size?` (≥1 when set, an entry can represent a household; null means "just this guest" —
treated as 1 everywhere it's summed: attending headcount, dietary rollup), `dietary_notes?`,
`table_number?` (seating), and `rsvp_token` — a unique, unguessable UUID that powers the public
RSVP link (see [rsvp.md](rsvp.md)).

**Name split (`V17`).** A guest's name was originally one free-text `name` column; `V17` splits
it into `first_name` (required), `last_name` (optional — a mononym or a joint "Alex & Jamie"
style entry has none), `title` (optional free-text honorific, "Mr.", "Dr."), and `gender`
(optional fixed enum MALE / FEMALE / OTHER). The migration backfills the split from the old
`name` on a best-effort basis (first whitespace token → `first_name`, the rest → `last_name`) —
titles embedded in the old name land in `first_name` and can be corrected per-row after
migration. `Guest.getFullName()` recomposes a single display string ("Title First Last", skipping
unset parts) for the activity log, the public RSVP page, and CSV export; the frontend has its own
`guestFullName()` helper (`components/guests/guest-list.tsx`) so search/sort/render don't all
round-trip through the backend.

**Classification (`V12`, all optional/nullable).** Four planner-internal properties, none of
which are ever exposed on the public RSVP surface (`RsvpDtos`/`PublicController` are untouched):
- `priority` — a fixed enum **A / B / C** (invite tier).
- `related_to` — a fixed enum **GROOM / BRIDE** (which side of the couple).
- `relationship` — a fixed enum: PARENT, IMMEDIATE_FAMILY, CLOSE_FRIEND, OFFICEMATE, RELATIVE,
  FAMILY_FRIEND, CHURCHMATE, COMPANION_OF_GUEST.
- `role_id` — a FK to **`guest_roles`**, an **admin-managed lookup** (same shape as
  `vendor_categories`: name/slug/active/sort_order, auto-slug on create, deactivate-if-in-use on
  delete). Seeded with a starter set (Principal Sponsor, Best Man, Maid of Honor, Officiating
  Pastor, …) admins can rename/add to/deactivate at `/admin/guest-roles`
  (`GET /api/guest-roles` public-active-list, `/api/admin/guest-roles` admin CRUD).

**Soft delete (`V18`, infrastructure only — no user-facing change yet).** `guests.deleted_at`
(nullable timestamp) plus `@SQLRestriction("deleted_at is null")` on `Guest` means every existing
read — `findByProjectId`, `findByRsvpToken`, `countByRoleId`, even `AdminService.stats()`'s plain
`count()` — transparently excludes a tombstoned row with no per-query changes.
`GuestService.delete` still hard-deletes for now; nothing sets the column yet. This landed as its
own change, proven against the full existing test suite with zero test modifications, ahead of the
undo/restore feature that will actually set it — see the migration's header comment for why a
DB-level behavior shift under every read needed to ship (and be verified) alone first.

## API

`canAccess`-gated under `…/{projectId}/guests`: list / create / full-replace PUT / delete, plus
`POST …/guests/import` — bulk create for CSV import (list of guest bodies, validated per row,
**all-or-nothing** in one transaction). `GuestRequest`/`GuestResponse` carry `firstName`,
`lastName`, `title`, `gender`, `priority`, `relatedTo`, `relationship`, `roleId` (+ `roleName` on
the response) alongside the original fields.

## Frontend (`/projects/[id]/guests`)

- **Summary stats** — Invites, Attending (headcount = Σ party sizes), Pending, Declined (each with
  a `formatPercent` share of Invites).
- **Dietary rollup** — aggregates notes across non-declined parties ("Vegetarian × 4") so
  caterers get one list.
- **Search + two filter-chip rows** — RSVP (All / Attending / Pending / Maybe / Declined) and
  Priority (All / A / B / C), combined; search also matches on role name. Sortable by name, RSVP,
  party size, table, priority, and role.
- **Rows** — RSVP badge, a **Priority** badge and **Role** badge when set, `Table N` badge,
  party/contact/relationship/dietary meta line, quick RSVP select (threads every field through
  unchanged — see Gotcha below), **copy RSVP link** button (`<origin>/rsvp/<token>`), edit modal
  (First/Last name, Title, Gender, Priority/Related to/Relationship/Role selects, all the latter
  optional — blank = "not set"/"no role"), delete.
- **CSV** — Export downloads `guest-list.csv`; Import parses client-side (`lib/csv.ts`, minimal
  RFC-4180: quotes/escapes, header detection, unknown RSVP → PENDING, unknown/blank
  title/gender/priority/related-to/relationship/role → left unset, blank party size → left unset).
  Column order:
  `firstName,lastName,title,gender,email,phone,rsvpStatus,partySize,dietaryNotes,tableNumber,priority,relatedTo,relationship,role`
  — `role` round-trips by **display name** (matched case-insensitively against the active roles),
  not id, so exported CSVs stay human-editable.

## Gotcha

PUT replaces the whole guest — every update call site must send **all** fields (including
`lastName`/`title`/`gender`, `tableNumber`, and the four classification fields), or quick actions
like the RSVP select will null out the rest.

## Key files

- `backend/.../domain/{Guest,Gender,GuestRole,GuestPriority,RelatedTo,GuestRelationship}.java`,
  `service/{GuestService,GuestRoleService}.java`,
  `web/{GuestController,GuestRoleController,GuestRoleAdminController}.java`, migrations
  `V12`/`V17`
- `frontend/components/guests/guest-list.tsx`, `components/admin/guest-role-manager.tsx`,
  `app/actions/{guests,guest-catalog}.ts`, `lib/csv.ts`,
  `app/(app)/admin/guest-roles/page.tsx`
- Tests: `GuestMappingTest`, `GuestRoleIntegrationTest`,
  `InvitationRsvpAdminIntegrationTest` (import + RSVP), `e2e/guest-classification.spec.ts`

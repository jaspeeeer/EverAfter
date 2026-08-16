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
- **roles** — a guest may carry zero, one, or several **`guest_roles`**, an **admin-managed
  lookup** (same shape as `vendor_categories`: name/slug/active/sort_order, auto-slug on
  create, deactivate-if-in-use on delete). Seeded with a starter set (Principal Sponsor, Best
  Man, Maid of Honor, Officiating Pastor, …) admins can rename/add to/deactivate at
  `/admin/guest-roles` (`GET /api/guest-roles` public-active-list, `/api/admin/guest-roles`
  admin CRUD). Also carries an admin-managed `entourage_eligible` flag (`V22`) controlling
  which roles' guests show up in the Entourage settings card's "import from guests" picker —
  see [attire-and-entourage.md](attire-and-entourage.md).

  **Many-to-many (`V25`).** `guest_role_assignments` is a join table between `guests` and
  `guest_roles`, shaped exactly like `timeline_event_vendors`'s package/vendor link (`V5`):
  composite PK (`guest_id`, `role_id`), both FKs `ON DELETE CASCADE`, no surrogate id. Replaces
  the original single nullable `guests.role_id` FK (dropped in the same migration, after an
  `INSERT … SELECT` carries every existing assignment forward — including soft-deleted guests,
  so a later restore keeps its role). `Guest.roles` is a unidirectional `@ManyToMany`
  (`Set<GuestRole>`) with a `replaceRoles(Set<GuestRole>)` full-replace helper, mirroring
  `TimelineEvent.replaceVendors`; `GuestRoleService.resolveRoles(List<UUID>)` resolves/dedupes
  the submitted ids (400 on an unknown one), mirroring `TimelineService.resolveVendors` minus
  the tenant check (guest roles are global, not project-scoped). `GuestRoleService.delete`'s
  deactivate-if-referenced guard now queries `GuestRepository.countByRolesId` (a derived count
  straight through the `@ManyToMany` collection) instead of the old `countByRoleId`.
  `GuestResponse.roles` is a nested `GuestRoleAssignmentResponse[]` (id, name,
  entourageEligible, parentName) — no flat `roleIds`/`roleNames` echo, matching
  `TimelineEventResponse`'s "nested list only" precedent. The Guests-tab add/edit form is a
  checkbox `<fieldset>` (mirroring the timeline vendor picker), not a `<select>`; the row shows
  one badge per role; the role filter matches if *any* of a guest's roles equals the selected
  value; CSV's "role" column holds a comma-separated list of role names on export and
  case-insensitively resolves + silently drops unknown names on import. The Entourage "import
  from guests" picker groups by (guest, role) **pairs**, not by guest alone — a guest with two
  eligible roles (e.g. Groomsman *and* a Secondary Sponsor sub-role like Candle) appears once
  per group, and checking both creates two separate entourage rows for the same person;
  `EntourageService.importFromGuests` dedupes by the **(name, role)** pair rather than name
  alone, so re-submitting the same pair no-ops but the same person under a second role is
  legitimately a second row.

  **One level of sub-role nesting (`V24`).** `guest_roles.parent_id` is a nullable self-FK
  (`ON DELETE SET NULL`), mirroring `Vendor.parent_id`'s package-item pattern (`V11`) — a
  sub-role's parent must itself be top-level, enforced in `GuestRoleService.resolveParent`
  (reject self-parenting, reject two levels of nesting, reject reparenting a role that already
  has sub-roles, reject deleting a role that still has sub-roles). `V24` organizes Secondary
  Sponsor into eight sub-roles matching Filipino Catholic-wedding tradition: Candle, Veil, Cord,
  Ring Bearer, Arrhae Bearer, Rosary Bearer, Bible Bearer, and Flower Girls. The last two are
  **reparented, not duplicated** — the existing `RING_BEARER` and `FLOWER_GIRL` rows (seeded in
  `V12`) keep their UUIDs and just gain a `parent_id`; `FLOWER_GIRL` is renamed to "Flower
  Girls" while its slug stays stable. The admin Guest Roles page's create/edit forms expose a
  "Sub-role of" dropdown (top-level roles only) — this is how the app satisfies "ask if it is a
  sub-role of an existing role" for any future custom role. `GuestResponse.parentRoleName`
  denormalizes the parent's name onto each guest so the frontend can prefix a sub-role label
  (e.g. "Secondary Sponsor → Candle") without an extra fetch — in the classification dropdown,
  the per-guest role badge, and the Guests-tab role filter. The Entourage "import from guests"
  picker is intentionally **not** rolled up under the parent — each sub-role still gets its own
  group heading, unchanged from before `V24`.

  **Admin list display.** The Guest Roles admin table always renders a sub-role directly
  beneath its own parent, regardless of the chosen sort field — `guest-role-manager.tsx`'s
  `compareGuestRoles` groups rows by their top-level ancestor (looked up by id in a local
  `rolesById` map, since a sub-role's own fields only carry the parent's *name*, not its other
  fields like `active`), orders groups and same-group siblings by the selected field, and always
  keeps the parent as the first row of its own group (that relationship doesn't flip with sort
  direction — the parent is a group header, not a sortable peer of its children). This bypasses
  `useTableControls`'s own generic sort for the actual render order (the hook is only used for
  its query/sortKey/sortDir/page state and `filteredCount`); an earlier attempt encoded the
  grouping as one composite sortable string, which is fragile — `localeCompare`'s
  `{numeric: true}` mode collates embedded digits specially, and role names sharing a prefix
  (e.g. a hypothetical "Best" vs. "Best Man") sort incorrectly without a safe separator — so a
  real comparator function replaced it. The search box also matches a sub-role's `parentName`,
  so searching "Secondary Sponsor" surfaces the whole family, not just the parent row.

**Soft delete + undo (`V18`).** `guests.deleted_at` (nullable timestamp) plus
`@SQLRestriction("deleted_at is null")` on `Guest` means every existing read —
`findByProjectId`, `findByRsvpToken`, `countByRolesId`, even `AdminService.stats()`'s plain
`count()` — transparently excludes a tombstoned row with no per-query changes. `GuestService.delete`
stamps `deletedAt` instead of removing the row; `POST …/guests/{guestId}/restore` reverses it (a
native `UPDATE … WHERE deleted_at IS NOT NULL` — `@SQLRestriction` hides the row from the normal
`findById` a restore would otherwise use to load it). The frontend's delete button surfaces an
**Undo** action in the confirmation toast (`components/ui/toast.tsx`'s `action` option, ~8s
window) that calls the restore endpoint. See [undo-delete.md](undo-delete.md) for the shared
mechanics across all four soft-deletable entities.

## API

`canAccess`-gated under `…/{projectId}/guests`: list / create / full-replace PUT / delete, plus
`POST …/guests/import` — bulk create for CSV import (list of guest bodies, validated per row,
**all-or-nothing** in one transaction). `GuestRequest` carries `firstName`, `lastName`, `title`,
`gender`, `priority`, `relatedTo`, `relationship`, `roleIds` (a list — see the many-to-many note
above); `GuestResponse` carries the same fields plus `roles` (nested `GuestRoleAssignmentResponse[]`).

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

`partySize` also has an opt-in public-write path: a project can let guests set it themselves on
the public RSVP form (`project.allowGuestPartySize`, off by default) — see "Guest-controlled
party size" in [rsvp.md](rsvp.md). This is separate from the planner-side editor above, which can
always set party size regardless of the toggle.

## Key files

- `backend/.../domain/{Guest,Gender,GuestRole,GuestPriority,RelatedTo,GuestRelationship}.java`,
  `service/{GuestService,GuestRoleService,EntourageService}.java`,
  `web/{GuestController,GuestRoleController,GuestRoleAdminController,EntourageController}.java`,
  `repository/GuestRepository.java`, migrations `V12`/`V17`/`V25`
- `frontend/components/guests/guest-list.tsx`, `components/admin/guest-role-manager.tsx`,
  `components/projects/entourage-manager.tsx`,
  `app/actions/{guests,guest-catalog,entourage}.ts`, `lib/{csv,guest-role-tree}.ts`,
  `app/(app)/admin/guest-roles/page.tsx`
- Tests: `GuestMappingTest`, `GuestRoleServiceTest`, `GuestRoleIntegrationTest`,
  `EntourageServiceTest`, `InvitationRsvpAdminIntegrationTest` (import + RSVP),
  `e2e/{guest-classification,entourage}.spec.ts`

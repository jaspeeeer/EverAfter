# Guest List CRM

Invitee tracking per project: RSVP status, party sizes, contact details, dietary needs, seating
assignments, CSV import/export, and per-guest public RSVP links.

## Data model

`guests` table (Flyway `V2`, extended in `V3`): `name`, `email?`, `phone?`,
`rsvp_status` (PENDING / ATTENDING / DECLINED / MAYBE), `party_size` (≥1, an entry can represent
a household), `dietary_notes?`, `table_number?` (seating), and `rsvp_token` — a unique,
unguessable UUID that powers the public RSVP link (see [rsvp.md](rsvp.md)).

## API

`canAccess`-gated under `…/{projectId}/guests`: list / create / full-replace PUT / delete, plus
`POST …/guests/import` — bulk create for CSV import (list of guest bodies, validated per row,
**all-or-nothing** in one transaction).

## Frontend (`/projects/[id]/guests`)

- **Summary stats** — Invites, Attending (headcount = Σ party sizes), Pending, Declined.
- **Dietary rollup** — aggregates notes across non-declined parties ("Vegetarian × 4") so
  caterers get one list.
- **Search + RSVP filter chips** (All / Attending / Pending / Maybe / Declined).
- **Rows** — RSVP badge, `Table N` badge, party/contact/dietary meta, quick RSVP select,
  **copy RSVP link** button (`<origin>/rsvp/<token>`), edit modal (incl. table), delete.
- **CSV** — Export downloads `guest-list.csv`; Import parses client-side (`lib/csv.ts`, minimal
  RFC-4180: quotes/escapes, header detection, unknown RSVP → PENDING) and posts to the bulk
  endpoint. Column order: `name,email,phone,rsvpStatus,partySize,dietaryNotes,tableNumber`.

## Gotcha

PUT replaces the whole guest — every update call site must send **all** fields (including
`tableNumber`), or quick actions like the RSVP select will null out the rest.

## Key files

- `backend/.../domain/Guest.java`, `service/GuestService.java`, `web/GuestController.java`
- `frontend/components/guests/guest-list.tsx`, `app/actions/guests.ts`, `lib/csv.ts`
- Tests: `GuestMappingTest`, `InvitationRsvpAdminIntegrationTest` (import + RSVP)

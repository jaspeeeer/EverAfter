# Project Settings

The first editable settings surface for a project — previously `name`/`weddingDate`/`totalBudget`
were only settable at creation (`POST /api/projects`) with no UI to change them afterward.

## API

`PUT /api/projects/{projectId}` — full replace, `canAccess`-gated (admin, the managing planner, or
the owning couple). No new endpoint: this was already the update route, just never wired to a
frontend form. `ProjectRequest`/`ProjectResponse` now also carry:

- `venueName` (≤200 chars), `venueAddress` (≤500 chars)
- `ceremonyTime`, `receptionTime` (`LocalTime`, e.g. `"15:00:00"`)
- `allowGuestPartySize` (boolean, default `false`), `maxPartySize` (optional `@Min(1)` cap) — see
  "Guest-controlled party size" in [rsvp.md](rsvp.md)

All are nullable/defaulted — a project with none of them set behaves exactly as before `V19`.

## Frontend (`/projects/[id]/settings`)

`components/projects/project-settings-form.tsx` — one form, three cards: **Basics** (name,
wedding date, budget), **Invitation details** (venue name/address, ceremony/reception time), and
**Guest RSVP options** (the party-size toggle + cap). Submits
via `updateProjectAction` (`app/actions/projects.ts`), a full-replace PUT following the same
"every field must be sent or it nulls out" rule as the guest editor (see the Gotcha in
[guests.md](guests.md)). A "Settings" tab was added to `ProjectTabs` alongside the existing
Overview/Checklist/Timeline/Vendors/Budget/Guests/Activity tabs.

Time inputs bind as `"HH:mm"` (native `<input type="time">`); the backend's `LocalTime` JSON form
is `"HH:mm:ss"`, so the form trims to five characters when populating `defaultValue` and the
server accepts either representation.

## Why these fields live on `Project`, not a separate table

Venue/time are 1:1 with a project and have no independent lifecycle — no history, no per-guest
variation — so they're plain nullable columns on `projects` (`V19__invitation_page_metadata.sql`)
rather than a new entity. See [rsvp.md](rsvp.md) for how they surface on the public invitation
page.

## Key files

- `backend/.../domain/Project.java`, `dto/{ProjectRequest,ProjectResponse}.java`,
  `service/ProjectService.java`, migration `V19`
- `frontend/components/projects/project-settings-form.tsx`,
  `app/(app)/projects/[id]/settings/page.tsx`, `app/actions/projects.ts`
  (`updateProjectAction`), `components/projects/project-tabs.tsx`
- Tests: `InvitationRsvpAdminIntegrationTest` (venue/time round-trip + public exposure),
  `GuestServiceTest` (party-size toggle branches), `e2e/project-settings.spec.ts`,
  `e2e/party-size-toggle.spec.ts`

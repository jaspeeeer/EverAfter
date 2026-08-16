# Project Settings

The first editable settings surface for a project — previously `name`/`weddingDate`/`totalBudget`
were only settable at creation (`POST /api/projects`) with no UI to change them afterward.

## API

`PUT /api/projects/{projectId}` — full replace, `canAccess`-gated (admin, the managing planner, or
the owning couple). No new endpoint: this was already the update route, just never wired to a
frontend form. `ProjectRequest`/`ProjectResponse` now also carry:

- `ceremonyVenueName`/`ceremonyVenueAddress` (≤200/≤500 chars) — the ceremony (church) location.
- `receptionVenueName`/`receptionVenueAddress` — the reception (venue) location, a separate place
  from the ceremony (`V20` — see [rsvp.md](rsvp.md) for why these are two locations, not one).
- `ceremonyTime`, `receptionTime` (`LocalTime`, e.g. `"15:00:00"`)
- `allowGuestPartySize` (boolean, default `false`), `maxPartySize` (optional `@Min(1)` cap) — see
  "Guest-controlled party size" in [rsvp.md](rsvp.md)
- `dressCode`, `attireNotesMen`, `attireNotesWomen`, `attirePalette` (comma-separated hex colors),
  `rsvpDeadline`, `kidsPolicy`, `socialHashtag` (`V21`) — see
  [attire-and-entourage.md](attire-and-entourage.md)

All are nullable/defaulted — a project with none of them set behaves exactly as before `V19`.
Photo slots (cover/ceremony/reception) are **not** part of this payload — they're set via their
own multipart endpoints, see [project-photos.md](project-photos.md). The entourage list is also
**not** part of this payload — it's its own resource under `/api/projects/{projectId}/entourage`,
see [attire-and-entourage.md](attire-and-entourage.md).

## Frontend (`/projects/[id]/settings`)

`components/projects/project-settings-form.tsx` — one form, six cards: **Basics** (name, wedding
date, budget), **Ceremony** (venue name/address/time), **Reception** (venue name/address/time),
**Guest RSVP options** (the party-size toggle + cap), **Attire** (dress code, men's/women's
notes, a repeatable color-palette editor), and **Invitation extras** (RSVP-by date, hashtag,
kids policy — see [attire-and-entourage.md](attire-and-entourage.md)). Submits via
`updateProjectAction` (`app/actions/projects.ts`), a full-replace PUT following the same "every
field must be sent or it nulls out" rule as the guest editor (see the Gotcha in
[guests.md](guests.md)). A "Settings" tab was added to `ProjectTabs` alongside the existing
Overview/Checklist/Timeline/Vendors/Budget/Guests/Activity tabs.

A separate **Photos** card, outside the main form (it submits multipart, not JSON — see
[project-photos.md](project-photos.md)), holds the cover/ceremony/reception photo uploads. A
separate **Entourage** card, also outside the main form (its own resource with its own
endpoints), manages the wedding-party list — see
[attire-and-entourage.md](attire-and-entourage.md).

Time inputs bind as `"HH:mm"` (native `<input type="time">`); the backend's `LocalTime` JSON form
is `"HH:mm:ss"`, so the form trims to five characters when populating `defaultValue` and the
server accepts either representation.

## Why these fields live on `Project`, not a separate table

Venue/time are 1:1 with a project and have no independent lifecycle — no history, no per-guest
variation — so they're plain nullable columns on `projects`
(`V19__invitation_page_metadata.sql`, `V20__ceremony_reception_venues.sql`) rather than a new
entity. See [rsvp.md](rsvp.md) for how they surface on the public invitation page.

## Key files

- `backend/.../domain/Project.java`, `dto/{ProjectRequest,ProjectResponse}.java`,
  `service/ProjectService.java`, migrations `V19`, `V20`, `V21`
- `frontend/components/projects/{project-settings-form,entourage-manager}.tsx`,
  `app/(app)/projects/[id]/settings/page.tsx`, `app/actions/{projects,entourage}.ts`
  (`updateProjectAction`), `components/projects/project-tabs.tsx`
- Tests: `InvitationRsvpAdminIntegrationTest` (venue/time round-trip + public exposure),
  `GuestServiceTest` (party-size toggle branches), `e2e/project-settings.spec.ts`,
  `e2e/party-size-toggle.spec.ts`, `e2e/entourage.spec.ts`

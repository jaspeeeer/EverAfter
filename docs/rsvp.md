# Public RSVP Page

Guests RSVP through a shareable link — no account, no login. Each guest row has its own
unguessable `rsvp_token` (UUID, unique index, generated at insert), so a link identifies exactly
one guest and leaks nothing else.

## Flow

1. The couple/planner copies a guest's link from the Guests tab: `<origin>/rsvp/<token>`.
2. The page (`/rsvp/[token]`, public in `frontend/proxy.ts`) server-fetches
   `GET /api/public/rsvp/{token}` and renders an invitation-style card: project name, wedding
   date, "Hello, <guest>", and a form — attendance choice ("Joyfully accepts / Regretfully
   declines / Not sure yet"), party size, dietary needs.
3. Submit → `PUT /api/public/rsvp/{token}` (via `submitRsvpAction`) → thank-you state. The link
   stays live so guests can change their answer; updates appear immediately in the project's
   guest list and dietary rollup.

## Security posture

- `/api/public/**` is `permitAll`, but every route is keyed by a 122-bit-random UUID token.
- The view DTO (`RsvpViewResponse`) exposes only guest name, project name, wedding date and the
  guest's own RSVP fields — no IDs, no other guests.
- The update DTO accepts only `rsvpStatus`, `partySize` (≥1), `dietaryNotes` — a guest can never
  rename themselves, change tables, or touch anything else.
- Unknown tokens → 404 (verified in tests both at API and page level).

## Key files

- `backend/.../web/PublicController.java`, `service/GuestService.java`
  (`viewByRsvpToken` / `respondByRsvpToken`), `dto/RsvpDtos.java`
- `frontend/app/rsvp/[token]/page.tsx`, `components/rsvp/rsvp-form.tsx`,
  `app/actions/invitations.ts` (`submitRsvpAction`)
- Tests: `InvitationRsvpAdminIntegrationTest`, `e2e/invite-rsvp.spec.ts`

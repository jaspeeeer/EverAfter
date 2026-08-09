# Public RSVP Page

Guests RSVP through a shareable link — no account, no login. Each guest row has its own
unguessable `rsvp_token` (UUID, unique index, generated at insert), so a link identifies exactly
one guest and leaks nothing else.

## Flow

1. The couple/planner copies a guest's link from the Guests tab: `<origin>/rsvp/<token>`.
2. The page (`/rsvp/[token]`, public in `frontend/proxy.ts`) server-fetches
   `GET /api/public/rsvp/{token}` and renders an invitation-style card: project name, wedding
   date, "Hello, <guest>" (the guest's composed display name — see [guests.md](guests.md)), and a
   form — attendance choice ("Joyfully accepts / Regretfully declines / Not sure yet"), dietary
   needs. There's no party-size field: headcount is planner-managed (see below).
3. Submit → `PUT /api/public/rsvp/{token}` (via `submitRsvpAction`) → thank-you state. The link
   stays live so guests can change their answer; updates appear immediately in the project's
   guest list and dietary rollup.

## Security posture

- `/api/public/**` is `permitAll`, but every route is keyed by a 122-bit-random UUID token.
- The view DTO (`RsvpViewResponse`) exposes only the guest's composed display name, project name,
  wedding date and the guest's own RSVP fields — no IDs, no other guests.
- The update DTO (`RsvpUpdateRequest`) accepts only `rsvpStatus` and `dietaryNotes` — a guest can
  never rename themselves, change tables, or touch anything else. Party size is deliberately not
  updatable here: `GuestService.respondByRsvpToken` always resets it to 1 server-side on every
  public submission, regardless of what the planner had set — headcount stays planner-managed via
  the guest editor.
- Unknown tokens → 404 (verified in tests both at API and page level).

## Key files

- `backend/.../web/PublicController.java`, `service/GuestService.java`
  (`viewByRsvpToken` / `respondByRsvpToken`), `dto/RsvpDtos.java`
- `frontend/app/rsvp/[token]/page.tsx`, `components/rsvp/rsvp-form.tsx`,
  `app/actions/invitations.ts` (`submitRsvpAction`)
- Tests: `InvitationRsvpAdminIntegrationTest`, `e2e/invite-rsvp.spec.ts`

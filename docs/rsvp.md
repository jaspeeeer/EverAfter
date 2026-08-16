# Public RSVP Page (the guest's wedding invitation)

> **Not to be confused with a couple onboarding invite**
> (see [couple-onboarding-invites.md](couple-onboarding-invites.md)) — that's the planner→couple
> link that hands over project ownership. This page is what a *guest* opens to RSVP.

Guests RSVP through a shareable link — no account, no login. Each guest row has its own
unguessable `rsvp_token` (UUID, unique index, generated at insert), so a link identifies exactly
one guest and leaks nothing else.

## Flow

1. The couple/planner copies a guest's link from the Guests tab: `<origin>/rsvp/<token>`.
2. The page (`/rsvp/[token]`, public in `frontend/proxy.ts`) server-fetches
   `GET /api/public/rsvp/{token}` and renders an invitation: a cover-photo hero banner (see
   [project-photos.md](project-photos.md)), the couple's names and wedding date, an
   "Add to calendar" link and (only when set) an RSVP-by date, a kids policy, and a social
   hashtag, a **Ceremony** section and a **Reception** section (each with its own
   name/address/time/photo/embedded map — see below), an **Attire** section and an **Entourage**
   section (dress code, palette, wedding party — see
   [attire-and-entourage.md](attire-and-entourage.md)), "Hello, <guest>" (the guest's composed
   display name — see [guests.md](guests.md)), and a form — attendance choice ("Joyfully accepts
   / Regretfully declines / Not sure yet"), dietary needs, and — only when the project has opted
   in — a party size field (see below).
3. Submit → `PUT /api/public/rsvp/{token}` (via `submitRsvpAction`) → thank-you state. The link
   stays live so guests can change their answer; updates appear immediately in the project's
   guest list and dietary rollup.

## Ceremony & reception venues (`V19`, split into two in `V20`)

Real weddings almost always have two distinct locations — a ceremony (church) and a reception
(venue) — so `projects` carries them as two independent pairs: `ceremony_venue_name`/
`ceremony_venue_address` and `reception_venue_name`/`reception_venue_address` (`V20`, both
nullable), alongside `ceremony_time`/`reception_time` (`V19` — already correctly per-event, no
rename needed when the venue split landed). Planners/admins/the owning couple edit all six
fields on the **Settings** tab (`/projects/{id}/settings`, `project-settings-form.tsx`, split
into separate **Ceremony** and **Reception** cards) — a full-replace `PUT /api/projects/{projectId}`
under the existing `canAccess` rule, no new endpoint or permission check.

`RsvpViewResponse` exposes all six fields plus `hasCeremonyPhoto`/`hasReceptionPhoto` (see
[project-photos.md](project-photos.md)). Each `VenueSection` (`components/rsvp/venue-section.tsx`)
renders nothing at all when its location has zero fields set, so a project that only configured
one venue doesn't leave an empty section on the page. The page builds an array of whichever of
its four optional sections (ceremony, reception, attire, entourage — see
[attire-and-entourage.md](attire-and-entourage.md)) have content, then renders a plain divider
(`bg-border`, no new CSS) between each adjacent pair — so any subset renders with no gaps or
dangling dividers, not just the ceremony/reception pair this started as.

**Embedded map per venue, not one combined map.** Each section with an address gets its own
`VenueMap` (`components/rsvp/venue-map.tsx`) — a real, interactive, panable/zoomable Google Map
embedded via `https://www.google.com/maps?q=<address>&output=embed`, no API key required. That
embed form only accepts **one** query, which is the actual reason for one map per venue rather
than a single map with two pins — a paid Maps Embed API key would be needed for multiple markers,
and this app deliberately has none. A directions link (`https://maps.google.com/?q=<address>`)
sits below each map, built client-side — no geocoding, no new dependency.

`V19` also added `allow_guest_party_size`, `max_party_size`, and `cover_attachment_id`; `V20`
added `ceremony_photo_attachment_id`/`reception_photo_attachment_id`. Party size is covered below;
all three photo slots are covered in [project-photos.md](project-photos.md).

## Guest-controlled party size (opt-in, `V19`)

Party size is **planner-managed by default** — the public RSVP form has no party-size field at
all, and submitting an RSVP never changes it. A planner/admin/the owning couple can flip
**"Allow guests to set their own party size"** on the Settings tab
(`project.allowGuestPartySize`, default `false`) and optionally set a cap
(`project.maxPartySize`). When the toggle is on:

- The RSVP form shows a number input (`min=1`, capped at `maxPartySize` when set), defaulting to
  the guest's current party size.
- `GuestService.respondByRsvpToken` accepts `RsvpUpdateRequest.partySize()` and applies it, 400ing
  if it exceeds `maxPartySize`.

When the toggle is off, `partySize` in the request body is **ignored outright** and the guest's
existing value is left untouched — this fixed a latent bug: the old unconditional
`guest.setPartySize(1)` on every public submission silently clobbered whatever the planner had set
each time a guest merely updated their dietary notes, regardless of whether the planner wanted
guest-controlled headcount at all. `RsvpUpdateRequest.partySize()` is validated `@Min(1)` but
otherwise optional — omitting it (as the frontend does when the field isn't rendered) leaves the
existing value alone in either toggle state.

## Add-to-calendar (`.ics`)

The invitation page shows an "Add to calendar" link whenever the project has a `weddingDate` set
(hidden otherwise — there's nothing to schedule). It downloads
`GET /api/public/rsvp/{token}/calendar.ics`, a hand-authored RFC 5545 file built by `IcsService`
from the same fields the invitation already shows — no calendar library, no new dependency.

- **`DTSTART`/`DTEND`** — `ceremonyTime`/`receptionTime` when set. Missing reception defaults to
  3 hours after the ceremony; missing both defaults to a near-full-day window (00:00–23:59).
  Times are emitted as RFC 5545 **floating** (no `Z`, no `TZID`): a ceremony is a wall-clock
  instant at the venue, so a guest opening the file from another time zone should see the same
  3:00 PM the invitation printed, not one shifted by the difference between zones.
- **`LOCATION`/`DESCRIPTION`** — the calendar event has exactly one `LOCATION` field, so the
  **ceremony** venue drives it (it's the anchor `DTSTART`/`DTEND` are derived from). When a
  reception venue is also set, it gets a one-line mention appended to `DESCRIPTION`
  ("Reception to follow at …") instead of fighting the ceremony for `LOCATION`.
- **`UID`** is `{rsvpToken}@wedding-planner` — deterministic per guest, so re-downloading the file
  updates the guest's existing calendar entry instead of creating a duplicate. This reuses the
  RSVP token that's already the page's only credential rather than exposing any new id.
- **`DESCRIPTION`** links back to `{app.frontend-base-url}/rsvp/{token}`. The backend can't
  reliably derive the guest-facing origin from the inbound request — see the frontend-proxy note
  below — so `app.frontend-base-url` (`APP_FRONTEND_BASE_URL`, default `http://localhost:3000`)
  is explicit config instead.
- No wedding date → `400 Bad Request` (`IcsService.buildInvitationIcs`), not a confusing empty
  file.

**Frontend proxy, not a direct link.** Per this codebase's architecture, the browser never calls
`:8080` directly (no CORS is configured for it) — so the RSVP page's link points at
`app/api/public/rsvp/[token]/calendar.ics/route.ts`, a Next.js route handler that streams the
backend's response server-side, exactly like the cover-photo proxy
(see [project-photos.md](project-photos.md)). This carries no auth of its own; the token in the URL
is the only credential, same as the endpoint it forwards to.

## Social preview (`generateMetadata`)

`/rsvp/[token]` exports `generateMetadata` — the first use of Next's dynamic metadata API in this
codebase — so pasting the link into Slack/iMessage/a group chat unfurls into an actual card
instead of a bare URL. It fetches the same `getPublicRsvp(token)` the page itself uses (Next
dedupes identical `fetch()` calls within one request, so this doesn't double the network cost)
and sets:

- `title`/`description` — "You're invited to {project}" / "{project} — {wedding date}".
- `openGraph.images` / `twitter.image` — only when `rsvp.hasCover` (see
  [project-photos.md](project-photos.md)); omitted entirely otherwise, so unfurlers fall back to a
  text-only card rather than a broken image.
- `twitter.card` — `summary_large_image` when there's a cover, plain `summary` otherwise.

**The image URL must be absolute** — unlike the page's own `<img>` tag, a social-media scraper
(Facebook, Twitter, Slack, iMessage) fetches the `og:image` URL directly, server-to-server, with
no browser and no relative-URL resolution. There's no `metadataBase` configured (which would only
paper over one deployment target anyway); instead the URL is built from the actual inbound
request's `Host`/`X-Forwarded-Proto` headers via `next/headers`, which — unlike the Spring
backend's equivalent problem for the `.ics` file's description line — genuinely reflects the
public-facing origin, since Next's own server is what's directly behind the deployment's edge.

An unknown/malformed token still resolves to a plain `{ title: 'Invitation' }` from
`generateMetadata` rather than throwing — Next replaces this with the `not-found` boundary's own
metadata once the page component itself calls `notFound()`, so this only guards against
`generateMetadata` crashing before that boundary ever gets a chance to render.

Manually verified: `curl -A 'facebookexternalhit/1.1' <cover-url>` returns `200` with
`Content-Type: image/jpeg` (no auth needed — see [project-photos.md](project-photos.md)), and the
rendered page's `<head>` carries the expected `og:*`/`twitter:*` tags with and without a cover set.

## Security posture

- `/api/public/**` is `permitAll`, but every route is keyed by a 122-bit-random UUID token.
- The view DTO (`RsvpViewResponse`) exposes only the guest's composed display name, project name,
  wedding date and the guest's own RSVP fields — no IDs, no other guests.
- The update DTO (`RsvpUpdateRequest`) accepts only `rsvpStatus`, `dietaryNotes`, and an optional
  `partySize` — a guest can never rename themselves, change tables, or touch anything else.
  `partySize` is only honored when the project has opted in (see "Guest-controlled party size"
  above); otherwise it's ignored and headcount stays exactly what the planner set.
- Unknown tokens → 404 (verified in tests both at API and page level). A **malformed** token (not
  a UUID at all) gets the same 404, not a different error shape — otherwise the two cases would be
  distinguishable, turning the endpoint into a token-format oracle
  (`GlobalExceptionHandler.handleTypeMismatch`).
- **Rate limited.** `RateLimitFilter` throttles `/api/public/**` per client IP
  (`app.rate-limit.public-api.*`, default 30 requests/minute) — the token keyspace itself is
  infeasible to brute-force (122-bit random UUID), but the endpoint was otherwise free
  reconnaissance and DoS amplification for anyone hammering it. `/api/auth/register` and
  `/api/auth/login` share the same filter under a tighter `app.rate-limit.auth.*` budget (default
  10/minute) — login is an account-enumeration oracle and a BCrypt CPU amplifier, register is a
  similar enumeration surface. A throttled request gets a **429** with the same RFC-7807 shape
  every other error uses (`ProblemDetails`, since a servlet filter runs outside
  `@RestControllerAdvice` and can't reuse `GlobalExceptionHandler` directly) plus a `Retry-After`
  header. See [Key files](#key-files) — `RateLimitFilterIntegrationTest` covers both buckets.
  Disabled in every test suite (`app.rate-limit.enabled=false`) except its own dedicated test,
  which needs the opposite and therefore runs in its own Spring context.

## Key files

- `backend/.../web/PublicController.java`, `service/GuestService.java`
  (`viewByRsvpToken` / `respondByRsvpToken`), `service/IcsService.java`, `dto/RsvpDtos.java`
- `backend/.../security/RateLimitFilter.java`, `config/RateLimitProperties.java`,
  `web/ProblemDetails.java` (shared RFC-7807 body builder)
- `frontend/app/rsvp/[token]/page.tsx`,
  `components/rsvp/{rsvp-form,venue-section,venue-map,attire-section,entourage-section}.tsx`,
  `app/actions/invitations.ts` (`submitRsvpAction`),
  `app/api/public/rsvp/[token]/calendar.ics/route.ts`
- Tests: `InvitationRsvpAdminIntegrationTest`, `RateLimitFilterIntegrationTest`,
  `GuestServiceTest` (party-size toggle branches), `IcsServiceTest`, `e2e/invite-rsvp.spec.ts`,
  `e2e/party-size-toggle.spec.ts`, `e2e/add-to-calendar.spec.ts`,
  `e2e/rsvp-og-metadata.spec.ts`, `e2e/project-settings.spec.ts` (two-venue-section rendering),
  `e2e/attire-entourage-rsvp.spec.ts`

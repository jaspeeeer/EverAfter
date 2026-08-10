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
  (`viewByRsvpToken` / `respondByRsvpToken`), `dto/RsvpDtos.java`
- `backend/.../security/RateLimitFilter.java`, `config/RateLimitProperties.java`,
  `web/ProblemDetails.java` (shared RFC-7807 body builder)
- `frontend/app/rsvp/[token]/page.tsx`, `components/rsvp/rsvp-form.tsx`,
  `app/actions/invitations.ts` (`submitRsvpAction`)
- Tests: `InvitationRsvpAdminIntegrationTest`, `RateLimitFilterIntegrationTest`,
  `e2e/invite-rsvp.spec.ts`

# Authentication & RBAC

JWT-based auth with three roles and strict server-side data isolation.

## Roles

| Role | Scope |
| --- | --- |
| `ROLE_ADMIN` | Every project and user on the platform. Seeded at boot (`DataInitializer`); cannot be self-registered. |
| `ROLE_PLANNER` | Only projects they manage (`projects.planner_id`). May create projects and issue couple invitations. |
| `ROLE_USER` (couple) | Only the single project they own (`projects.owner_id`). Cannot create projects or invitations. |

## Flow

1. `POST /api/auth/register` — self-service for `ROLE_PLANNER` / `ROLE_USER` only. Optional
   `inviteToken` (couples only) attaches the new account as a project's owner (see
   [invitations.md](invitations.md)). Returns a bearer token.
2. `POST /api/auth/login` — email + password (BCrypt). Disabled accounts get 401.
3. `GET /api/auth/me` — current user (id, email, names, roles).

Tokens are HS256 JWTs (JJWT): subject = email, `uid` claim = user id, 24h lifetime, issuer
`wedding-planner`. Config under `app.jwt.*` (`APP_JWT_SECRET`, `APP_JWT_EXPIRATION_MS`).

## Frontend session

The token never reaches browser JavaScript: the login/register **server actions**
(`frontend/app/actions/auth.ts`) call Spring, then store the token in an **httpOnly cookie**
(`wp_token`, SameSite=Lax, Secure in prod). All reads/mutations happen server-side with the
cookie's token attached (`lib/api.ts`, `lib/session.ts`). `frontend/proxy.ts` redirects
cookie-less requests to `/login`; `requireUser()` validates for real against `/api/auth/me`.

## Enforcement

- Route level: everything under `/api/**` requires auth except `/api/auth/register|login` and
  `/api/public/**` (SecurityConfig).
- Method level: `@PreAuthorize` with the `@projectSecurity` bean (`canAccess` / `canManage`)
  plus `hasRole('ADMIN')` on admin endpoints — see `backend/.../security/ProjectSecurity.java`.
- UI level (convenience only): role-aware dashboard headings, hidden create/invite actions for
  couples, admin-only nav link.

## Key files

- `backend/.../security/` — `JwtService`, `JwtAuthenticationFilter`, `AppUserPrincipal`,
  `AppUserDetailsService`, `ProjectSecurity`
- `backend/.../config/SecurityConfig.java`, `DataInitializer.java`
- `frontend/lib/session.ts`, `frontend/proxy.ts`, `frontend/app/actions/auth.ts`
- Tests: `SecurityIntegrationTest`, `ProjectSecurityTest`, `JwtServiceTest`, `e2e/auth.spec.ts`,
  `e2e/access-control.spec.ts`

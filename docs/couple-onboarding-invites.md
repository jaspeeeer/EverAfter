# Couple Onboarding Invites

> **Not to be confused with a guest's wedding invitation** (`/rsvp/[token]`, see
> [rsvp.md](rsvp.md)) — that's a public, no-login page each guest opens with their own
> `rsvp_token` to RSVP; it has no entity of its own. This page is about the *other* "invitation" in
> this codebase: the one-time link a planner sends the couple so they can create an account and
> take ownership of their own project.

Lets a planner hand a project to the couple who'll own it: issue a token link → the couple
registers through it → their new account is atomically attached as the project owner.

## Flow

1. **Issue** — on the project Overview (visible to planner/admin while the project has no
   owner), enter the couple's email → `POST /api/projects/{id}/invitations` (**`canManage`**
   gate: invitations carry the secret registration token, so couples/outsiders can't see them).
2. **Share** — the card lists invites with status and a **Copy onboarding link** button:
   `<origin>/register?invite=<token>`. There is no SMTP integration; delivery is manual by
   design (a mail provider would be a follow-up).
3. **Accept** — the register page fetches `GET /api/public/invitations/{token}` (public,
   pending-only) to show "You're invited!", prefill the email, and lock the role to couple
   (hidden `role=ROLE_USER` + `inviteToken`). On submit, `AuthService.register` consumes the
   invitation **in the same transaction**: sets `projects.owner`, marks the invite `ACCEPTED`.
   A used/duplicate token rolls the whole registration back (400).

## Rules

- Single-use: accepting flips `status` to `ACCEPTED`; reuse is rejected.
- One owner: creating or accepting against a project that already has an owner is rejected.
- Invited registrations must be couple accounts (`ROLE_USER`).
- Unknown/used tokens on the register page degrade gracefully to a normal registration form.

## Data

`invitations` table (Flyway `V3`): `email`, unique `token` (UUID), `status`
(PENDING/ACCEPTED), `project_id`, `created_at`, `accepted_at`.

## Key files

- `backend/.../domain/Invitation.java`, `service/InvitationService.java`,
  `web/InvitationController.java`, `web/PublicController.java`, `service/AuthService.java`
- `frontend/components/projects/invite-couple-card.tsx`,
  `app/(auth)/register/page.tsx`, `components/auth/register-form.tsx`,
  `app/actions/invitations.ts`
- Tests: `InvitationRsvpAdminIntegrationTest`, `e2e/invite-rsvp.spec.ts`

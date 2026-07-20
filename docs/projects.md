# Projects & Dashboard

A project is the aggregate root of one wedding: it owns tasks, vendors, expenses, guests and
invitations (all cascade on delete), has a managing planner (required) and optionally an owning
couple.

## API

| Endpoint | Access |
| --- | --- |
| `GET /api/projects` | Role-scoped list: admin = all, planner = theirs, couple = their one |
| `POST /api/projects` | `ADMIN` or `PLANNER`. A planner always becomes the managing planner; an admin must pass `plannerId`. Optional `ownerEmail` links an existing couple account. |
| `GET /api/projects/{id}` | `canAccess` (admin / managing planner / owning couple) |
| `PUT /api/projects/{id}` | `canAccess` |
| `DELETE /api/projects/{id}` | `canManage` (admin / managing planner — couples cannot delete) |
| `GET /api/projects/{id}/budget` | `canAccess` — see [budget.md](budget.md) |

Fields: `name`, `weddingDate`, `totalBudget`, `plannerId/-Email`, `ownerId/-Email`, timestamps.

## Frontend

- `/dashboard` — role-aware: heading and empty states differ per role; only planners see the
  **New project** modal button. Admins see every project with the planner's email on each card.
- `/projects/[id]` — tabbed layout (Overview / Checklist / Vendors / Budget / Guests) with a
  wedding-date countdown badge. The layout maps 403/404 from the API to Next's `notFound()` so
  foreign projects don't leak existence.
- Overview tab — budget roll-up card, checklist/vendors/guests summary cards, and (for
  planners/admins while the project has no owner) the **Invite the couple** card.

## Key files

- `backend/.../domain/Project.java`, `service/ProjectService.java`, `web/ProjectController.java`
- `frontend/app/(app)/dashboard/page.tsx`, `app/(app)/projects/[id]/{layout,page}.tsx`,
  `components/projects/*`
- Tests: `ProjectServiceTest`, `SecurityIntegrationTest`, `e2e/access-control.spec.ts`

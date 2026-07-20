# Templates (Checklist & Vendor)

Admin-managed presets that planners apply to a project: a checklist template bulk-creates tasks
("Book venue", "Book photographer", …), a vendor template bulk-creates unbooked supplier slots.

## Roles

| Action | Who |
| --- | --- |
| Create / edit / delete templates | **Admin only** |
| Browse templates | Admin + planner |
| Apply a template to a project | Admin + planner, and only on projects they can access. Couples never see or reach the apply surface. |

## Data model (Flyway `V4`)

- `checklist_templates` + `checklist_template_items` — item: `title`, optional `description`,
  optional `days_before_wedding`, `sort_order` (assigned by the parent's `addItem`, read back via
  `@OrderBy`).
- `vendor_templates` + `vendor_template_items` — item: `name`, `category` (reuses
  `VendorCategory`).

`DataInitializer` seeds **"Classic Wedding Checklist"** (12 tasks with day offsets) and
**"Essential Vendors"** (6 slots) only while the catalog is empty, so admin edits/deletions stick.

## API

- `/api/templates/checklist` and `/api/templates/vendors` — `GET` (planner/admin);
  `POST`, `PUT /{id}`, `DELETE /{id}` (admin). PUT replaces the whole item list.
- `POST /api/projects/{id}/tasks/apply-template` and `…/vendors/apply-template` —
  body `{ templateId }`; gate: `hasAnyRole('ADMIN','PLANNER') and @projectSecurity.canAccess(...)`.
  Returns 201 + the created tasks/vendors (all-or-nothing transaction).

**Due-date math:** applying a checklist computes `dueDate = weddingDate − daysBeforeWedding` when
both are present; items without an offset (or projects without a date) get no due date.

## Frontend

- **Admin** — `/admin/templates` (linked from `/admin`): two sections with template cards
  (item counts + previews) and editor modals whose item rows are dynamic React state serialized
  to a hidden JSON field (`components/admin/template-manager.tsx`,
  `app/actions/templates.ts`).
- **Planner** — "Use template" on the Checklist tab and "Add from template" on the Vendors tab
  open a picker modal (name, description, item preview) → confirm → toast "Added N tasks".
  Shared picker: `components/templates/apply-template-modal.tsx`. Buttons are hidden for
  couples (`canApplyTemplates` from the server component; the API enforces regardless).

## Key files

- `backend/.../domain/{Checklist,Vendor}Template*.java`, `service/TemplateService.java`,
  `web/TemplateController.java`, apply endpoints in `TaskController` / `VendorController`,
  `config/DataInitializer.java`
- Tests: `TemplateIntegrationTest` (authz, PUT-replace, due-date math, cross-tenant/couple 403s),
  `e2e/templates.spec.ts`

# Checklist (Kanban Board)

Per-project to-do board with three columns — **To do / In progress / Done** — and real
drag-and-drop.

## API

Nested under the project; all endpoints `canAccess`-gated on `{projectId}`:

- `GET /api/projects/{projectId}/tasks`
- `POST …/tasks` — `{ title, description?, status, dueDate? }`
- `PUT …/tasks/{taskId}` — full replace
- `DELETE …/tasks/{taskId}`

`TaskService` verifies the task belongs to the path's project, so cross-project task IDs 404.
Statuses: `TODO`, `IN_PROGRESS`, `DONE` (enum `TaskStatus`).

## Frontend (`/projects/[id]/checklist`)

`components/checklist/checklist-board.tsx`:

- **Drag & drop** via `@dnd-kit/core`: each card has a grip handle (`useDraggable`), each column
  is a drop target (`useDroppable`, highlighted ring when hovered). A `PointerSensor` with a 5px
  activation distance keeps normal clicks (delete) working.
- **Optimistic moves** — a local status override applies instantly on drop; the server action
  (`updateTaskAction`) confirms with a "Task moved" toast or rolls the card back with an error
  toast.
- **Overdue highlighting** — non-done tasks past `dueDate` get a red border and an
  "Overdue · <date>" label (`isPastDue` in `lib/format.ts`).
- Add-task modal (title, notes, column, due date) with toast on success.

## E2E note

Playwright can't use dnd-kit with a plain `dragTo`; use the mouse-level `dragTo()` helper in
`frontend/e2e/helpers.ts` (move → down → stepped move → up). The planning-flow spec drags a card
to Done and verifies persistence through the API.

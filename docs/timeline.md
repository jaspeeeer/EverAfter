# Wedding-Day Timeline

The run sheet for the wedding day itself: events mapped to time slots — from the makeup call to
the after-party — rendered as a **Google Calendar-style vertical day grid**. Clicking a slot
shows the event's details and the **suppliers (vendors) involved**.

## Roles

| Action | Who |
| --- | --- |
| View the timeline | Everyone with project access (the couple follows the schedule) |
| Add / edit / delete / drag events, quick-start | Planner + admin only (`hasAnyRole('ADMIN','PLANNER') and canAccess`) |

## Data model (Flyway `V5`)

- `timeline_events` — `title`, `description?`, `location?`, `start_time` (time, required),
  `end_time?`, `project_id`.
- `timeline_event_vendors` — event↔vendor join with **`ON DELETE CASCADE` on both sides**, so
  deleting a vendor silently detaches it from events (verified by test) and deleting an event
  never strands rows.

## API (`/api/projects/{projectId}/timeline`)

- `GET` — events sorted for the day, each with its supplier summaries (name, category, booked,
  contact) via a fetch-join.
- `POST` / `PUT /{eventId}` / `DELETE /{eventId}` — body includes `vendorIds`; **every id must
  belong to the same project** or the request is rejected (400) — supplier links can't cross
  tenants. PUT replaces all fields including the vendor set.
- `POST /typical-day` — quick-start: seeds 9 preset events (Hair & makeup call 06:00 → After-party
  22:00). Only valid while the timeline is empty (400 otherwise).

**Sorting — the early-morning wrap:** times before **04:00** are treated as "after midnight" and
sort to the end of the day (`TimelineService.wrappedMinutes`), so a 01:00 after-party follows the
23:00 party instead of preceding the 06:00 makeup call. The frontend grid uses the same rule to
position blocks past midnight.

## Frontend (`/projects/[id]/timeline`)

`components/timeline/timeline-view.tsx`:

- **Day grid** — hour gutter + hour/half-hour lines at a fixed `PX_PER_MIN = 1` scale (60px/hour);
  the range stretches to fit the events (defaults 6 AM–11 PM, extends past midnight).
- **Event blocks** — kanban-style cards positioned by `start_time` and sized by duration
  (60-minute visual default when no end time); overlapping events split into side-by-side
  columns (`layoutDay`, greedy cluster/column assignment).
- **Click a slot** — detail modal with time range, location, notes, and the supplier rows
  (category badge, Booked badge, contact); Edit/Delete in the footer for planners.
- **Drag to reschedule** — planners drag a block vertically (`@dnd-kit`, 5px activation);
  the drop snaps to **15-minute** increments, applies optimistically, PUTs the shifted times,
  and rolls back with a toast on failure. Couples get static blocks.
- **Quick-start** — empty state offers "Add a typical day" (planner/admin only).

## Key files

- `backend/.../domain/TimelineEvent.java`, `service/TimelineService.java`,
  `web/TimelineController.java`, `dto/TimelineDtos.java`
- `frontend/components/timeline/timeline-view.tsx`, `app/actions/timeline.ts`,
  `app/(app)/projects/[id]/timeline/page.tsx`, `formatTime` in `lib/format.ts`
- Tests: `TimelineIntegrationTest` (8 tests), `e2e/timeline.spec.ts` (3 tests incl. a real drag)

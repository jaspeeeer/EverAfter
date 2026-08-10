# Project Search

A single search box in the project header — next to the tabs — that searches guests, vendors,
tasks, and expenses **within the current wedding** in one go, instead of hunting tab by tab.
Reached via the search icon at the right edge of `ProjectTabs`.

## Why project-scoped, not per-keystroke

Every list in this app already receives its full data as a prop and filters client-side (see
`lib/use-table-controls.ts`) — no list anywhere pages or searches server-side, and a wedding's
dataset (guests + vendors + tasks + expenses) is small. Global search follows the same
architecture rather than introducing a new one: `ProjectSearch` fetches one flat index **once**,
the first time the box opens, and filters it in memory from then on. That sidesteps debouncing, a
stale-response race, and `AbortController` bookkeeping entirely — none of which have any
precedent elsewhere in the frontend to copy.

## API

`GET /api/projects/{projectId}/search-index` (Next route handler, not a Spring endpoint) —
`Promise.all`s the four existing `lib/data.ts` getters (`getGuests`, `getVendors`, `getTasks`,
`getExpenses`) and flattens them to `{ type, id, label, sublabel, href }[]`. No new
`@PreAuthorize` surface: each getter already calls a `canAccess`-gated backend endpoint, so a user
without project access gets the same 403 here they'd get on any other tab — the first rejected
call in the `Promise.all` is what the route's `catch` maps to a status code.

`label`/`sublabel` mirror the searchable-field selectors the per-tab searches already define, so
global and per-tab search agree on what "matches":
- guest → `guestFullName(g)` / role name or email
- vendor → name / category
- task → title / description (the Kanban board has no search of its own today, so this is the
  first way a task becomes findable by name)
- expense → description / category

`rsvpToken` is deliberately left out of the index — no reason to widen its exposure beyond what
each guest row already needs.

## Frontend

`components/search/project-search.tsx` — structurally modeled on `NotificationBell` (a `relative`
root, `absolute` panel, outside-click via a `document` `mousedown` listener) but not a copy: the
bell has no keyboard handling at all, and a search box needs it, so Escape-to-close and
↑/↓/Enter were written fresh. Results are grouped by type (icons match `activity-feed.tsx`'s
per-entity icon choices for consistency) and capped at 5 per group with a "+N more — refine your
search" line rather than dumping a long flat list.

**Every dismissal path resets the query** — outside click, Escape, and clicking a result all call
the same `close()`, so reopening the box never surprises you with a stale search from last time.
(An earlier draft only did this for Escape/selection; outside-click just called `setOpen(false)`,
so clicking away and reopening silently appended new keystrokes onto the old query. Caught via
manual browser testing before this shipped — worth remembering if this component grows more
dismissal paths.)

Mounted in `app/(app)/projects/[id]/layout.tsx`, in a flex row alongside `<ProjectTabs>` — moved
the tab row's `border-b` off `ProjectTabs` itself and onto that wrapping row so the tabs and the
search icon share one continuous bottom border.

## Key files

- `frontend/app/api/projects/[projectId]/search-index/route.ts`
- `frontend/components/search/project-search.tsx`
- `frontend/components/projects/project-tabs.tsx`, `app/(app)/projects/[id]/layout.tsx`
- Tests: `e2e/project-search.spec.ts`

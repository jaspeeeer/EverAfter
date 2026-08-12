# Project Cover Photo

A single banner image per project, shown at the top of the public invitation page
(`/rsvp/[token]`). Reuses the existing attachment infrastructure rather than a bespoke upload
path.

## Data model

`projects.cover_attachment_id` (`V19`, nullable, FK → `attachments(id)` `ON DELETE SET NULL`)
enforces strict singularity — a project has **at most one** cover, not a history of them. The
attachment itself is a normal row in `attachments` with a new
[`AttachmentOwnerType.PROJECT`](attachments.md) — the first owner type where `ownerId` always
equals the project's own id (`AttachmentService.requireOwnerInProject`'s `PROJECT` case is a
defence-in-depth equality check, not a real lookup, since the project is already loaded by the
caller).

## API

- `POST /api/projects/{projectId}/cover` — multipart `file`, `canAccess`-gated (admin, managing
  planner, or owning couple — same as every other project write). `ProjectService.setCover`
  uploads via `AttachmentService.upload(PROJECT, projectId, ...)`, points
  `project.coverAttachmentId` at the new row, and — if a cover already existed — **hard-deletes**
  the old attachment (file + row) once the new one is in place. There is never an orphaned file
  and never two "current" covers.
- `DELETE /api/projects/{projectId}/cover` — clears the FK and hard-deletes the attachment; 404s
  if the project has no cover (`ProjectService.removeCover`).
- `ProjectResponse.coverAttachmentId` — the planner-facing id, used to drive the settings UI's
  Upload/Replace/Remove state.
- `RsvpViewResponse.hasCover` (boolean only — **not** the attachment id, to avoid widening the
  public DTO's exposure) drives whether the invitation page renders a banner at all.
- `GET /api/public/rsvp/{token}/cover` — streams the bytes for the invitation page. Resolved via
  `GuestService.projectIdByRsvpToken(token)` → `AttachmentService.downloadProjectCover(projectId)`,
  so the public surface never has to know (or expose) the attachment's own id — the token is the
  only credential, consistent with every other `/api/public/**` route (see
  [rsvp.md](rsvp.md)). 404s when the project has no cover. Rate-limited by the same
  `RateLimitFilter` as the rest of `/api/public/**`.

## Frontend

- **Settings tab** (`components/projects/project-cover-upload.tsx`) — its own `<form>`, separate
  from the main settings form (`project-settings-form.tsx`) because it submits multipart, not the
  settings form's JSON PUT, and a `<form>` cannot nest inside another `<form>` in HTML. Rendered
  as its own Card below the main settings form rather than inside it. Upload immediately submits
  on file selection (matching `attachment-list.tsx`'s pattern); a Remove button appears only once
  a cover exists.
- **Public invitation page** (`app/rsvp/[token]/page.tsx`) — when `rsvp.hasCover`, renders a
  banner `<img>` pointed at a **frontend proxy route**,
  `app/api/public/rsvp/[token]/cover/route.ts`, which streams the bytes from the Spring backend
  server-side. This is not optional plumbing: per this codebase's architecture "the browser never
  calls :8080 directly" (no CORS is configured for it), so a raw `<img src>` pointed straight at
  the backend would simply fail to load in production. The existing authenticated attachment
  proxy (`app/api/projects/[projectId]/attachments/[id]/route.ts`) is the precedent; this one
  carries no auth token since the route it forwards to is itself public.
- `proxy.ts`'s `PUBLIC_PATHS` gained `/api/public` — without it, an unauthenticated guest's
  browser would get redirected to `/login` when it tried to load the cover image, since the proxy
  only special-cased `/rsvp` (the page route) and had no entry at all for API routes under
  `/api/public/**`. This was caught before it shipped, not after — worth remembering for any
  future public API route added under a path outside the existing public prefixes.
- Plain `<img>`, matching the rest of the app (`next/image` is unused everywhere else) — and here
  it wouldn't even hit the auth-cookie limitation that ruled it out for attachments, since this
  page is genuinely public.

## Key files

- `backend/.../domain/AttachmentOwnerType.java` (new `PROJECT` value),
  `service/{ProjectService,AttachmentService,GuestService}.java` (`setCover`/`removeCover`,
  `downloadProjectCover`, `projectIdByRsvpToken`), `web/{ProjectController,PublicController}.java`,
  `dto/{ProjectResponse,RsvpDtos}.java`, migration `V19`
- `frontend/components/projects/{project-settings-form,project-cover-upload}.tsx`,
  `app/actions/projects.ts` (`setProjectCoverAction`/`removeProjectCoverAction`),
  `app/api/public/rsvp/[token]/cover/route.ts`, `app/rsvp/[token]/page.tsx`, `proxy.ts`
- Tests: `ProjectServiceTest` (replace/remove branches), `ProjectCoverIntegrationTest` (full HTTP
  round trip, tenant isolation, 404s), `e2e/project-cover.spec.ts`

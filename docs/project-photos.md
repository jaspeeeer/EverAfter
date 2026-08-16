# Project Photos

Five independent, single-photo slots per project — **cover** (a hero banner), **ceremony**,
**reception**, and the two attire reference photos (**men's attire**, **women's attire**) —
shown on the public invitation page (`/rsvp/[token]`). All five reuse the same attachment
infrastructure rather than five bespoke upload paths.

## Data model

`projects.cover_attachment_id` (`V19`), `ceremony_photo_attachment_id`,
`reception_photo_attachment_id` (both `V20`), `attire_men_photo_attachment_id` and
`attire_women_photo_attachment_id` (both `V23`) — each a nullable FK → `attachments(id)`
`ON DELETE SET NULL`. Each slot holds at most one photo, not a history: uploading a new one
hard-deletes the prior one (file + row) once the new one is in place, so there's never an
orphan and never two "current" photos for the same slot.

All five slots share **one** `AttachmentOwnerType.PROJECT` — the owner type doesn't distinguish
them, the FK column does. `ownerId` is always the project's own id for all five
(`AttachmentService.requireOwnerInProject`'s `PROJECT` case is a defence-in-depth equality check,
not a real lookup, since the project is already loaded by the caller). See
[attachments.md](attachments.md) for why this is the one owner type that works this way.

**Why this needed a fix, not just per-slot copies of the cover code.** Before ceremony/reception
existed, `requireOwnerInProject`'s generic `PROJECT` case hardcoded the activity-log label to
"the project cover photo" — harmless when cover was the only PROJECT-owned upload. Adding more
slots through the same generic path would have made every ceremony/reception/attire upload log
itself as a cover-photo update, since `ownerId == projectId` in all five cases and the generic
lookup has no way to tell them apart. Fixed by giving `AttachmentService.upload(...)` an
explicit `ownerLabelOverride` parameter — `ProjectService`'s photo setters always pass one
("the ceremony photo", etc.); the plain 5-arg `upload()` used by `AttachmentController`
(vendors/payments/expenses) is unaffected and still falls back to the generic lookup.

**`ProjectService`'s dedup.** A private `PhotoSlot` enum (`COVER`, `CEREMONY`, `RECEPTION`,
`ATTIRE_MEN`, `ATTIRE_WOMEN`, each carrying its own label string) plus shared
`getPhotoId`/`setPhotoId` (switch to the right `Project` getter/setter) and
`setPhoto`/`removePhoto` helpers do the actual upload/replace/404 logic once; the ten public
methods (`setCover`/`removeCover`/`setCeremonyPhoto`/… through `setAttireWomenPhoto`/
`removeAttireWomenPhoto`) are each a one-line call into the shared helpers. Same pattern on the
read side: `AttachmentService.downloadPhoto` (private) is wrapped by `downloadProjectCover`/
`downloadCeremonyPhoto`/`downloadReceptionPhoto`/`downloadAttireMenPhoto`/
`downloadAttireWomenPhoto`, each resolving its own FK column.

## API

Per slot, mirroring the original cover endpoints exactly:

| Slot | Set/replace | Remove | Public stream |
|---|---|---|---|
| Cover | `POST /api/projects/{id}/cover` | `DELETE …/cover` | `GET /api/public/rsvp/{token}/cover` |
| Ceremony | `POST …/ceremony-photo` | `DELETE …/ceremony-photo` | `GET …/rsvp/{token}/ceremony-photo` |
| Reception | `POST …/reception-photo` | `DELETE …/reception-photo` | `GET …/rsvp/{token}/reception-photo` |
| Attire — men | `POST …/attire-men-photo` | `DELETE …/attire-men-photo` | `GET …/rsvp/{token}/attire-men-photo` |
| Attire — women | `POST …/attire-women-photo` | `DELETE …/attire-women-photo` | `GET …/rsvp/{token}/attire-women-photo` |

Authenticated endpoints are multipart (`file` part), `canAccess`-gated (admin, managing planner,
or owning couple — same as every other project write). The public stream endpoints resolve
`projectId` from the guest's own RSVP token via `GuestService.projectIdByRsvpToken`, so the public
surface never has to know (or expose) the attachment's own id — consistent with every other
`/api/public/**` route (see [rsvp.md](rsvp.md)). Each 404s when that slot is empty, and is
rate-limited by the same `RateLimitFilter` as the rest of `/api/public/**`.

`ProjectResponse` carries `coverAttachmentId`/`ceremonyPhotoAttachmentId`/
`receptionPhotoAttachmentId`/`attireMenPhotoAttachmentId`/`attireWomenPhotoAttachmentId`
(planner-facing, drives the settings UI's Upload/Replace/Remove state). `RsvpViewResponse`
carries only booleans — `hasCover`/`hasCeremonyPhoto`/`hasReceptionPhoto`/`hasAttireMenPhoto`/
`hasAttireWomenPhoto` — never the attachment ids, to avoid widening the public DTO's exposure.

## Frontend

**Settings tab** (`components/projects/project-photo-upload.tsx`) — one generalized
`ProjectPhotoUpload` component, given `projectId`/`slot`/`label`/`hasPhoto` props and rendered
five times (cover, ceremony, reception, attire-men, attire-women) in the Settings page's
**Photos** card. Each instance is
its own `<form>`, separate from the main settings form (`project-settings-form.tsx`) because it
submits multipart, not the settings form's JSON PUT, and a `<form>` cannot nest inside another
`<form>` in HTML. Upload immediately submits on file selection (matching
`attachment-list.tsx`'s pattern); a Remove button appears only once that slot has a photo. Each
button carries an explicit `aria-label` (e.g. "Upload ceremony photo") since the three instances
would otherwise all expose the identical accessible name "Upload photo".

**Public invitation page** (`app/rsvp/[token]/page.tsx`, `components/rsvp/venue-section.tsx`,
`components/rsvp/attire-section.tsx`) — the cover renders as a full-width hero banner at the
top of the page; the ceremony/reception photos render inside their respective `VenueSection`,
alongside that venue's name/address/time/map; the attire men/women photos render inside
`AttireSection` next to each set of notes (see [rsvp.md](rsvp.md) for the full section
layout). All five point at a **frontend proxy route**
(`app/api/public/rsvp/[token]/{cover,ceremony-photo,reception-photo,attire-men-photo,attire-women-photo}/route.ts`),
which streams the bytes from the Spring backend server-side. This is not optional plumbing: per this
codebase's architecture "the browser never calls :8080 directly" (no CORS is configured for it),
so a raw `<img src>` pointed straight at the backend would simply fail to load in production. The
existing authenticated attachment proxy
(`app/api/projects/[projectId]/attachments/[id]/route.ts`) is the precedent; these carry no auth
token since the routes they forward to are themselves public.

`proxy.ts`'s `PUBLIC_PATHS` covers the whole `/api/public` prefix (added when the cover photo
shipped) — without it, an unauthenticated guest's browser would get redirected to `/login` trying
to load any of these images, since the proxy only special-cased `/rsvp` (the page route) and had
no entry for API routes under `/api/public/**`.

Plain `<img>` for all three, matching the rest of the app (`next/image` is unused everywhere
else) — and here it wouldn't even hit the auth-cookie limitation that ruled it out for
attachments, since this page is genuinely public.

## Key files

- `backend/.../domain/{Project,AttachmentOwnerType}.java`,
  `service/{ProjectService,AttachmentService,GuestService}.java` (`{set,remove}{Cover,
  CeremonyPhoto,ReceptionPhoto}`, `download{ProjectCover,CeremonyPhoto,ReceptionPhoto}`,
  `projectIdByRsvpToken`), `web/{ProjectController,PublicController}.java`,
  `dto/{ProjectRequest,ProjectResponse,RsvpDtos}.java`, migrations `V19`, `V20`
- `frontend/components/projects/{project-settings-form,project-photo-upload}.tsx`,
  `components/rsvp/{venue-section,venue-map}.tsx`, `app/actions/projects.ts`
  (`setProjectPhotoAction`/`removeProjectPhotoAction`),
  `app/api/public/rsvp/[token]/{cover,ceremony-photo,reception-photo}/route.ts`,
  `app/rsvp/[token]/page.tsx`, `proxy.ts`
- Tests: `ProjectServiceTest` (all three slots' set/replace/remove + activity-log-label
  assertions), `AttachmentServiceTest` (`ownerLabelOverride`), `ProjectCoverIntegrationTest`,
  `ProjectVenuePhotosIntegrationTest` (ceremony/reception full HTTP round trip, tenant isolation,
  404s), `e2e/project-cover.spec.ts`, `e2e/venue-photos.spec.ts`

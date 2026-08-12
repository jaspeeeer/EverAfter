# Attachments

Files (contracts, receipts, quotes) hung off a vendor, a vendor payment, or an expense — plus a
project's own cover photo (see below).

## Data model

`attachments` (Flyway `V16`): polymorphic owner reference — `(owner_type, owner_id)` — because a
single SQL column can't FK to four tables (`VENDOR` / `VENDOR_PAYMENT` / `EXPENSE` / `PROJECT`,
see `AttachmentOwnerType`). `project_id` is denormalized alongside the owner reference so RBAC
stays a cheap indexed lookup and `ON DELETE CASCADE` from `projects` sweeps orphan rows on project
deletion. Owner-side cleanup — deleting a vendor, payment, or expense must not leave orphaned
attachment rows/files behind — is enforced application-side (there's no FK to enforce it):
`VendorService.delete` clears a vendor's own attachments *and* every payment's attachments before
deleting the vendor (the DB's `ON DELETE CASCADE` on `vendor_payments` would otherwise delete the
payment rows without touching their attachments); `VendorService.deletePayment` and
`ExpenseService.delete` each clear their own single owner's attachments. All three call
`AttachmentService.deleteAllFor`.

**`PROJECT` is the odd one out (`V19`).** Every other owner type can have any number of
attachments; a project has **at most one** (its cover photo), tracked via a dedicated
`projects.cover_attachment_id` FK rather than the generic list query — `ProjectService.setCover`
hard-deletes the prior cover once a new one lands, so there's never more than one live row for a
given project. See [project-cover.md](project-cover.md) for the full feature.

Stored bytes live outside Postgres: `AttachmentStorage` (filesystem impl:
`FilesystemAttachmentStorage`) writes under `app.attachments.storage-root`
(`APP_ATTACHMENTS_STORAGE_ROOT`, defaults to a temp-dir subfolder for local/dev). Storage keys are
always `<projectId>/<UUID>` — assigned server-side, never derived from the uploaded filename — so
there's no path-traversal surface; `resolve()` double-checks the resolved path still starts with
the configured root before any read/write/delete.

## Validation

- **Allowed types:** `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `application/pdf` only
  — checked against the browser-supplied `Content-Type`, not the filename extension. An
  unsupported type is a **415**.
- **Size:** `app.attachments.max-file-bytes` (`APP_ATTACHMENTS_MAX_FILE_BYTES`, default 10 MB) is
  the app-level limit, checked in `AttachmentService.validate` with a friendly message. Spring's
  own `spring.servlet.multipart.max-file-size` (12 MB) is a little higher — pure headroom so a
  request that would fail the app check still reaches `AttachmentService` instead of being
  rejected at the servlet layer with a generic error.
- **Filename:** `sanitizeFilename` strips any path component a client might send and caps the
  length at 200 chars; the original name is otherwise preserved for display and download.

## API

`canAccess`-gated (admin, managing planner, or the owning couple — the same rule as the owner
resource itself) under `/api/projects/{projectId}`:
- `POST /vendors/{vendorId}/attachments`, `POST /vendor-payments/{paymentId}/attachments`,
  `POST /expenses/{expenseId}/attachments` — multipart upload (`file` part). Every write
  re-verifies the owner entity actually belongs to `projectId` (`requireOwnerInProject`) — the
  same defence-in-depth pattern as `VendorService`/`ExpenseService` — so an attachment can't be
  uploaded against an id borrowed from another tenant's project.
- `GET /attachments?ownerType=&ownerId=` — list for one owner, or the whole project when the
  query params are omitted.
- `GET /attachments/{attachmentId}/download` — streams the file with a `Content-Disposition:
  attachment` header (original filename, UTF-8).
- `DELETE /attachments/{attachmentId}` — removes the DB row and the stored file.

A couple may upload and remove their own paperwork just like the planner — there's no
manage-only restriction here, unlike project delete/invitations. Every write is captured in the
activity log (`ActivityEntityType.ATTACHMENT`) except `deleteAllFor` (cascade cleanup), since the
owner's own delete already logs a summary line.

## Frontend

`components/attachments/attachment-list.tsx` — a self-contained "Files" panel that takes
`{ projectId, ownerType, ownerId }` and loads on demand (the parent only mounts it once an owner
id exists — e.g. a vendor must be created before it can hold attachments). Client-side validates
type/size before submitting so obviously-bad uploads never hit the network.

**Image preview.** An image attachment (`contentType` starting `image/`) renders as a button that
opens `components/attachments/image-lightbox.tsx` instead of the plain download `<a>` a PDF gets.
The download proxy route (`app/api/projects/[projectId]/attachments/[id]/route.ts`) works unchanged
as an `<img src>` — browsers ignore `Content-Disposition` for subresource requests, only for
top-level navigation, so the same authenticated URL that forces a PDF download renders an image
inline. **Deliberately not `next/image`**: its optimizer refetches the URL server-side without the
httpOnly `wp_token` cookie, which would 401.

The lightbox is a standalone overlay, not built on `components/ui/modal.tsx`, because every
attachment list already renders inside an open `Modal` (the vendor/payment/expense edit dialogs) —
a lightbox is therefore always a *nested* overlay. `Modal`'s Escape handler is an unscoped
`document` listener, so a naive nested `Modal` would close both layers on one Escape press. The
lightbox's own Escape listener registers with `{ capture: true }` and calls `stopPropagation()` so
it wins the race and the parent edit dialog survives. Covered by the last case in
`e2e/attachments.spec.ts`.

Wired into:
- **Vendors** (`components/vendors/vendor-list.tsx`) — the vendor edit modal (owner = the vendor
  itself) and each payment row's expandable panel (owner = that `VendorPayment`, toggled by a
  paperclip button).
- **Budget** (`components/budget/budget-tracker.tsx`) — the expense edit modal (owner = that
  `Expense`; only reachable for non-managed lines, since managed lines have no edit modal at all —
  see [vendor-catalog.md](vendor-catalog.md)).

`app/actions/attachments.ts` — `listAttachmentsAction`, `uploadAttachmentAction` (maps **415** →
"unsupported file type" and **413** → "too large" to friendlier copy), `deleteAttachmentAction`.

## Key files

- `backend/.../domain/{Attachment,AttachmentOwnerType}.java`, `service/AttachmentService.java`,
  `storage/{AttachmentStorage,FilesystemAttachmentStorage}.java`,
  `web/AttachmentController.java`, `dto/AttachmentDtos.java`, migration `V16`
- `frontend/components/attachments/{attachment-list,image-lightbox}.tsx`,
  `app/actions/attachments.ts`
- Tests: `AttachmentServiceTest`, `AttachmentControllerIntegrationTest`, `e2e/attachments.spec.ts`

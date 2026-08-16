# Attire, Entourage, and Invitation Extras

Three small additions to the public invitation page (`V21`): dress-code guidance, the wedding
party (entourage), and a handful of invitation-etiquette fields — an RSVP-by deadline, a kids
policy, and a social hashtag. All are optional; each renders on `/rsvp/[token]` only when set,
the same "no data, no section" rule as [project-photos.md](project-photos.md)'s venue sections.

## Data model

Seven nullable scalar columns on `projects` (`V21__attire_entourage_and_invitation_extras.sql`):

- `dress_code`, `attire_notes_men`, `attire_notes_women` — free text.
- `attire_palette` — a single comma-separated hex-color list, e.g. `"#f4a5a5,#a5c4f4"`. Like
  venue name/address, this has no independent lifecycle and no per-guest variation, so it's one
  delimited column rather than a child table — the Settings UI is what gives it list-like editing
  (add/remove color rows), not the schema.
- `rsvp_deadline` (date), `kids_policy` (text), `social_hashtag` (text, no leading `#`).

**Entourage is different — a genuine ordered list** — so it gets its own table,
`entourage_members` (`id`, `project_id` FK `ON DELETE CASCADE`, `role`, `name`, `sort_order`),
and follows the `Guest`/`Task` child-resource shape (own repository, service, controller) rather
than the admin-managed lookup-table shape (`guest_roles`/`vendor_categories`) — there's no
lighter-weight "simple list" pattern elsewhere in this codebase to reuse. No soft delete: unlike
the four entities `V18` covers, an entourage entry has no financial or historical weight, so
`EntourageService.remove` hard-deletes.

## API

`/api/projects/{projectId}/entourage`, `@projectSecurity.canAccess`-gated (same tier as guests,
not manage-only):

| Method | Path | |
|---|---|---|
| `GET` | `/entourage` | list, ordered by `sortOrder` |
| `POST` | `/entourage` | add, appended at `max(sortOrder) + 1` |
| `PUT` | `/entourage/{memberId}` | update role/name |
| `DELETE` | `/entourage/{memberId}` | remove |
| `PUT` | `/entourage/{memberId}/move-up` \| `/move-down` | swap `sortOrder` with the adjacent member; a no-op at either end |

Reordering is move-up/move-down, not drag-and-drop — deliberately, to keep this at "simple list"
scope rather than wiring `@dnd-kit` (already a dependency, used by the Kanban board) for a list
this small.

The seven scalar fields ride on the existing `PUT /api/projects/{projectId}` (`ProjectRequest`/
`ProjectResponse`) — no new endpoint, same as the venue fields in
[project-settings.md](project-settings.md).

**Public exposure.** `RsvpDtos.RsvpViewResponse` carries the seven scalar fields directly, plus
`entourage: List<PublicEntourageMember>` — a small nested record with only `role`/`name`, no id,
matching this DTO's existing "no internal ids" rule (`EntourageDtos.EntourageMemberResponse` is
the id-bearing, planner-facing shape used everywhere else). `GuestService.viewByRsvpToken` /
`respondByRsvpToken` fetch the list via `EntourageService.listForPublicView(projectId)` when
building the response.

## Frontend

**Settings** (`project-settings-form.tsx`): an **Attire** card (dress code, men's/women's notes,
and `AttirePaletteEditor` — a repeatable row of native `<input type="color">` swatches, joined
into one comma-separated string on submit via a hidden `attirePalette` field) and an **Invitation
extras** card (RSVP-by date, hashtag, kids policy) — both part of the main JSON-PUT form, same as
the Ceremony/Reception cards.

**Entourage** (`entourage-manager.tsx`): its own card, outside the main form — it's a separate
resource with its own endpoints, not a `Project` field, the same reasoning that keeps the Photos
card separate. Add via a small `{role, name}` form; each row has move-up/move-down/remove
buttons calling `app/actions/entourage.ts` directly (not through a `<form>`), matching the
pattern `project-photo-upload.tsx` uses for its Remove button.

**Public page** (`app/rsvp/[token]/page.tsx`): `AttireSection` (dress code, a two-column
men/women notes grid, and palette swatches as colored circles — each carries an `aria-label` with
its hex value, since a color swatch is the one place on this page where a non-text identity cue
is unavoidable) and `EntourageSection` (a two-column `role — name` list) render after the
Ceremony/Reception sections. The RSVP-deadline/kids-policy/hashtag lines render inline near the
header, next to the existing "Add to calendar" link. All four (ceremony, reception, attire,
entourage) share one generalized divider-between-populated-sections loop in `page.tsx`, replacing
the old ceremony/reception-only pairwise check now that there are more than two possible sections.

## Key files

- `backend/.../domain/EntourageMember.java`, `repository/EntourageMemberRepository.java`,
  `service/EntourageService.java`, `web/EntourageController.java`,
  `dto/{EntourageDtos,ProjectRequest,ProjectResponse,RsvpDtos}.java`, migration `V21`
- `frontend/components/projects/{entourage-manager,project-settings-form}.tsx`,
  `components/rsvp/{attire-section,entourage-section}.tsx`, `app/actions/entourage.ts`,
  `app/actions/projects.ts` (`updateProjectAction`), `app/rsvp/[token]/page.tsx`
- Tests: `EntourageServiceTest`, `InvitationRsvpAdminIntegrationTest` (attire/extras/entourage
  round trip, reorder, tenant isolation), `e2e/entourage.spec.ts`,
  `e2e/attire-entourage-rsvp.spec.ts`, `e2e/project-settings.spec.ts` (attire/extras persistence)

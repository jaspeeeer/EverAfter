# Undo Delete

Deleting a vendor, guest, expense, or task is soft — the row is tombstoned, not removed — and the
frontend surfaces a brief **Undo** window in the confirmation toast. No trash/browse-the-deleted UI
exists; undo within the toast's lifetime is the only way back.

This is the second half of a two-phase change. The schema (`deleted_at` + `@SQLRestriction`) and
read-side filtering landed first, on their own, proven against the full existing test suite with
**zero test modifications** — see the `V18` migration's header comment for why a DB-level behavior
shift under every read needed to ship and be verified in isolation before any new user-facing
behavior went on top of it. This doc covers what that second phase actually wired up.

## Backend mechanics

**Delete** (`{Vendor,Guest,Expense,Task}Service.delete`) sets `deletedAt = Instant.now()` on the
entity instead of calling `repository.delete(...)`. Dirty-checking persists it — no explicit
`.save()`, same idiom as `VendorPayment.markPaid` elsewhere in the codebase.

**Restore** (`POST …/{id}/restore`, same `@PreAuthorize` rule as the delete it reverses) can't just
`findById` the row — `@SQLRestriction` hides a tombstoned row from every ORM read, including the
one that would load it to un-delete it. Each repository instead has a **native**
`@Modifying @Query` `UPDATE … SET deleted_at = NULL WHERE id = :id AND project_id = :projectId AND
deleted_at IS NOT NULL` — native queries bypass `@SQLRestriction` entirely. The row count (0 or 1)
is the 404 signal: not found, wrong project, or not actually deleted all collapse into one 404,
never a 500.

**Vendor packages are the one entity with a cascade.** Soft-deleting a package stamps its
currently-*live* items with the **exact same `Instant`** the package itself gets
(`VendorRepository.findByParentId`, which — because it's a normal, `@SQLRestriction`-respecting
query — only returns items that are still live, leaving anything already independently deleted
alone). Restoring the package reads that timestamp back via a native
`findDeletedAtIfSoftDeleted` (again bypassing the restriction), restores the package, then restores
only items whose `deleted_at` matches — `restoreItemsWithDeletedAt`. This is what keeps an item
deleted at a *different* time from being incorrectly resurrected alongside the package.

**Landmines specific to vendors** (found in this order while landing the feature — see
[vendors.md](vendors.md) for the full list):
- The managed (agreed-price) expense line stays a **hard** delete; restore recreates it via
  `syncVendorExpense` rather than restoring a tombstone, since it's derived bookkeeping and
  `findByVendorIdAndManagedTrue` assumes at most one live row.
- Manual expense mappings are **explicitly unmapped** (`expense.setVendor(null)`) rather than left
  alone. A hard delete used to unmap them for free via the DB's `ON DELETE SET NULL`; a soft delete
  never fires it, since the vendor row still physically exists. Left mapped, the very next read of
  that expense — `ExpenseResponse.from`'s `expense.getVendor().getName()` — would try to
  initialize a lazy proxy for a row `@SQLRestriction` now hides and throw
  `EntityNotFoundException`. **This is a real bug the full test suite caught**, not a
  hypothetical: any `@ManyToOne` pointing at a soft-deletable entity, where the referencing side's
  own delete/unmap only used to happen via a DB-level `ON DELETE SET NULL` that a soft delete never
  triggers, is a latent landmine — worth checking again if another soft-deletable entity gets
  referenced this way later. (Accessing only `.getId()` on a lazy proxy is safe and needs no fix —
  `VendorResponse.from`'s `vendor.getParent().getId()` never hits the DB. The danger is specifically
  a non-ID property forcing initialization.)
- Attachments and vendor payments are completely untouched by delete/restore — nothing is actually
  removed, so there's nothing to orphan or bring back.

## Frontend mechanics

`components/ui/toast.tsx`'s `toast()` gained an options parameter —
`toast(message, variant?, { duration?, action?: { label, onClick } })` — **while keeping the
original positional calls working unchanged** (11 pre-existing call sites use `toast(msg)` /
`toast(msg, "error")`). An actionable toast defaults to an 8s window instead of the normal 3.5s.
The provider now retains each toast's `setTimeout` handle (previously discarded) so `dismiss` can
cancel a pending auto-dismiss — needed for the undo button to close its own toast immediately
rather than lingering with a now-stale action for the rest of the window.

Each of the four `remove()` closures (`vendor-list.tsx`, `guest-list.tsx`, `budget-tracker.tsx`,
`checklist-board.tsx`) follows the same shape:
```ts
const res = await deleteXAction(projectId, x.id);
if (res.error) { toast(res.error, "error"); return; }
toast("X deleted", "success", {
  action: { label: "Undo", onClick: () => startTransition(async () => {
    const restoreRes = await restoreXAction(projectId, x.id);
    toast(restoreRes.error ?? "X restored", restoreRes.error ? "error" : "success");
  }) },
});
```
The four delete server actions (`app/actions/{vendors,guests,expenses,tasks}.ts`) changed from
`Promise<void>` to `Promise<{ error?: string }>` to make this possible — previously a failed
delete threw inside the transition and the success toast (and now, the undo action) never had a
chance to run.

## Key files

- `backend/.../domain/{Vendor,Guest,Expense,Task}.java` (`deletedAt` field, added in `V18`)
- `backend/.../repository/{Vendor,Guest,Expense,Task}Repository.java` (native restore queries)
- `backend/.../service/{Vendor,Guest,Expense,Task}Service.java` (`delete`/`restore`)
- `backend/.../web/{Vendor,Guest,Expense,Task}Controller.java` (`POST …/{id}/restore`)
- `frontend/components/ui/toast.tsx`, `app/actions/{vendors,guests,expenses,tasks}.ts`
- Tests: `RestoreIntegrationTest` (package cascade, managed-expense recreation, tenant scoping,
  no-op-on-live), `AttachmentControllerIntegrationTest` (attachment survival through
  delete→restore), `e2e/undo-delete.spec.ts`

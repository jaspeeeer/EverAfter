# Vendors

Supplier tracking per project: who's being considered, their contact details, and whether
they're booked.

## API

Nested under the project; all endpoints `canAccess`-gated on `{projectId}`:

- `GET /api/projects/{projectId}/vendors`
- `POST …/vendors` — `{ name, categoryId, contactEmail?, phone?, booked, agreedPrice?, parentId? }`
- `PUT …/vendors/{vendorId}` — full replace
- `DELETE …/vendors/{vendorId}`
- `POST …/vendors/from-directory` — `{ directoryId }`, planner/admin (adds from the global directory)

Categories are **admin-managed data** (a lookup table), not a hard enum — vendors reference one by
`categoryId`. The `agreedPrice` is the vendor's **full amount to be paid**; it feeds the Budget tab
and can be settled in **installments or one full payment** via the vendor's **Payments** button
(`vendor_payments`, `V10`). See [vendor-catalog.md](vendor-catalog.md) for categories, the
directory, pricing, payments, and reports.

## Payments (installments)

- `GET/POST/DELETE /api/projects/{projectId}/vendors/{vendorId}/payments` (`canAccess`-gated). A
  payment is `{ amount, paidOn, note? }`; the API rejects a payment that has no agreed full amount
  or that would exceed the remaining balance.
- The vendor row shows `₱paid / ₱full paid (X%)` progress; the **Payments** modal lists
  installments and records new ones (each chip also shows its % of the full amount). Payment
  changes keep the vendor's managed budget line's `paidAmount` in sync, so the Budget roll-up
  reflects partial payment.

## Package vendors (`vendors.parent_id`, `V11`)

A vendor can bundle other vendors under it as a **package** — e.g. an all-in coordinator package
that bundles catering, photography, and flowers into one deal. There's no separate flag: a
**package** is simply a top-level vendor (`parentId == null`) that has items nested under it; an
**item** has `parentId` set to its package's id. One level of nesting only — an item can't itself
contain items.

- **Money lives only on the package.** Its `agreedPrice`/payments/managed budget line work exactly
  as for any vendor (see [vendor-catalog.md](vendor-catalog.md)); an item can carry **no price, no
  payments, and no managed expense of its own** — `VendorService` rejects a `POST`/`PUT` that sets
  both `parentId` and `agreedPrice`, and rejects a payment on an item. This is what keeps the
  budget and admin reports from double-counting a package and its items.
- Deleting a package **cascades** its items (`ON DELETE CASCADE`); any of the package's items with
  a *manual* expense mapping just unmap (same `SET NULL` behavior as any vendor delete).
- Admin reports and the platform vendor count treat items as invisible — they're not separate
  "vendors" for those purposes (`vendorRepository...` queries filter `parent is null`).

## Frontend (`/projects/[id]/vendors`)

`components/vendors/vendor-list.tsx`:

- Row list with name, category badge, agreed-price badge, contact line. A package additionally
  shows a **Package** badge; its items render indented directly beneath it (excluded from
  search/sort/paging — a package's own row absorbs its items' names into its search text, so
  searching for a bundled item still surfaces the package).
- Every top-level row has an **Add item** button (`VendorFormModal` in a reduced "package item"
  mode — no price field, `parentId` preset via a hidden input); item rows show only
  name/category/contact + edit/delete (`lib/vendor-tree.ts` builds the parent → items grouping,
  reused by the expense-form vendor picker and the timeline supplier picker to indent items there
  too).
- One-click **booked toggle** (green ✓ Booked badge ↔ "Mark booked" outline).
- Add/edit share one modal (`VendorFormModal`, category select is fetched, plus an agreed-price
  field); planners also get **Add from template** and **Add from directory** pickers.
- **Name search** (`SearchInput`) filters client-side.
- Toasts on add/update/book/delete.

## Key files

- `backend/.../domain/Vendor.java`, `service/VendorService.java`, `web/VendorController.java`,
  migration `V11`
- `frontend/app/actions/vendors.ts`, `components/vendors/vendor-list.tsx`, `lib/vendor-tree.ts`

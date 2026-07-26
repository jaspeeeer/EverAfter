# Vendor Catalog, Pricing & Reports

Five related features around vendors: admin-managed categories, agreed pricing that feeds the
budget, a global vendor directory, admin reports, and package vendors.

## 1. Vendor categories (admin-managed)

Categories were a hardcoded enum; they are now a `vendor_categories` table (Flyway `V6`, backfilled
from the old enum column) that vendors and vendor-template items reference by FK.

- Anyone authenticated reads the **active** list: `GET /api/vendor-categories` (the vendor form
  picker). Admin CRUD: `GET/POST/PUT/DELETE /api/admin/vendor-categories` (full list incl.
  inactive), `hasRole('ADMIN')`.
- `POST` derives a stable `slug` from the name; duplicate names → **409**.
- **Delete policy:** an unused category is hard-deleted; one still referenced by any vendor,
  template item, directory entry, **or expense** is **deactivated** (`active=false`) — kept on
  existing data, hidden from new pickers. Renaming a category propagates everywhere (FK + name
  lookup).
- **One lookup for vendors *and* expenses.** Expenses reference the same table by FK
  (`expenses.category_id`, `V8`); the old `ExpenseCategory` enum is gone. Every category dropdown in
  the app — vendor form, vendor directory, vendor template items, in-demand report filter, and
  **Add Expense** — is fed by `GET /api/vendor-categories`, so adding a category surfaces it
  everywhere.
- Existence (not active) is required when assigning a category, so an existing vendor whose
  category was deactivated can still be edited.

## 2. Vendor agreed price → budget

`vendors.agreed_price` (`V7`) is the vendor's **full amount to be paid**, editable by anyone who can
edit the project's vendors. Setting it **upserts one linked budget expense** (`expenses.vendor_id`,
flagged `managed = true`, `V9`): description = vendor name, amount = the full price, category = the
vendor's own category (vendors and expenses share one lookup). Clearing the price deletes the
managed line; deleting the vendor removes it explicitly.

**Payments (installments).** `vendor_payments` (`V10`) records each payment against the full amount
(a single payment = full, several = installments; the API rejects overpayment). The managed
expense's `paid_amount` = the sum of the vendor's payments, so the budget reflects partial payment
— `BudgetService.totalPaid` is `sum(paid_amount)`, not all-or-nothing. Endpoints:
`GET/POST/DELETE …/vendors/{vendorId}/payments`; UI: the vendor row's **Payments** modal (each
figure also shows its `formatPercent` share of the full amount).

Separately, users can **map any expense to a vendor** from the Budget tab (`managed = false`) —
those manual mappings are fully editable and survive vendor deletion (the FK is
`ON DELETE SET NULL`, so they just unmap). The sync only ever touches the one `managed` line;
vendor-linked *managed* expenses are read-only on the Budget tab (a "From vendor" badge, managed
via the Vendors tab) — `ExpenseService` rejects direct edit/delete of them.

## 3. Global vendor directory

`vendor_directory` (`V7`) is an admin-curated master list. Admin CRUD:
`/api/admin/vendor-directory` (`hasRole('ADMIN')`); planners browse the active list:
`GET /api/vendor-directory` (`hasAnyRole('ADMIN','PLANNER')`). "Add from directory"
(`POST /api/projects/{id}/vendors/from-directory`) copies an entry into the project as a new
vendor and sets `vendors.directory_id` — the link that powers the in-demand report. Delete of an
in-use entry deactivates it (keeps the link) like categories.

## 4. Admin reports (`/api/admin/reports/**`, `hasRole('ADMIN')`)

Group-by aggregations on `VendorRepository` (precedent: `UserRepository.countUsersByRole`):
- **Vendors by category** — per category: vendor count, booked count, total agreed value.
- **In-demand vendors** — usages grouped by directory-entry name (or vendor name when unlinked),
  filtered by the project's `weddingDate` in an optional `[from, to]` window and optional category;
  sorted by usage. (Boolean guards, not `:from is null`, so Postgres can type the nullable params.)
- **Booking conversion** — considered vs booked per category + overall rate.

CSV is built **client-side** from the JSON (`lib/csv.ts` `rowsToCsv` + `downloadCsv`), so there's
no server content-negotiation. Each report also shows a `%` figure (vendors-by-category and
in-demand rows get a "Booked %" column; booking-conversion already showed a rate). UI:
`/admin/reports` (single-hue proportional bars + a filterable in-demand table);
`/admin/vendor-categories` and `/admin/vendor-directory` for management, all linked from `/admin`.

All three report queries — and the admin platform `totalVendors` stat — count **top-level vendors
only** (`v.parent is null`); see below.

## 5. Package vendors (`vendors.parent_id`, `V11`)

A vendor can bundle other vendors under it as a **package** (e.g. an all-in coordinator package
bundling catering + photography + flowers). There's no separate "is package" flag — a package is
simply a top-level vendor (`parent_id IS NULL`) that has items nested under it (`parent_id` set to
the package's id); one level of nesting only, enforced in `VendorService.resolveParent`.

- **Money lives only on the package** — an item can carry no `agreedPrice`, no payments, and no
  managed expense of its own (`VendorService` rejects a price/payment on an item with a clear
  message). `syncVendorExpense`'s very first check is `vendor.getParent() != null` → delete any
  managed line and return, which is the single guard that keeps the budget from double-counting a
  package alongside its items.
- Deleting a package **cascades** its items (`ON DELETE CASCADE` on `vendors.parent_id`); an item's
  own *manual* expense mapping just unmaps, same as any vendor delete (`V9`).
- The three report aggregations and `AdminService.stats().totalVendors` filter `v.parent is null`
  so items are invisible to cross-project counts/values — the package's own price/booking already
  represents the whole bundle.
- Frontend: `components/vendors/vendor-list.tsx` groups vendors into packages + items via
  `lib/vendor-tree.ts` (`groupVendorsByParent`, `orderVendorsForPicker` — the latter also indents
  items in the expense-form vendor `<select>` and the timeline supplier checkbox list). A package
  shows a **Package** badge; each top-level row has an **Add item** button opening the vendor form
  in a reduced mode (no price field, `parentId` preset).

## Key files

- `backend/.../domain/{VendorCategory,VendorDirectoryEntry,Vendor,Expense}.java`,
  `service/{VendorCategoryService,VendorDirectoryService,VendorService,ExpenseService,ReportService}.java`,
  `web/{VendorCategory*,VendorDirectory*,Report,Vendor}Controller.java`, migrations `V6`/`V7`/`V11`
- `frontend/components/admin/{vendor-category-manager,vendor-directory-manager,reports-view}.tsx`,
  `components/vendors/vendor-list.tsx`, `lib/vendor-tree.ts`,
  `app/actions/{vendors,vendor-catalog,reports}.ts`
- Tests: `VendorCatalogIntegrationTest`, `VendorReportIntegrationTest`, `e2e/vendor-admin.spec.ts`

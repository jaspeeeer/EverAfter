# Budget Tracker

Expense line items per project, a server-computed roll-up, and a spend-by-category chart.
All money is displayed as **Philippine Peso** (`formatMoney` → `en-PH` / `PHP`).

## API

- `GET /api/projects/{projectId}/budget` — roll-up computed in `BudgetService` with `BigDecimal`:
  `totalBudget` (from the project, nullable), `totalExpenses`, `totalPaid`, `totalOutstanding`,
  `remaining` (null when no budget set), `overBudget`.
- CRUD at `…/expenses` — `{ description, amount, categoryId, paid }`, `canAccess`-gated, full
  replace on PUT. **Categories are the shared admin-managed `vendor_categories` lookup** (not a
  fixed enum) — expenses reference one by `categoryId` (`expenses.category_id` FK, `V8`), and the
  Add Expense dropdown is fed by `GET /api/vendor-categories`. So a category the admin adds shows
  up here too. `ExpenseResponse` exposes `categoryId` + `categoryName`. See
  [vendor-catalog.md](vendor-catalog.md).

**Mapping an expense to a vendor.** The expense form has an optional **Vendor** dropdown
(`expenses.vendor_id`, populated from the project's vendors) so any line can be attributed to a
supplier; the row then shows the vendor's name and the value is searchable by it.

**Managed (agreed-price) lines.** A vendor's `agreedPrice` (the full amount) auto-syncs one
**managed** expense (`expenses.managed = true`) whose category is the vendor's own category. These
show a "From vendor" badge and are **fully read-only here** — their paid state is driven by the
vendor's **payments/installments** (the row shows `₱paid / ₱full`, not a toggle). Manage them on the
Vendors tab. A **manual** vendor mapping (`managed = false`) is fully editable. Deleting a vendor
**removes** its managed line but only **unmaps** manual ones (`vendor_id` FK `ON DELETE SET NULL`,
`V9`). See [vendor-catalog.md](vendor-catalog.md).

**Partial payment.** `expenses.paid_amount` (`V10`) tracks how much of a line is paid — the amount
when a regular line is toggled paid, or the sum of a vendor's payments for a managed line. The
roll-up's **Paid** total is `sum(paid_amount)`, so vendor installments show up as partial paid, not
all-or-nothing.

## Soft delete (`V18`, infrastructure only — no user-facing change yet)

`expenses.deleted_at` (nullable timestamp) plus `@SQLRestriction("deleted_at is null")` on
`Expense` excludes a tombstoned line from every existing read — `findByProjectId` (and therefore
`BudgetService.summarize`'s roll-up), `countByCategoryId`, `AdminService.stats()`'s plain
`count()` — with no per-query changes. `ExpenseService.delete` still hard-deletes for now; nothing
sets the column yet. See [guests.md](guests.md) for why this shipped as its own change ahead of
the actual undo feature.

## Frontend (`/projects/[id]/budget`)

- **Budget tracker card** — Budget / Committed / Paid / Outstanding stats (each with a `formatPercent`
  hint — Committed shows its % of budget, Paid/Outstanding their % of committed), a **two-segment**
  progress bar (paid in `--chart-paid`, committed-but-unpaid in `--chart-outstanding` — destructive
  red instead when over budget) with a small paid/outstanding legend, and "On track / Over budget"
  badge. Renders even with no budget cap set (scales against total committed spend instead). The
  project overview page (`app/(app)/projects/[id]/page.tsx`) has a matching, simpler copy of this bar.
- **Spend by category** (`components/budget/category-breakdown.tsx`) — horizontal stacked bars
  (paid + outstanding per category, using each expense's **`paidAmount`** so a partially-paid vendor
  installment shows as partially paid rather than all-or-nothing), sorted largest-first, with
  legend, per-row value + `(X% paid)` labels, 2px surface gaps between segments, rounded data ends,
  and hover tooltips showing the split (with the same percentage). Series colors are the
  `--chart-paid` / `--chart-outstanding` tokens in `globals.css` — **CVD-validated per theme**
  (light reuses brand rose/gold; dark mode has its own darker steps). If you change them, re-run
  the dataviz palette validator for both surfaces.
- **Expense list** — add/edit modal, paid/unpaid toggle, delete; toasts throughout. Mutations
  revalidate both the budget tab and the overview (which shows the same roll-up).

## Key files

- `backend/.../service/BudgetService.java` (+ `BudgetServiceTest` for the math)
- `frontend/components/budget/{budget-tracker,category-breakdown}.tsx`,
  `app/actions/expenses.ts`, `lib/format.ts` (`formatPercent`, the house `"40%"` percentage helper
  reused across the budget, vendor, guest, admin, and report surfaces)

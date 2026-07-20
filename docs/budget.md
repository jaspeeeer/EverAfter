# Budget Tracker

Expense line items per project, a server-computed roll-up, and a spend-by-category chart.
All money is displayed as **Philippine Peso** (`formatMoney` → `en-PH` / `PHP`).

## API

- `GET /api/projects/{projectId}/budget` — roll-up computed in `BudgetService` with `BigDecimal`:
  `totalBudget` (from the project, nullable), `totalExpenses`, `totalPaid`, `totalOutstanding`,
  `remaining` (null when no budget set), `overBudget`.
- CRUD at `…/expenses` — `{ description, amount, category, paid }`, `canAccess`-gated, full
  replace on PUT. Categories (`ExpenseCategory`): VENUE, CATERING, PHOTOGRAPHY, VIDEOGRAPHY,
  FLOWERS, MUSIC, ATTIRE, BEAUTY, STATIONERY, TRANSPORT, GIFTS, OTHER.

## Frontend (`/projects/[id]/budget`)

- **Budget tracker card** — Budget / Committed / Paid / Outstanding stats, progress bar
  (destructive red when over budget), "On track / Over budget" badge.
- **Spend by category** (`components/budget/category-breakdown.tsx`) — horizontal stacked bars
  (paid + outstanding per category), sorted largest-first, with legend, per-row value labels,
  2px surface gaps between segments, rounded data ends, and hover tooltips showing the split.
  Series colors are the `--chart-paid` / `--chart-outstanding` tokens in `globals.css` —
  **CVD-validated per theme** (light reuses brand rose/gold; dark mode has its own darker
  steps). If you change them, re-run the dataviz palette validator for both surfaces.
- **Expense list** — add/edit modal, paid/unpaid toggle, delete; toasts throughout. Mutations
  revalidate both the budget tab and the overview (which shows the same roll-up).

## Key files

- `backend/.../service/BudgetService.java` (+ `BudgetServiceTest` for the math)
- `frontend/components/budget/{budget-tracker,category-breakdown}.tsx`,
  `app/actions/expenses.ts`

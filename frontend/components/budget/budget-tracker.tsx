"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import { Check, Pencil, Plus, Store, Trash2, Wallet } from "lucide-react";
import {
  createExpenseAction,
  deleteExpenseAction,
  editExpenseAction,
  updateExpenseAction,
} from "@/app/actions/expenses";
import { AttachmentList } from "@/components/attachments/attachment-list";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CategoryBreakdown } from "@/components/budget/category-breakdown";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import { formatMoney, formatPercent } from "@/lib/format";
import { orderVendorsForPicker } from "@/lib/vendor-tree";
import type {
  BudgetSummaryResponse,
  ExpenseResponse,
  VendorCategoryResponse,
  VendorResponse,
} from "@/lib/types";

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function BudgetTracker({
  projectId,
  budget,
  expenses,
  categories,
  vendors,
}: {
  projectId: string;
  budget: BudgetSummaryResponse;
  expenses: ExpenseResponse[];
  categories: VendorCategoryResponse[];
  vendors: VendorResponse[];
}) {
  const [form, setForm] = useState<{ open: boolean; expense: ExpenseResponse | null }>({
    open: false,
    expense: null,
  });
  // Scale the progress bar against the budget when set, else against total committed spend
  // (so the paid/outstanding split still renders for projects with no budget cap).
  const budgetDenom = budget.totalBudget ?? budget.totalExpenses;
  const paidPct =
    budgetDenom > 0 ? Math.min(100, (budget.totalPaid / budgetDenom) * 100) : 0;
  const unpaidCommittedPct =
    budgetDenom > 0
      ? Math.min(
          100 - paidPct,
          Math.max(0, ((budget.totalExpenses - budget.totalPaid) / budgetDenom) * 100),
        )
      : 0;
  const showProgress = budget.totalBudget !== null || budget.totalExpenses > 0;

  const t = useTableControls(expenses, {
    search: (e) => `${e.description} ${e.categoryName} ${e.vendorName ?? ""}`,
    sortOptions: [
      { key: "description", label: "Description", get: (e) => e.description },
      { key: "category", label: "Category", get: (e) => e.categoryName },
      { key: "amount", label: "Amount", get: (e) => e.amount },
      { key: "paid", label: "Paid", get: (e) => e.paid },
    ],
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Budget tracker</CardTitle>
              <CardDescription>Planned vs. committed spend.</CardDescription>
            </div>
            {budget.overBudget ? (
              <Badge variant="destructive">Over budget</Badge>
            ) : (
              <Badge variant="success">On track</Badge>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Stat label="Budget" value={formatMoney(budget.totalBudget)} />
            <Stat
              label="Committed"
              value={formatMoney(budget.totalExpenses)}
              hint={
                budget.totalBudget !== null
                  ? `${formatPercent(budget.totalExpenses, budget.totalBudget)} of budget`
                  : undefined
              }
            />
            <Stat
              label="Paid"
              value={formatMoney(budget.totalPaid)}
              hint={
                budget.totalExpenses > 0
                  ? `${formatPercent(budget.totalPaid, budget.totalExpenses)} of committed`
                  : undefined
              }
            />
            <Stat
              label="Outstanding"
              value={formatMoney(budget.totalOutstanding)}
              hint={
                budget.totalExpenses > 0
                  ? `${formatPercent(budget.totalOutstanding, budget.totalExpenses)} of committed`
                  : undefined
              }
            />
          </div>
          {showProgress && (
            <>
              <div
                className="flex h-2 w-full overflow-hidden rounded-full bg-muted"
                role="img"
                aria-label={`${formatMoney(budget.totalPaid)} paid, ${formatMoney(
                  budget.totalExpenses - budget.totalPaid,
                )} outstanding${
                  budget.totalBudget !== null
                    ? ` of ${formatMoney(budget.totalBudget)} budget`
                    : ""
                }`}
              >
                {paidPct > 0 && (
                  <span
                    className="h-full"
                    style={{ width: `${paidPct}%`, background: "var(--chart-paid)" }}
                  />
                )}
                {paidPct > 0 && unpaidCommittedPct > 0 && (
                  <span className="h-full w-[2px] shrink-0 bg-card" aria-hidden />
                )}
                {unpaidCommittedPct > 0 && (
                  <span
                    className={cn("h-full", budget.overBudget && "bg-destructive")}
                    style={
                      budget.overBudget
                        ? { width: `${unpaidCommittedPct}%` }
                        : { width: `${unpaidCommittedPct}%`, background: "var(--chart-outstanding)" }
                    }
                  />
                )}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
                <div className="flex items-center gap-3">
                  <span className="flex items-center gap-1.5">
                    <span
                      aria-hidden
                      className="inline-block size-2 rounded-full"
                      style={{ background: "var(--chart-paid)" }}
                    />
                    Paid
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span
                      aria-hidden
                      className={cn(
                        "inline-block size-2 rounded-full",
                        budget.overBudget && "bg-destructive",
                      )}
                      style={budget.overBudget ? undefined : { background: "var(--chart-outstanding)" }}
                    />
                    Outstanding
                  </span>
                </div>
                {budget.totalBudget !== null && (
                  <span>
                    {formatMoney(budget.remaining)} remaining of {formatMoney(budget.totalBudget)}.
                  </span>
                )}
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <CategoryBreakdown expenses={expenses} />

      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Expenses</h2>
        <Button size="sm" onClick={() => setForm({ open: true, expense: null })}>
          <Plus />
          Add expense
        </Button>
      </div>

      {expenses.length > 0 && (
        <TableToolbar>
          <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search expenses…" />
          <SortControl {...t} />
        </TableToolbar>
      )}

      {expenses.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border py-14 text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Wallet className="size-6" />
          </div>
          <p className="text-sm text-muted-foreground">
            No expenses yet. Add line items to track your spend.
          </p>
        </div>
      ) : t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          No expenses match your search.
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {t.pageItems.map((expense) => (
            <ExpenseRow
              key={expense.id}
              projectId={projectId}
              expense={expense}
              onEdit={() => setForm({ open: true, expense })}
            />
          ))}
        </Card>
      )}

      <Pagination {...t} />

      <ExpenseFormModal
        key={form.expense?.id ?? "new"}
        projectId={projectId}
        expense={form.expense}
        categories={categories}
        vendors={vendors}
        open={form.open}
        onClose={() => setForm((f) => ({ ...f, open: false }))}
      />
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 text-lg font-semibold">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

function ExpenseRow({
  projectId,
  expense,
  onEdit,
}: {
  projectId: string;
  expense: ExpenseResponse;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const togglePaid = () => {
    startTransition(async () => {
      await updateExpenseAction(projectId, expense.id, {
        description: expense.description,
        amount: expense.amount,
        categoryId: expense.categoryId,
        vendorId: expense.vendorId,
        paid: !expense.paid,
      });
      toast(expense.paid ? "Marked unpaid" : "Marked paid");
    });
  };

  const remove = () => {
    startTransition(async () => {
      await deleteExpenseAction(projectId, expense.id);
      toast("Expense deleted");
    });
  };

  // Managed lines mirror a vendor's agreed price and are edited on the Vendors tab.
  const managed = expense.managed;

  return (
    <div className={cn("flex items-center gap-3 p-4", pending && "opacity-50")}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-medium">{expense.description}</p>
          <Badge variant="secondary">{expense.categoryName}</Badge>
          {expense.vendorName && (
            <Badge variant="outline">
              <Store className="size-3" />
              {managed ? "From vendor" : expense.vendorName}
            </Badge>
          )}
        </div>
      </div>
      <span className="font-semibold tabular-nums">{formatMoney(expense.amount)}</span>
      {managed ? (
        // Paid state is driven by the vendor's payments; manage installments on the Vendors tab.
        <Badge variant={expense.paid ? "success" : "warning"} title="Managed by vendor payments">
          {expense.paid ? <Check className="size-3" /> : null}
          {formatMoney(expense.paidAmount)} / {formatMoney(expense.amount)} (
          {formatPercent(expense.paidAmount, expense.amount)})
        </Badge>
      ) : (
        <button type="button" onClick={togglePaid} disabled={pending} title="Toggle paid">
          {expense.paid ? (
            <Badge variant="success">
              <Check className="size-3" />
              Paid
            </Badge>
          ) : (
            <Badge variant="warning">Unpaid</Badge>
          )}
        </button>
      )}
      {/* Managed (agreed-price) lines are edited from the Vendors tab. */}
      {!managed && (
        <>
          <button
            type="button"
            onClick={onEdit}
            disabled={pending}
            aria-label="Edit expense"
            className="text-muted-foreground transition-colors hover:text-foreground"
          >
            <Pencil className="size-4" />
          </button>
          <button
            type="button"
            onClick={remove}
            disabled={pending}
            aria-label="Delete expense"
            className="text-muted-foreground transition-colors hover:text-destructive"
          >
            <Trash2 className="size-4" />
          </button>
        </>
      )}
    </div>
  );
}

function ExpenseFormModal({
  projectId,
  expense,
  categories,
  vendors,
  open,
  onClose,
}: {
  projectId: string;
  expense: ExpenseResponse | null;
  categories: VendorCategoryResponse[];
  vendors: VendorResponse[];
  open: boolean;
  onClose: () => void;
}) {
  const isEdit = expense !== null;
  const action = expense
    ? editExpenseAction.bind(null, projectId, expense.id)
    : createExpenseAction.bind(null, projectId);

  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  // Keep an existing expense's (possibly deactivated) category selectable.
  const options =
    expense && !categories.some((c) => c.id === expense.categoryId)
      ? [
          { id: expense.categoryId, name: expense.categoryName, slug: "", active: false },
          ...categories,
        ]
      : categories;

  useEffect(() => {
    if (state.ok) {
      toast(isEdit ? "Expense updated" : "Expense added");
      formRef.current?.reset();
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? "Edit expense" : "Add expense"}
      description="Track a budget line item."
    >
      <form ref={formRef} action={formAction} className="space-y-4">
        {state.error && (
          <p
            role="alert"
            className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {state.error}
          </p>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="description">Description</Label>
          <Input
            id="description"
            name="description"
            placeholder="Venue deposit"
            defaultValue={expense?.description}
            required
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="amount">Amount</Label>
            <Input
              id="amount"
              name="amount"
              type="number"
              min="0"
              step="0.01"
              placeholder="3000"
              defaultValue={expense?.amount}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="category">Category</Label>
            <select
              id="category"
              name="categoryId"
              defaultValue={expense?.categoryId ?? options[0]?.id}
              className={selectClass}
            >
              {options.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="vendorId">Vendor (optional)</Label>
          <select
            id="vendorId"
            name="vendorId"
            defaultValue={expense?.vendorId ?? ""}
            className={selectClass}
          >
            <option value="">— No vendor —</option>
            {orderVendorsForPicker(vendors).map(({ vendor: v, indent }) => (
              <option key={v.id} value={v.id}>
                {indent ? `— ${v.name}` : v.name}
              </option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name="paid"
            defaultChecked={expense?.paid ?? false}
            className="size-4 rounded border-input"
          />
          Already paid
        </label>
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : "Add expense"}
          </Button>
        </div>
      </form>
      {isEdit && expense && (
        <div className="mt-4 border-t border-border pt-4">
          <AttachmentList projectId={projectId} ownerType="EXPENSE" ownerId={expense.id} />
        </div>
      )}
    </Modal>
  );
}

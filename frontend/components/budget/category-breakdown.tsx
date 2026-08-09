"use client";

import { useState } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatMoney, formatPercent } from "@/lib/format";
import type { ExpenseResponse } from "@/lib/types";

interface CategoryRow {
  categoryId: string;
  categoryName: string;
  paid: number;
  outstanding: number;
  total: number;
}

/**
 * Spend-by-category breakdown: stacked horizontal bars (paid + outstanding per category),
 * sorted by total descending. Series colors come from the validated --chart-* tokens; identity
 * is never color-alone (legend + per-row text labels), and the expense list below acts as the
 * table view.
 */
export function CategoryBreakdown({ expenses }: { expenses: ExpenseResponse[] }) {
  const [hovered, setHovered] = useState<string | null>(null);

  if (expenses.length === 0) return null;

  const byCategory = new Map<string, CategoryRow>();
  for (const expense of expenses) {
    const row = byCategory.get(expense.categoryId) ?? {
      categoryId: expense.categoryId,
      categoryName: expense.categoryName,
      paid: 0,
      outstanding: 0,
      total: 0,
    };
    // paidAmount captures partial payment (a vendor's installments), not just all-or-nothing.
    row.paid += expense.paidAmount;
    row.outstanding += expense.amount - expense.paidAmount;
    row.total += expense.amount;
    byCategory.set(expense.categoryId, row);
  }
  const rows = [...byCategory.values()].sort((a, b) => b.total - a.total);
  const max = rows[0].total;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Spend by category</CardTitle>
        <CardDescription>Committed spend per category, largest first.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* Legend — identity for the two series, never color-alone */}
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span
              aria-hidden
              className="inline-block size-2.5 rounded-[3px]"
              style={{ background: "var(--chart-paid)" }}
            />
            Paid
          </span>
          <span className="flex items-center gap-1.5">
            <span
              aria-hidden
              className="inline-block size-2.5 rounded-[3px]"
              style={{ background: "var(--chart-outstanding)" }}
            />
            Outstanding
          </span>
        </div>

        <div className="space-y-2">
          {rows.map((row) => {
            const paidPct = (row.paid / max) * 100;
            const outstandingPct = (row.outstanding / max) * 100;
            const isHovered = hovered === row.categoryId;
            return (
              <div
                key={row.categoryId}
                className="group relative grid grid-cols-[7rem_1fr_auto] items-center gap-3"
                onMouseEnter={() => setHovered(row.categoryId)}
                onMouseLeave={() => setHovered(null)}
              >
                <span className="truncate text-sm text-muted-foreground">
                  {row.categoryName}
                </span>

                {/* Track: thin marks, 2px surface gap between stacked segments,
                    rounded corners only at the data end */}
                <div className="flex h-4 items-center" role="img"
                     aria-label={`${row.categoryName}: ${formatMoney(row.total)} total, ${formatMoney(row.paid)} paid (${formatPercent(row.paid, row.total)}), ${formatMoney(row.outstanding)} outstanding`}>
                  {row.paid > 0 && (
                    <span
                      className={row.outstanding > 0 ? "h-full" : "h-full rounded-r-[4px]"}
                      style={{
                        width: `${paidPct}%`,
                        background: "var(--chart-paid)",
                        opacity: hovered && !isHovered ? 0.45 : 1,
                        transition: "opacity 120ms",
                      }}
                    />
                  )}
                  {row.paid > 0 && row.outstanding > 0 && (
                    <span className="h-full w-[2px] shrink-0 bg-card" aria-hidden />
                  )}
                  {row.outstanding > 0 && (
                    <span
                      className="h-full rounded-r-[4px]"
                      style={{
                        width: `${outstandingPct}%`,
                        background: "var(--chart-outstanding)",
                        opacity: hovered && !isHovered ? 0.45 : 1,
                        transition: "opacity 120ms",
                      }}
                    />
                  )}
                </div>

                <span className="text-sm font-medium tabular-nums">
                  {formatMoney(row.total)}
                  <span className="ml-1 font-normal text-muted-foreground">
                    ({formatPercent(row.paid, row.total)} paid)
                  </span>
                </span>

                {/* Hover tooltip with the per-series split */}
                {isHovered && (row.paid > 0 || row.outstanding > 0) && (
                  <div className="pointer-events-none absolute -top-9 left-[7.75rem] z-10 whitespace-nowrap rounded-md border border-border bg-card px-2.5 py-1.5 text-xs shadow-md">
                    <span className="font-medium">{row.categoryName}</span>
                    <span className="text-muted-foreground">
                      {" "}· {formatMoney(row.paid)} paid ({formatPercent(row.paid, row.total)}) ·{" "}
                      {formatMoney(row.outstanding)} outstanding
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

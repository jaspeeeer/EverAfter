"use client";

import { useState, useTransition } from "react";
import { Download } from "lucide-react";
import { inDemandReportAction } from "@/app/actions/reports";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortableTh, SortControl } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { downloadCsv, rowsToCsv } from "@/lib/csv";
import { useTableControls } from "@/lib/use-table-controls";
import { formatMoney, formatPercent } from "@/lib/format";
import type {
  BookingConversionReport,
  InDemandVendorRow,
  VendorCategoryResponse,
  VendorsByCategoryRow,
} from "@/lib/types";

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function ReportsView({
  vendorsByCategory,
  bookingConversion,
  initialInDemand,
  categories,
}: {
  vendorsByCategory: VendorsByCategoryRow[];
  bookingConversion: BookingConversionReport;
  initialInDemand: InDemandVendorRow[];
  categories: VendorCategoryResponse[];
}) {
  return (
    <div className="space-y-10">
      <VendorsByCategorySection rows={vendorsByCategory} />
      <InDemandSection initial={initialInDemand} categories={categories} />
      <BookingConversionSection report={bookingConversion} />
    </div>
  );
}

function ExportButton({ onExport }: { onExport: () => void }) {
  return (
    <Button size="sm" variant="outline" onClick={onExport}>
      <Download />
      CSV
    </Button>
  );
}

function VendorsByCategorySection({ rows }: { rows: VendorsByCategoryRow[] }) {
  const max = Math.max(1, ...rows.map((r) => r.vendorCount));
  const t = useTableControls(rows, {
    paginate: false,
    sortOptions: [
      { key: "vendors", label: "Vendors", get: (r) => r.vendorCount },
      { key: "category", label: "Category", get: (r) => r.categoryName },
      { key: "booked", label: "Booked", get: (r) => r.bookedCount },
      { key: "value", label: "Total value", get: (r) => r.totalAgreedValue },
    ],
    initialSortDir: "desc",
  });

  const exportCsv = () =>
    downloadCsv(
      "vendors-by-category.csv",
      rowsToCsv(
        ["Category", "Vendors", "Booked", "Booked %", "Total agreed value"],
        rows.map((r) => [
          r.categoryName,
          r.vendorCount,
          r.bookedCount,
          formatPercent(r.bookedCount, r.vendorCount),
          r.totalAgreedValue,
        ]),
      ),
    );

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Vendors by category</CardTitle>
            <CardDescription>How many vendors sit in each category, and their value.</CardDescription>
          </div>
          {rows.length > 0 && (
            <div className="flex items-center gap-2">
              <SortControl {...t} />
              <ExportButton onExport={exportCsv} />
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">No vendors on the platform yet.</p>
        ) : (
          <div className="space-y-2">
            {t.pageItems.map((r) => (
              <div
                key={r.categoryId}
                className="grid grid-cols-[8rem_1fr_auto] items-center gap-3"
              >
                <span className="truncate text-sm text-muted-foreground">{r.categoryName}</span>
                <div className="flex h-4 items-center" role="img"
                     aria-label={`${r.categoryName}: ${r.vendorCount} vendors, ${r.bookedCount} booked (${formatPercent(r.bookedCount, r.vendorCount)})`}>
                  <span
                    className="h-full rounded-r-[4px]"
                    style={{ width: `${(r.vendorCount / max) * 100}%`, background: "var(--chart-paid)" }}
                  />
                </div>
                <span className="text-sm tabular-nums">
                  <span className="font-medium">{r.vendorCount}</span>
                  <span className="text-muted-foreground">
                    {" "}· {r.bookedCount} booked ({formatPercent(r.bookedCount, r.vendorCount)}) ·{" "}
                  </span>
                  <span className="font-medium">{formatMoney(r.totalAgreedValue)}</span>
                </span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function InDemandSection({
  initial,
  categories,
}: {
  initial: InDemandVendorRow[];
  categories: VendorCategoryResponse[];
}) {
  const [rows, setRows] = useState(initial);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const t = useTableControls(rows, {
    search: (r) => `${r.vendorName} ${r.categoryName}`,
    sortOptions: [
      { key: "usage", label: "Times used", get: (r) => r.usageCount },
      { key: "vendor", label: "Vendor", get: (r) => r.vendorName },
      { key: "category", label: "Category", get: (r) => r.categoryName },
      { key: "booked", label: "Booked", get: (r) => r.bookedCount },
      {
        key: "bookedRate",
        label: "Booked %",
        get: (r) => (r.usageCount > 0 ? r.bookedCount / r.usageCount : 0),
      },
      { key: "value", label: "Total value", get: (r) => r.totalAgreedValue },
    ],
    initialSortDir: "desc",
  });

  const apply = () => {
    startTransition(async () => {
      const result = await inDemandReportAction({
        from: from || undefined,
        to: to || undefined,
        categoryId: categoryId || undefined,
      });
      if (result.error) toast(result.error, "error");
      else setRows(result.rows ?? []);
    });
  };

  const exportCsv = () =>
    downloadCsv(
      "in-demand-vendors.csv",
      rowsToCsv(
        ["Vendor", "Category", "Times used", "Booked", "Booked %", "Total value", "In directory"],
        rows.map((r) => [
          r.vendorName, r.categoryName, r.usageCount, r.bookedCount,
          formatPercent(r.bookedCount, r.usageCount), r.totalAgreedValue,
          r.fromDirectory ? "yes" : "no",
        ]),
      ),
    );

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>In-demand vendors</CardTitle>
            <CardDescription>
              Most-used vendors for weddings in a date window — e.g. the busiest venues this season.
            </CardDescription>
          </div>
          {rows.length > 0 && <ExportButton onExport={exportCsv} />}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="rep-from">Wedding date from</Label>
            <Input id="rep-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="rep-to">to</Label>
            <Input id="rep-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="min-w-40 space-y-1.5">
            <Label htmlFor="rep-cat">Category</Label>
            <select
              id="rep-cat"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className={selectClass}
            >
              <option value="">All categories</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <Button onClick={apply} disabled={pending}>
            {pending ? "Running…" : "Apply"}
          </Button>
        </div>

        {rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">No vendors match these filters.</p>
        ) : (
          <>
            <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search vendors…" />
            {t.filteredCount === 0 ? (
              <p className="text-sm text-muted-foreground">No vendors match “{t.query}”.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                      <SortableTh label="Vendor" sortKey="vendor" activeKey={t.sortKey} dir={t.sortDir} onSort={t.setSort} />
                      <SortableTh label="Category" sortKey="category" activeKey={t.sortKey} dir={t.sortDir} onSort={t.setSort} />
                      <SortableTh label="Times used" sortKey="usage" activeKey={t.sortKey} dir={t.sortDir} onSort={t.setSort} align="right" />
                      <SortableTh label="Booked" sortKey="booked" activeKey={t.sortKey} dir={t.sortDir} onSort={t.setSort} align="right" />
                      <SortableTh label="Booked %" sortKey="bookedRate" activeKey={t.sortKey} dir={t.sortDir} onSort={t.setSort} align="right" />
                      <SortableTh label="Total value" sortKey="value" activeKey={t.sortKey} dir={t.sortDir} onSort={t.setSort} align="right" className="pr-0" />
                    </tr>
                  </thead>
                  <tbody>
                    {t.pageItems.map((r, i) => (
                      <tr key={`${r.vendorName}-${r.categoryName}-${i}`} className="border-b border-border/60">
                        <td className="py-2 pr-3">
                          <span className="font-medium">{r.vendorName}</span>
                          {r.fromDirectory && (
                            <Badge variant="secondary" className="ml-2">Directory</Badge>
                          )}
                        </td>
                        <td className="py-2 pr-3 text-muted-foreground">{r.categoryName}</td>
                        <td className="py-2 pr-3 text-right tabular-nums font-medium">{r.usageCount}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{r.bookedCount}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">
                          {formatPercent(r.bookedCount, r.usageCount)}
                        </td>
                        <td className="py-2 text-right tabular-nums">{formatMoney(r.totalAgreedValue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <Pagination {...t} />
          </>
        )}
      </CardContent>
    </Card>
  );
}

function BookingConversionSection({ report }: { report: BookingConversionReport }) {
  const t = useTableControls(report.categories, {
    paginate: false,
    sortOptions: [
      { key: "rate", label: "Rate", get: (r) => r.bookedRate },
      { key: "category", label: "Category", get: (r) => r.categoryName },
      { key: "considered", label: "Considered", get: (r) => r.considered },
      { key: "booked", label: "Booked", get: (r) => r.booked },
    ],
    initialSortDir: "desc",
  });

  const exportCsv = () =>
    downloadCsv(
      "booking-conversion.csv",
      rowsToCsv(
        ["Category", "Considered", "Booked", "Rate %"],
        report.categories.map((r) => [r.categoryName, r.considered, r.booked, r.bookedRate]),
      ),
    );

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Booking conversion</CardTitle>
            <CardDescription>
              How many considered vendors actually get booked ({report.totalBooked}/
              {report.totalConsidered} = {report.overallRate}% overall).
            </CardDescription>
          </div>
          {report.categories.length > 0 && (
            <div className="flex items-center gap-2">
              <SortControl {...t} />
              <ExportButton onExport={exportCsv} />
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {report.categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">No vendors on the platform yet.</p>
        ) : (
          <div className="space-y-2">
            {t.pageItems.map((r) => (
              <div key={r.categoryName} className="grid grid-cols-[8rem_1fr_auto] items-center gap-3">
                <span className="truncate text-sm text-muted-foreground">{r.categoryName}</span>
                <div className="h-2 overflow-hidden rounded-full bg-muted" role="img"
                     aria-label={`${r.categoryName}: ${r.booked} of ${r.considered} booked`}>
                  <div
                    className="h-full rounded-full"
                    style={{ width: `${r.bookedRate}%`, background: "var(--chart-paid)" }}
                  />
                </div>
                <span className="text-sm tabular-nums">
                  <span className="font-medium">{r.bookedRate}%</span>
                  <span className="text-muted-foreground"> ({r.booked}/{r.considered})</span>
                </span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

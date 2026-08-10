"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { SortDir } from "@/lib/use-table-controls";

const controlSelectClass =
  "h-9 rounded-md border border-input bg-card px-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

/** Header row: search / filters on the left, action buttons on the right. */
export function TableToolbar({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-wrap items-center justify-between gap-3", className)}>
      {children}
    </div>
  );
}

/** A "Sort by" dropdown plus an ascending/descending toggle. */
export function SortControl({
  sortOptions,
  sortKey,
  sortDir,
  setSort,
  className,
}: {
  sortOptions: { key: string; label: string }[];
  sortKey: string;
  sortDir: SortDir;
  setSort: (key: string) => void;
  className?: string;
}) {
  if (sortOptions.length === 0) return null;
  return (
    <div className={cn("flex items-center gap-1.5", className)}>
      <span className="hidden text-sm text-muted-foreground sm:inline">Sort</span>
      <select
        aria-label="Sort by"
        value={sortKey}
        onChange={(e) => {
          if (e.target.value !== sortKey) setSort(e.target.value);
        }}
        className={controlSelectClass}
      >
        {sortOptions.map((o) => (
          <option key={o.key} value={o.key}>
            {o.label}
          </option>
        ))}
      </select>
      <Button
        type="button"
        size="icon"
        variant="outline"
        className="h-9 w-9"
        onClick={() => setSort(sortKey)}
        aria-label={sortDir === "asc" ? "Sorted ascending — click for descending" : "Sorted descending — click for ascending"}
        title={sortDir === "asc" ? "Ascending" : "Descending"}
      >
        {sortDir === "asc" ? <ArrowUp /> : <ArrowDown />}
      </Button>
    </div>
  );
}

/** Rows-per-page selector + range indicator + prev/next. Hidden when the list fits one small page. */
export function Pagination({
  page,
  pageCount,
  setPage,
  pageSize,
  setPageSize,
  pageSizeOptions,
  filteredCount,
  className,
}: {
  page: number;
  pageCount: number;
  setPage: (page: number) => void;
  pageSize: number;
  setPageSize: (size: number) => void;
  pageSizeOptions: number[];
  filteredCount: number;
  className?: string;
}) {
  // Keep the bar (and its rows-per-page control) visible for any list longer than the
  // smallest page size, so switching page size never makes the control vanish mid-interaction.
  if (filteredCount <= pageSizeOptions[0]) return null;

  const from = filteredCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, filteredCount);

  return (
    <div
      className={cn(
        "flex flex-wrap items-center justify-between gap-3 pt-1 text-sm text-muted-foreground",
        className,
      )}
    >
      <label className="flex items-center gap-2">
        Rows per page
        <select
          aria-label="Rows per page"
          value={pageSize}
          onChange={(e) => setPageSize(Number(e.target.value))}
          className={controlSelectClass}
        >
          {pageSizeOptions.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
      <div className="flex items-center gap-3">
        <span className="tabular-nums">
          {from}–{to} of {filteredCount}
        </span>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            size="icon"
            variant="outline"
            className="h-9 w-9"
            onClick={() => setPage(page - 1)}
            disabled={page <= 1}
            aria-label="Previous page"
          >
            <ChevronLeft />
          </Button>
          <Button
            type="button"
            size="icon"
            variant="outline"
            className="h-9 w-9"
            onClick={() => setPage(page + 1)}
            disabled={page >= pageCount}
            aria-label="Next page"
          >
            <ChevronRight />
          </Button>
        </div>
      </div>
    </div>
  );
}

/** A clickable, sort-aware column header for real <table> layouts. */
export function SortableTh({
  label,
  sortKey,
  activeKey,
  dir,
  onSort,
  align = "left",
  className,
}: {
  label: string;
  sortKey: string;
  activeKey: string;
  dir: SortDir;
  onSort: (key: string) => void;
  align?: "left" | "right";
  className?: string;
}) {
  const active = activeKey === sortKey;
  return (
    <th className={cn("py-2 pr-3 font-medium", align === "right" && "text-right", className)}>
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={cn(
          "inline-flex items-center gap-1 uppercase tracking-wide transition-colors hover:text-foreground",
          align === "right" && "flex-row-reverse",
          active && "text-foreground",
        )}
      >
        {label}
        {active ? (
          dir === "asc" ? (
            <ArrowUp className="size-3" />
          ) : (
            <ArrowDown className="size-3" />
          )
        ) : (
          <ArrowUpDown className="size-3 opacity-40" />
        )}
      </button>
    </th>
  );
}

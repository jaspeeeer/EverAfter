"use client";

import { useEffect, useMemo, useState } from "react";

export type SortDir = "asc" | "desc";

export type SortOption<T> = {
  /** Stable key used for state + as the <select> value. */
  key: string;
  /** Human label shown in the sort dropdown / column header. */
  label: string;
  /** Extracts the comparable value for a row (string | number | boolean | null). */
  get: (row: T) => string | number | boolean | null | undefined;
};

export type TableControls<T> = {
  query: string;
  setQuery: (value: string) => void;
  hasSearch: boolean;

  sortOptions: SortOption<T>[];
  sortKey: string;
  sortDir: SortDir;
  /** Same key toggles direction; a new key selects it ascending. */
  setSort: (key: string) => void;

  paginate: boolean;
  pageSize: number;
  setPageSize: (size: number) => void;
  pageSizeOptions: number[];

  page: number;
  pageCount: number;
  setPage: (page: number) => void;

  /** Filtered → sorted → (paginated) rows to render. */
  pageItems: T[];
  /** Rows after search/filter, before pagination. */
  filteredCount: number;
  /** Original row count (data.length). */
  rawTotal: number;
};

const DEFAULT_PAGE_SIZES = [10, 25, 50];

/**
 * Compares two extracted sort values. Numbers sort numerically, booleans put `true`
 * first (ascending), strings use locale/numeric-aware compare. null/undefined always
 * sort last, regardless of direction.
 */
function compareValues(
  a: string | number | boolean | null | undefined,
  b: string | number | boolean | null | undefined,
): number {
  const aEmpty = a === null || a === undefined;
  const bEmpty = b === null || b === undefined;
  if (aEmpty && bEmpty) return 0;
  if (aEmpty) return 1; // nulls last
  if (bEmpty) return -1;

  if (typeof a === "number" && typeof b === "number") return a - b;
  if (typeof a === "boolean" && typeof b === "boolean") {
    return a === b ? 0 : a ? -1 : 1; // true first
  }
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: "base" });
}

/**
 * Client-side search + sort + pagination over an in-memory array. Every list in the app
 * receives its full data as a prop, so this keeps all three controls on the client with no
 * backend paging. `search` omitted → no search box; `paginate: false` → sort-only.
 */
export function useTableControls<T>(
  data: T[],
  opts: {
    search?: (row: T) => string;
    sortOptions: SortOption<T>[];
    initialSortDir?: SortDir;
    paginate?: boolean;
    pageSizeOptions?: number[];
    /** Extra dependency (e.g. an external filter chip) that resets the page to 1 when it changes. */
    resetKey?: unknown;
  },
): TableControls<T> {
  const {
    search,
    sortOptions,
    initialSortDir = "asc",
    paginate = true,
    pageSizeOptions = DEFAULT_PAGE_SIZES,
    resetKey,
  } = opts;

  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState(sortOptions[0]?.key ?? "");
  const [sortDir, setSortDir] = useState<SortDir>(initialSortDir);
  const [pageSize, setPageSize] = useState(pageSizeOptions[0] ?? 10);
  const [page, setPage] = useState(1);

  const setSort = (key: string) => {
    if (key === sortKey) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  const q = query.trim().toLowerCase();
  const filtered = useMemo(() => {
    if (!search || !q) return data;
    return data.filter((row) => search(row).toLowerCase().includes(q));
  }, [data, search, q]);

  const sorted = useMemo(() => {
    const option = sortOptions.find((o) => o.key === sortKey);
    if (!option) return filtered;
    const dir = sortDir === "asc" ? 1 : -1;
    return [...filtered].sort((a, b) => compareValues(option.get(a), option.get(b)) * dir);
  }, [filtered, sortOptions, sortKey, sortDir]);

  const filteredCount = sorted.length;
  const pageCount = paginate ? Math.max(1, Math.ceil(filteredCount / pageSize)) : 1;

  // Reset to the first page whenever the result set is re-scoped by the user.
  useEffect(() => {
    setPage(1);
  }, [q, sortKey, sortDir, pageSize, resetKey]);

  // Clamp so a delete/refresh that shrinks the list can't strand an out-of-range page.
  const currentPage = Math.min(page, pageCount);

  const pageItems = useMemo(() => {
    if (!paginate) return sorted;
    const start = (currentPage - 1) * pageSize;
    return sorted.slice(start, start + pageSize);
  }, [sorted, paginate, currentPage, pageSize]);

  return {
    query,
    setQuery,
    hasSearch: Boolean(search),

    sortOptions,
    sortKey,
    sortDir,
    setSort,

    paginate,
    pageSize,
    setPageSize,
    pageSizeOptions,

    page: currentPage,
    pageCount,
    setPage,

    pageItems,
    filteredCount,
    rawTotal: data.length,
  };
}

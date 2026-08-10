"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ListChecks, Receipt, Search, Store, Users } from "lucide-react";
import { cn } from "@/lib/utils";
import type { SearchIndexItem, SearchItemType } from "@/app/api/projects/[projectId]/search-index/route";

const MAX_PER_GROUP = 5;

const GROUP_META: Record<SearchItemType, { label: string; Icon: React.ComponentType<{ className?: string }> }> = {
  guest: { label: "Guests", Icon: Users },
  vendor: { label: "Vendors", Icon: Store },
  task: { label: "Tasks", Icon: ListChecks },
  expense: { label: "Expenses", Icon: Receipt },
};

const GROUP_ORDER: SearchItemType[] = ["guest", "vendor", "task", "expense"];

function matches(item: SearchIndexItem, query: string): boolean {
  const haystack = `${item.label} ${item.sublabel ?? ""}`.toLowerCase();
  return haystack.includes(query);
}

export function ProjectSearch({ projectId }: { projectId: string }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [items, setItems] = useState<SearchIndexItem[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const router = useRouter();

  function close() {
    setOpen(false);
    setQuery("");
  }

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  // Close on outside click — same pattern as NotificationBell. Uses the full `close()` (not just
  // setOpen(false)) so every dismissal path — outside click, Escape, selecting a result — behaves
  // identically and doesn't leave a stale query for a later reopen to inherit.
  useEffect(() => {
    if (!open) return;
    function onDoc(event: MouseEvent) {
      if (!rootRef.current) return;
      if (!rootRef.current.contains(event.target as Node)) close();
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  const groups = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q || !items) return [];
    return GROUP_ORDER.map((type) => {
      const all = items.filter((i) => i.type === type && matches(i, q));
      return { type, all, shown: all.slice(0, MAX_PER_GROUP) };
    }).filter((g) => g.all.length > 0);
  }, [items, query]);

  // Flat, display-order list of the currently visible (capped) items, for arrow-key navigation.
  const flat = useMemo(() => groups.flatMap((g) => g.shown), [groups]);

  function loadIndexIfNeeded() {
    if (items !== null || loading) return;
    setLoading(true);
    fetch(`/api/projects/${projectId}/search-index`, { cache: "no-store" })
      .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
      .then((data: SearchIndexItem[]) => setItems(data))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }

  function toggleOpen() {
    setOpen((o) => {
      const next = !o;
      if (next) loadIndexIfNeeded();
      return next;
    });
  }

  function onQueryChange(value: string) {
    setQuery(value);
    // The filtered set is about to change — reset here (a direct response to the user's own
    // keystroke) rather than via an effect watching the derived `flat` array.
    setActiveIndex(0);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      close();
      return;
    }
    if (flat.length === 0) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => (i + 1) % flat.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => (i - 1 + flat.length) % flat.length);
    } else if (e.key === "Enter" && activeIndex >= 0) {
      e.preventDefault();
      const item = flat[activeIndex];
      close();
      router.push(item.href);
    }
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label="Search this project"
        aria-expanded={open}
        onClick={toggleOpen}
        className="inline-flex size-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Search className="size-4" />
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-lg border border-border bg-card shadow-xl">
          <div className="border-b border-border p-2">
            <input
              ref={inputRef}
              type="search"
              value={query}
              onChange={(e) => onQueryChange(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder="Search guests, vendors, tasks, expenses…"
              className="w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>

          <div className="max-h-96 overflow-y-auto">
            {loading ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">Loading…</p>
            ) : !query.trim() ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">
                Type to search across this wedding.
              </p>
            ) : groups.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">No matches.</p>
            ) : (
              <div className="divide-y divide-border">
                {groups.map((group) => {
                  const meta = GROUP_META[group.type];
                  return (
                    <div key={group.type} className="py-1">
                      <div className="flex items-center gap-1.5 px-4 py-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                        <meta.Icon className="size-3.5" />
                        {meta.label}
                      </div>
                      <ul>
                        {group.shown.map((item) => {
                          const flatIndex = flat.indexOf(item);
                          return (
                            <li key={item.id}>
                              <Link
                                href={item.href}
                                onClick={close}
                                className={cn(
                                  "flex flex-col px-4 py-2 text-sm transition-colors hover:bg-muted",
                                  flatIndex === activeIndex && "bg-muted",
                                )}
                              >
                                <span className="truncate font-medium text-card-foreground">
                                  {item.label}
                                </span>
                                {item.sublabel && (
                                  <span className="truncate text-xs text-muted-foreground">
                                    {item.sublabel}
                                  </span>
                                )}
                              </Link>
                            </li>
                          );
                        })}
                      </ul>
                      {group.all.length > MAX_PER_GROUP && (
                        <p className="px-4 py-1 text-xs text-muted-foreground">
                          +{group.all.length - MAX_PER_GROUP} more — refine your search
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

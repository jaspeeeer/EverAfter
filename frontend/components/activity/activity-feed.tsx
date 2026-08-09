"use client";

import { useMemo, useState } from "react";
import {
  CalendarClock,
  Ellipsis,
  FileText,
  ListChecks,
  Mail,
  Paperclip,
  Receipt,
  Store,
  UserCircle,
  Users,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type {
  ActivityAction,
  ActivityEntityType,
  ActivityLogResponse,
} from "@/lib/types";

interface Props {
  entries: ActivityLogResponse[];
}

const ENTITY_META: Record<
  ActivityEntityType,
  { label: string; Icon: React.ComponentType<{ className?: string }> }
> = {
  PROJECT: { label: "Project", Icon: FileText },
  TASK: { label: "Task", Icon: ListChecks },
  VENDOR: { label: "Vendor", Icon: Store },
  VENDOR_PAYMENT: { label: "Payment", Icon: Receipt },
  EXPENSE: { label: "Expense", Icon: Receipt },
  GUEST: { label: "Guest", Icon: Users },
  INVITATION: { label: "Invite", Icon: Mail },
  TIMELINE_EVENT: { label: "Timeline", Icon: CalendarClock },
  ATTACHMENT: { label: "File", Icon: Paperclip },
};

const ACTION_TONE: Record<ActivityAction, string> = {
  CREATE: "text-success",
  UPDATE: "text-primary",
  DELETE: "text-destructive",
};

function initials(email: string | null): string {
  if (!email) return "•";
  const local = email.split("@")[0] ?? "";
  const parts = local.split(/[._-]+/).filter(Boolean);
  if (parts.length === 0) return local.slice(0, 2).toUpperCase() || "•";
  return (parts[0][0] + (parts[1]?.[0] ?? "")).toUpperCase();
}

function relative(iso: string): string {
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diffSec = Math.max(1, Math.floor((now - then) / 1000));
  if (diffSec < 60) return `${diffSec}s ago`;
  const min = Math.floor(diffSec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const day = Math.floor(hr / 24);
  if (day < 30) return `${day}d ago`;
  return new Date(iso).toLocaleDateString();
}

function dayKey(iso: string): string {
  const d = new Date(iso);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).toISOString();
}

function dayLabel(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const isToday =
    d.getFullYear() === today.getFullYear() &&
    d.getMonth() === today.getMonth() &&
    d.getDate() === today.getDate();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  const isYesterday =
    d.getFullYear() === yesterday.getFullYear() &&
    d.getMonth() === yesterday.getMonth() &&
    d.getDate() === yesterday.getDate();
  if (isToday) return "Today";
  if (isYesterday) return "Yesterday";
  return d.toLocaleDateString(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric",
    year:
      d.getFullYear() === today.getFullYear() ? undefined : "numeric",
  });
}

export function ActivityFeed({ entries }: Props) {
  const [entityFilter, setEntityFilter] = useState<ActivityEntityType | "">("");
  const [actorFilter, setActorFilter] = useState<string>("");

  const actors = useMemo(() => {
    const seen = new Map<string, string>();
    for (const e of entries) {
      if (e.actorEmail && !seen.has(e.actorEmail)) seen.set(e.actorEmail, e.actorEmail);
    }
    return Array.from(seen.values());
  }, [entries]);

  const filtered = useMemo(
    () =>
      entries.filter(
        (e) =>
          (entityFilter === "" || e.entityType === entityFilter) &&
          (actorFilter === "" || e.actorEmail === actorFilter),
      ),
    [entries, entityFilter, actorFilter],
  );

  const grouped = useMemo(() => {
    const groups: { key: string; label: string; rows: ActivityLogResponse[] }[] = [];
    let currentKey: string | null = null;
    let currentGroup: (typeof groups)[number] | null = null;
    for (const e of filtered) {
      const key = dayKey(e.createdAt);
      if (key !== currentKey) {
        currentGroup = { key, label: dayLabel(e.createdAt), rows: [] };
        groups.push(currentGroup);
        currentKey = key;
      }
      currentGroup!.rows.push(e);
    }
    return groups;
  }, [filtered]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label="Filter by kind"
          value={entityFilter}
          onChange={(e) => setEntityFilter(e.target.value as ActivityEntityType | "")}
          className="h-9 rounded-md border border-input bg-card px-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <option value="">All kinds</option>
          {Object.entries(ENTITY_META).map(([value, meta]) => (
            <option key={value} value={value}>
              {meta.label}
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by actor"
          value={actorFilter}
          onChange={(e) => setActorFilter(e.target.value)}
          className="h-9 rounded-md border border-input bg-card px-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <option value="">Anyone</option>
          {actors.map((email) => (
            <option key={email} value={email}>
              {email}
            </option>
          ))}
        </select>
        <p className="ml-auto text-xs text-muted-foreground">
          {filtered.length} {filtered.length === 1 ? "entry" : "entries"}
        </p>
      </div>

      {filtered.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          No activity yet.
        </p>
      ) : (
        <div className="space-y-6">
          {grouped.map((g) => (
            <section key={g.key}>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {g.label}
              </h3>
              <ol className="divide-y divide-border rounded-lg border border-border bg-card">
                {g.rows.map((row) => {
                  const meta = ENTITY_META[row.entityType] ?? {
                    label: row.entityType,
                    Icon: Ellipsis,
                  };
                  const Icon = meta.Icon;
                  return (
                    <li key={row.id} className="flex items-start gap-3 p-3">
                      <span
                        className="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-medium text-muted-foreground"
                        aria-hidden
                      >
                        {row.actorEmail ? (
                          initials(row.actorEmail)
                        ) : (
                          <UserCircle className="size-5" />
                        )}
                      </span>
                      <span className="flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <Icon
                            className={cn("size-4", ACTION_TONE[row.action])}
                            aria-hidden
                          />
                          <span className="text-sm text-card-foreground">{row.summary}</span>
                        </span>
                        <span className="mt-0.5 block text-xs text-muted-foreground">
                          {row.actorEmail ?? "System"} · {meta.label} · {relative(row.createdAt)}
                        </span>
                      </span>
                    </li>
                  );
                })}
              </ol>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

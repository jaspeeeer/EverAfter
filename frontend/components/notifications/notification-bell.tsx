"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useTransition,
} from "react";
import { Bell, Check, CheckCheck } from "lucide-react";
import { cn } from "@/lib/utils";
import type { NotificationResponse } from "@/lib/types";
import {
  markAllNotificationsReadAction,
  markNotificationReadAction,
} from "@/app/actions/notifications";

interface NotificationBellProps {
  initial: NotificationResponse[];
  initialUnreadCount: number;
}

function timeAgo(iso: string): string {
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diffSec = Math.max(1, Math.floor((now - then) / 1000));
  if (diffSec < 60) return `${diffSec}s ago`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 30) return `${diffDay}d ago`;
  return new Date(iso).toLocaleDateString();
}

export function NotificationBell({
  initial,
  initialUnreadCount,
}: NotificationBellProps) {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationResponse[]>(initial);
  const [unread, setUnread] = useState<number>(initialUnreadCount);
  const [isPending, startTransition] = useTransition();
  const rootRef = useRef<HTMLDivElement>(null);
  const router = useRouter();

  // Close on outside click.
  useEffect(() => {
    if (!open) return;
    function onDoc(event: MouseEvent) {
      if (!rootRef.current) return;
      if (!rootRef.current.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  const refresh = useCallback(async () => {
    try {
      const [list, count] = await Promise.all([
        fetch("/api/notifications?limit=20", { cache: "no-store" }).then(
          (r) => (r.ok ? r.json() : Promise.reject(r.status)),
        ),
        fetch("/api/notifications/unread-count", { cache: "no-store" }).then(
          (r) => (r.ok ? r.json() : Promise.reject(r.status)),
        ),
      ]);
      setItems(list as NotificationResponse[]);
      setUnread((count as { count: number }).count);
    } catch {
      // silent — server actions still keep the read-state true
    }
  }, []);

  // Poll only on window focus. No websocket in MVP.
  useEffect(() => {
    function onFocus() {
      void refresh();
    }
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [refresh]);

  function handleClick(item: NotificationResponse) {
    if (!item.readAt) {
      // Optimistic
      setItems((prev) =>
        prev.map((n) =>
          n.id === item.id ? { ...n, readAt: new Date().toISOString() } : n,
        ),
      );
      setUnread((prev) => Math.max(0, prev - 1));
      startTransition(async () => {
        await markNotificationReadAction(item.id);
      });
    }
    setOpen(false);
    if (item.linkPath) router.push(item.linkPath);
  }

  function handleMarkAll() {
    if (unread === 0) return;
    // Optimistic
    const now = new Date().toISOString();
    setItems((prev) => prev.map((n) => (n.readAt ? n : { ...n, readAt: now })));
    setUnread(0);
    startTransition(async () => {
      await markAllNotificationsReadAction();
    });
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label="Notifications"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="relative inline-flex size-9 items-center justify-center rounded-md text-foreground transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Bell className="size-4" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 inline-flex min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold leading-4 text-primary-foreground">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-lg border border-border bg-card shadow-xl">
          <div className="flex items-center justify-between border-b border-border px-4 py-2">
            <p className="text-sm font-semibold">Notifications</p>
            <button
              type="button"
              onClick={handleMarkAll}
              disabled={unread === 0 || isPending}
              className="inline-flex items-center gap-1 text-xs text-muted-foreground transition-colors hover:text-foreground disabled:opacity-40"
            >
              <CheckCheck className="size-3" /> Mark all read
            </button>
          </div>

          <div className="max-h-96 overflow-y-auto">
            {items.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">
                You&apos;re all caught up.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {items.map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => handleClick(item)}
                      className={cn(
                        "flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-muted",
                        !item.readAt && "bg-primary/5",
                      )}
                    >
                      <span
                        className={cn(
                          "mt-1.5 inline-block size-2 shrink-0 rounded-full",
                          item.readAt ? "bg-transparent" : "bg-primary",
                        )}
                        aria-hidden
                      />
                      <span className="flex-1">
                        <span className="block text-sm font-medium text-card-foreground">
                          {item.title}
                        </span>
                        <span className="mt-0.5 block text-xs text-muted-foreground">
                          {item.body}
                        </span>
                        <span className="mt-1 block text-[11px] text-muted-foreground">
                          {timeAgo(item.createdAt)}
                        </span>
                      </span>
                      {item.readAt && (
                        <Check
                          className="mt-1 size-3 shrink-0 text-muted-foreground/60"
                          aria-hidden
                        />
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="border-t border-border px-4 py-2 text-right">
            <Link
              href="/settings/notifications"
              onClick={() => setOpen(false)}
              className="text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              Notification settings
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

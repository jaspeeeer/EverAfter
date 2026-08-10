"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import {
  DndContext,
  PointerSensor,
  useDraggable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type Modifier,
} from "@dnd-kit/core";
import { CalendarClock, MapPin, Pencil, Plus, Sparkles, Store, Trash2 } from "lucide-react";
import {
  applyTypicalDayAction,
  deleteTimelineEventAction,
  saveTimelineEventAction,
  updateTimelineEventAction,
} from "@/app/actions/timeline";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/utils";
import { formatTime } from "@/lib/format";
import { orderVendorsForPicker } from "@/lib/vendor-tree";
import type { TimelineEventResponse, VendorResponse } from "@/lib/types";

/** 1px per minute → 60px per hour, the scale the whole grid (and the drag math) uses. */
const PX_PER_MIN = 1;
/** Drag snaps to this many minutes. */
const SNAP_MIN = 15;
/** Times before this hour count as "after midnight" and render at the bottom of the day. */
const EARLY_MORNING_CUTOFF_MIN = 4 * 60;

// --- time helpers -----------------------------------------------------------

/** "HH:mm[:ss]" → minutes from the day's logical start (early morning wraps past 24h). */
function wrappedMinutes(time: string): number {
  const [h, m] = time.split(":").map(Number);
  const minutes = h * 60 + m;
  return minutes < EARLY_MORNING_CUTOFF_MIN ? minutes + 24 * 60 : minutes;
}

/** Wrapped minutes → "HH:mm" wall-clock time. */
function toClock(wrapped: number): string {
  const minutes = ((wrapped % (24 * 60)) + 24 * 60) % (24 * 60);
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

interface Positioned {
  event: TimelineEventResponse;
  startMin: number;
  endMin: number;
  col: number;
  cols: number;
}

/**
 * Google-Calendar-style layout: sort by start, group overlapping events into clusters, and
 * greedily assign side-by-side columns within each cluster.
 */
function layoutDay(events: TimelineEventResponse[]): Positioned[] {
  const items: Positioned[] = events
    .map((event) => {
      const startMin = wrappedMinutes(event.startTime);
      let endMin = event.endTime ? wrappedMinutes(event.endTime) : startMin + 60;
      if (endMin <= startMin) endMin = startMin + 60;
      return { event, startMin, endMin, col: 0, cols: 1 };
    })
    .sort((a, b) => a.startMin - b.startMin || a.endMin - b.endMin);

  const clusters: Positioned[][] = [];
  let cluster: Positioned[] = [];
  let clusterEnd = -1;
  for (const item of items) {
    if (cluster.length > 0 && item.startMin >= clusterEnd) {
      clusters.push(cluster);
      cluster = [];
      clusterEnd = -1;
    }
    cluster.push(item);
    clusterEnd = Math.max(clusterEnd, item.endMin);
  }
  if (cluster.length > 0) clusters.push(cluster);

  for (const c of clusters) {
    const columnEnds: number[] = [];
    for (const item of c) {
      let placed = false;
      for (let i = 0; i < columnEnds.length; i++) {
        if (item.startMin >= columnEnds[i]) {
          item.col = i;
          columnEnds[i] = item.endMin;
          placed = true;
          break;
        }
      }
      if (!placed) {
        item.col = columnEnds.length;
        columnEnds.push(item.endMin);
      }
    }
    for (const item of c) item.cols = columnEnds.length;
  }
  return items;
}

const verticalOnly: Modifier = ({ transform }) => ({ ...transform, x: 0 });

// --- main view ---------------------------------------------------------------

export function TimelineView({
  projectId,
  events,
  vendors,
  canEdit,
}: {
  projectId: string;
  events: TimelineEventResponse[];
  vendors: VendorResponse[];
  canEdit: boolean;
}) {
  const [detailId, setDetailId] = useState<string | null>(null);
  const [form, setForm] = useState<{ open: boolean; event: TimelineEventResponse | null }>({
    open: false,
    event: null,
  });
  // Optimistic time overrides while a drag's server action is in flight.
  const [overrides, setOverrides] = useState<
    Record<string, { startTime: string; endTime: string | null }>
  >({});
  const [, startTransition] = useTransition();
  const { toast } = useToast();

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  );

  const effective = events.map((e) =>
    overrides[e.id] ? { ...e, ...overrides[e.id] } : e,
  );
  const positioned = layoutDay(effective);

  // Grid range: hour-aligned, defaults 6:00 → 23:00, stretched by actual events.
  const rangeStart = Math.min(
    6 * 60,
    ...positioned.map((p) => Math.floor(p.startMin / 60) * 60),
  );
  const rangeEnd = Math.max(
    23 * 60,
    ...positioned.map((p) => Math.ceil(p.endMin / 60) * 60),
  );
  const hours: number[] = [];
  for (let m = rangeStart; m <= rangeEnd; m += 60) hours.push(m);

  const detail = effective.find((e) => e.id === detailId) ?? null;

  const onDragEnd = (dragEvent: DragEndEvent) => {
    const moved = effective.find((e) => e.id === dragEvent.active.id);
    if (!moved) return;
    const deltaMin = Math.round(dragEvent.delta.y / PX_PER_MIN / SNAP_MIN) * SNAP_MIN;
    if (deltaMin === 0) return;

    const newStart = toClock(wrappedMinutes(moved.startTime) + deltaMin);
    const newEnd = moved.endTime
      ? toClock(wrappedMinutes(moved.endTime) + deltaMin)
      : null;

    setOverrides((prev) => ({
      ...prev,
      [moved.id]: { startTime: newStart, endTime: newEnd },
    }));
    startTransition(async () => {
      try {
        await updateTimelineEventAction(projectId, moved.id, {
          title: moved.title,
          description: moved.description,
          location: moved.location,
          startTime: newStart,
          endTime: newEnd,
          vendorIds: moved.vendors.map((v) => v.id),
        });
        toast(`Moved to ${formatTime(newStart)}`);
      } catch {
        setOverrides((prev) => {
          const next = { ...prev };
          delete next[moved.id];
          return next;
        });
        toast("Could not move the event", "error");
      }
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          {canEdit
            ? "The wedding-day run sheet. Drag a block to reschedule it; click one to see the suppliers involved."
            : "Your wedding-day schedule. Click an event to see the suppliers involved."}
        </p>
        {canEdit && (
          <Button size="sm" onClick={() => setForm({ open: true, event: null })}>
            <Plus />
            Add event
          </Button>
        )}
      </div>

      {events.length === 0 ? (
        <EmptyState projectId={projectId} canEdit={canEdit} />
      ) : (
        <DndContext sensors={sensors} modifiers={[verticalOnly]} onDragEnd={onDragEnd}>
          <div className="flex rounded-xl border border-border bg-card p-4">
            {/* Hour gutter */}
            <div
              className="relative w-16 shrink-0 select-none"
              style={{ height: (rangeEnd - rangeStart) * PX_PER_MIN + 20 }}
            >
              {hours.map((m) => (
                <span
                  key={m}
                  className="absolute right-3 -translate-y-1/2 text-xs tabular-nums text-muted-foreground"
                  style={{ top: (m - rangeStart) * PX_PER_MIN + 10 }}
                >
                  {formatTime(toClock(m))}
                </span>
              ))}
            </div>

            {/* Day grid */}
            <div
              className="relative flex-1"
              style={{ height: (rangeEnd - rangeStart) * PX_PER_MIN + 20 }}
            >
              {hours.map((m) => (
                <div
                  key={m}
                  aria-hidden
                  className="absolute inset-x-0 border-t border-border"
                  style={{ top: (m - rangeStart) * PX_PER_MIN + 10 }}
                />
              ))}
              {hours.slice(0, -1).map((m) => (
                <div
                  key={`half-${m}`}
                  aria-hidden
                  className="absolute inset-x-0 border-t border-border/40"
                  style={{ top: (m + 30 - rangeStart) * PX_PER_MIN + 10 }}
                />
              ))}

              {positioned.map((item) => (
                <EventBlock
                  key={item.event.id}
                  item={item}
                  rangeStart={rangeStart}
                  canEdit={canEdit}
                  onOpen={() => setDetailId(item.event.id)}
                />
              ))}
            </div>
          </div>
        </DndContext>
      )}

      {/* Slot detail: the suppliers involved */}
      <EventDetailModal
        event={detail}
        projectId={projectId}
        canEdit={canEdit}
        onClose={() => setDetailId(null)}
        onEdit={() => {
          if (detail) {
            setDetailId(null);
            setForm({ open: true, event: detail });
          }
        }}
      />

      {canEdit && (
        <EventFormModal
          key={form.event?.id ?? "new"}
          projectId={projectId}
          event={form.event}
          vendors={vendors}
          open={form.open}
          onClose={() => setForm((f) => ({ ...f, open: false }))}
        />
      )}
    </div>
  );
}

function EmptyState({ projectId, canEdit }: { projectId: string; canEdit: boolean }) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const quickStart = () => {
    startTransition(async () => {
      const result = await applyTypicalDayAction(projectId);
      if (result.error) toast(result.error, "error");
      else toast(`Added ${result.count} events — adjust the times to your day`);
    });
  };

  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border py-16 text-center">
      <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <CalendarClock className="size-6" />
      </div>
      <h2 className="text-lg font-semibold">No schedule yet</h2>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">
        {canEdit
          ? "Map the wedding day hour by hour — from the makeup call to the after-party."
          : "Your planner hasn't mapped the day yet."}
      </p>
      {canEdit && (
        <Button className="mt-5" onClick={quickStart} disabled={pending}>
          <Sparkles />
          {pending ? "Adding…" : "Add a typical day"}
        </Button>
      )}
    </div>
  );
}

// --- event block --------------------------------------------------------------

function EventBlock({
  item,
  rangeStart,
  canEdit,
  onOpen,
}: {
  item: Positioned;
  rangeStart: number;
  canEdit: boolean;
  onOpen: () => void;
}) {
  const { event } = item;
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: event.id,
    disabled: !canEdit,
  });
  // A completed drag also fires a click on pointer-up; swallow that one.
  const justDragged = useRef(false);
  useEffect(() => {
    if (isDragging) justDragged.current = true;
  }, [isDragging]);

  const top = (item.startMin - rangeStart) * PX_PER_MIN + 10;
  const height = Math.max((item.endMin - item.startMin) * PX_PER_MIN, 36);
  const widthPct = 100 / item.cols;

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      role="button"
      tabIndex={0}
      aria-label={`${event.title} at ${formatTime(event.startTime)}`}
      onClick={() => {
        if (justDragged.current) {
          justDragged.current = false;
          return;
        }
        onOpen();
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen();
        }
      }}
      className={cn(
        "absolute overflow-hidden rounded-md border border-primary/30 bg-primary/10 px-2 py-1 text-left shadow-sm outline-none transition-shadow",
        "hover:shadow-md focus-visible:ring-2 focus-visible:ring-ring",
        canEdit && "cursor-grab touch-none active:cursor-grabbing",
        isDragging && "z-20 opacity-80 shadow-lg",
      )}
      style={{
        top,
        height,
        left: `calc(${item.col * widthPct}% + 2px)`,
        width: `calc(${widthPct}% - 4px)`,
        transform: transform ? `translate3d(0, ${transform.y}px, 0)` : undefined,
      }}
    >
      <div className="absolute inset-y-0 left-0 w-1 bg-primary" aria-hidden />
      <div className="pl-2">
        <p className="truncate text-xs font-semibold leading-tight">{event.title}</p>
        <p className="truncate text-[11px] tabular-nums text-muted-foreground">
          {formatTime(event.startTime)}
          {event.endTime ? ` – ${formatTime(event.endTime)}` : ""}
        </p>
        {height >= 56 && event.location && (
          <p className="truncate text-[11px] text-muted-foreground">
            <MapPin className="mr-0.5 inline size-3" />
            {event.location}
          </p>
        )}
        {height >= 72 && event.vendors.length > 0 && (
          <p className="mt-0.5 truncate text-[11px] text-primary">
            <Store className="mr-0.5 inline size-3" />
            {event.vendors.length} supplier{event.vendors.length === 1 ? "" : "s"}
          </p>
        )}
      </div>
    </div>
  );
}

// --- detail modal (click a slot → suppliers) -----------------------------------

function EventDetailModal({
  event,
  projectId,
  canEdit,
  onClose,
  onEdit,
}: {
  event: TimelineEventResponse | null;
  projectId: string;
  canEdit: boolean;
  onClose: () => void;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  if (!event) return null;

  const remove = () => {
    startTransition(async () => {
      await deleteTimelineEventAction(projectId, event.id);
      toast("Event removed");
      onClose();
    });
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={event.title}
      description={`${formatTime(event.startTime)}${
        event.endTime ? ` – ${formatTime(event.endTime)}` : ""
      }${event.location ? ` · ${event.location}` : ""}`}
      footer={
        canEdit ? (
          <>
            <Button variant="ghost" onClick={remove} disabled={pending}>
              <Trash2 />
              Delete
            </Button>
            <Button variant="outline" onClick={onEdit}>
              <Pencil />
              Edit
            </Button>
          </>
        ) : undefined
      }
    >
      <div className="space-y-4">
        {event.description && (
          <p className="text-sm text-muted-foreground">{event.description}</p>
        )}

        <div>
          <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Suppliers involved
          </h4>
          {event.vendors.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No suppliers linked to this slot.
            </p>
          ) : (
            <ul className="divide-y divide-border rounded-lg border border-border">
              {event.vendors.map((vendor) => (
                <li key={vendor.id} className="flex items-center gap-3 px-4 py-2.5">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate text-sm font-medium">{vendor.name}</p>
                      <Badge variant="secondary">{vendor.categoryName}</Badge>
                      {vendor.booked && <Badge variant="success">Booked</Badge>}
                    </div>
                    {(vendor.contactEmail || vendor.phone) && (
                      <p className="truncate text-xs text-muted-foreground">
                        {[vendor.contactEmail, vendor.phone].filter(Boolean).join(" · ")}
                      </p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Modal>
  );
}

// --- create / edit form ---------------------------------------------------------

function EventFormModal({
  projectId,
  event,
  vendors,
  open,
  onClose,
}: {
  projectId: string;
  event: TimelineEventResponse | null;
  vendors: VendorResponse[];
  open: boolean;
  onClose: () => void;
}) {
  const isEdit = event !== null;
  const [state, action, pending] = useActionState(
    saveTimelineEventAction.bind(null, projectId, event?.id ?? null),
    {},
  );
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast(isEdit ? "Event updated" : "Event added");
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const linked = new Set(event?.vendors.map((v) => v.id) ?? []);
  const hhmm = (t: string | null | undefined) => (t ? t.slice(0, 5) : "");

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? "Edit event" : "Add event"}
      description="A slot on the wedding-day timeline."
    >
      <form action={action} className="space-y-4">
        {state.error && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.error}
          </p>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="title">Title</Label>
          <Input
            id="title"
            name="title"
            placeholder="Hair & makeup call"
            defaultValue={event?.title}
            required
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="startTime">Starts</Label>
            <Input
              id="startTime"
              name="startTime"
              type="time"
              defaultValue={hhmm(event?.startTime) || "08:00"}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="endTime">Ends (optional)</Label>
            <Input id="endTime" name="endTime" type="time" defaultValue={hhmm(event?.endTime)} />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="location">Location</Label>
          <Input
            id="location"
            name="location"
            placeholder="Bridal suite"
            defaultValue={event?.location ?? ""}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="description">Notes</Label>
          <Textarea
            id="description"
            name="description"
            placeholder="Who needs to be where…"
            defaultValue={event?.description ?? ""}
          />
        </div>

        <fieldset className="space-y-1.5">
          <legend className="text-sm font-medium">Suppliers involved</legend>
          {vendors.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              No vendors on this project yet — add them on the Vendors tab first.
            </p>
          ) : (
            <div className="max-h-40 space-y-1 overflow-y-auto rounded-lg border border-border p-3">
              {orderVendorsForPicker(vendors).map(({ vendor, indent }) => (
                <label
                  key={vendor.id}
                  className={cn("flex items-center gap-2 text-sm", indent && "pl-5")}
                >
                  <input
                    type="checkbox"
                    name="vendorIds"
                    value={vendor.id}
                    defaultChecked={linked.has(vendor.id)}
                    className="size-4 rounded border-input"
                  />
                  <span className="truncate">{vendor.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {vendor.categoryName}
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : "Add event"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

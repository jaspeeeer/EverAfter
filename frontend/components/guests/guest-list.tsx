"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import { Armchair, Download, Link2, Pencil, Plus, Trash2, Upload, Users, Utensils } from "lucide-react";
import {
  createGuestAction,
  deleteGuestAction,
  editGuestAction,
  importGuestsAction,
  restoreGuestAction,
  updateGuestAction,
} from "@/app/actions/guests";
import { csvToGuests, guestsToCsv } from "@/lib/csv";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import { formatPercent, guestFullName, humanizeEnum } from "@/lib/format";
import type {
  Gender,
  GuestPriority,
  GuestRelationship,
  GuestResponse,
  GuestRoleResponse,
  RelatedTo,
  RsvpStatus,
} from "@/lib/types";

const RSVP_OPTIONS: RsvpStatus[] = ["PENDING", "ATTENDING", "MAYBE", "DECLINED"];

const RSVP_VARIANT: Record<
  RsvpStatus,
  "default" | "success" | "warning" | "destructive" | "accent"
> = {
  PENDING: "warning",
  ATTENDING: "success",
  MAYBE: "accent",
  DECLINED: "destructive",
};

type Filter = "ALL" | RsvpStatus;
const FILTERS: Filter[] = ["ALL", "ATTENDING", "PENDING", "MAYBE", "DECLINED"];

const PRIORITY_OPTIONS: GuestPriority[] = ["A", "B", "C"];
const PRIORITY_VARIANT: Record<GuestPriority, "primary" | "secondary" | "outline"> = {
  A: "primary",
  B: "secondary",
  C: "outline",
};

type PriorityFilter = "ALL" | GuestPriority;
const PRIORITY_FILTERS: PriorityFilter[] = ["ALL", "A", "B", "C"];

const GENDER_OPTIONS: Gender[] = ["MALE", "FEMALE", "OTHER"];

const RELATED_TO_OPTIONS: RelatedTo[] = ["GROOM", "BRIDE"];

const RELATIONSHIP_OPTIONS: GuestRelationship[] = [
  "PARENT",
  "IMMEDIATE_FAMILY",
  "CLOSE_FRIEND",
  "OFFICEMATE",
  "RELATIVE",
  "FAMILY_FRIEND",
  "CHURCHMATE",
  "COMPANION_OF_GUEST",
];

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function GuestList({
  projectId,
  guests,
  roles,
}: {
  projectId: string;
  guests: GuestResponse[];
  roles: GuestRoleResponse[];
}) {
  const [form, setForm] = useState<{ open: boolean; guest: GuestResponse | null }>({
    open: false,
    guest: null,
  });
  const [filter, setFilter] = useState<Filter>("ALL");
  const [priorityFilter, setPriorityFilter] = useState<PriorityFilter>("ALL");
  // "ALL" (default), "NONE" (guests without a role assigned), or a role id.
  const [roleFilter, setRoleFilter] = useState<string>("ALL");
  const [importing, startImport] = useTransition();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  const attendingHeadcount = guests
    .filter((g) => g.rsvpStatus === "ATTENDING")
    .reduce((sum, g) => sum + (g.partySize ?? 1), 0);
  const pending = guests.filter((g) => g.rsvpStatus === "PENDING").length;
  const declined = guests.filter((g) => g.rsvpStatus === "DECLINED").length;

  const exportCsv = () => {
    const blob = new Blob([guestsToCsv(guests)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "guest-list.csv";
    a.click();
    URL.revokeObjectURL(url);
    toast("Guest list exported");
  };

  const importCsv = (file: File) => {
    startImport(async () => {
      const text = await file.text();
      const rows = csvToGuests(text, roles);
      const result = await importGuestsAction(projectId, rows);
      if (result.error) toast(result.error, "error");
      else toast(`Imported ${result.count} guest${result.count === 1 ? "" : "s"}`);
    });
  };

  const byRsvp = filter === "ALL" ? guests : guests.filter((g) => g.rsvpStatus === filter);
  const byPriority =
    priorityFilter === "ALL" ? byRsvp : byRsvp.filter((g) => g.priority === priorityFilter);
  const filtered =
    roleFilter === "ALL"
      ? byPriority
      : roleFilter === "NONE"
        ? byPriority.filter((g) => g.roleId === null)
        : byPriority.filter((g) => g.roleId === roleFilter);

  // Options list: every active role, plus any role currently assigned to a guest but no longer
  // active (an admin deactivating a role should not hide the guests that still carry it).
  const assignedRoleIds = new Set(guests.map((g) => g.roleId).filter((id): id is string => !!id));
  const roleOptions = [
    ...roles.filter((r) => r.active || assignedRoleIds.has(r.id)),
  ].sort((a, b) => a.name.localeCompare(b.name));

  const t = useTableControls(filtered, {
    search: (g) => `${guestFullName(g)} ${g.email ?? ""} ${g.roleName ?? ""}`,
    sortOptions: [
      { key: "name", label: "Name", get: (g) => guestFullName(g) },
      { key: "rsvp", label: "RSVP", get: (g) => g.rsvpStatus },
      { key: "partySize", label: "Party size", get: (g) => g.partySize ?? 1 },
      { key: "table", label: "Table", get: (g) => g.tableNumber },
      { key: "priority", label: "Priority", get: (g) => g.priority },
      { key: "role", label: "Role", get: (g) => g.roleName },
    ],
    resetKey: `${filter}:${priorityFilter}:${roleFilter}`,
  });

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <SummaryStat label="Invites" value={guests.length} />
        <SummaryStat
          label="Attending"
          value={attendingHeadcount}
          accent="success"
          hint={guests.length > 0 ? formatPercent(attendingHeadcount, guests.length) : undefined}
        />
        <SummaryStat
          label="Pending"
          value={pending}
          accent="warning"
          hint={guests.length > 0 ? formatPercent(pending, guests.length) : undefined}
        />
        <SummaryStat
          label="Declined"
          value={declined}
          accent="destructive"
          hint={guests.length > 0 ? formatPercent(declined, guests.length) : undefined}
        />
      </div>

      <DietaryRollup guests={guests} />

      <TableToolbar>
        <div className="flex flex-wrap items-center gap-2">
          <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search guests…" />
          <SortControl {...t} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) importCsv(file);
              e.target.value = "";
            }}
          />
          <Button
            size="sm"
            variant="outline"
            disabled={importing}
            onClick={() => fileInputRef.current?.click()}
          >
            <Upload />
            {importing ? "Importing…" : "Import CSV"}
          </Button>
          <Button size="sm" variant="outline" onClick={exportCsv} disabled={guests.length === 0}>
            <Download />
            Export CSV
          </Button>
          <Button size="sm" onClick={() => setForm({ open: true, guest: null })}>
            <Plus />
            Add guest
          </Button>
        </div>
      </TableToolbar>

      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-xs font-medium text-muted-foreground">RSVP</span>
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={cn(
                "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                filter === f
                  ? "border-primary bg-primary/10 text-primary"
                  : "border-border text-muted-foreground hover:bg-muted",
              )}
            >
              {f === "ALL" ? "All" : humanizeEnum(f)}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-xs font-medium text-muted-foreground">Priority</span>
          {PRIORITY_FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setPriorityFilter(f)}
              className={cn(
                "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                priorityFilter === f
                  ? "border-primary bg-primary/10 text-primary"
                  : "border-border text-muted-foreground hover:bg-muted",
              )}
            >
              {f === "ALL" ? "All" : f}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <label
            htmlFor="role-filter"
            className="mr-1 text-xs font-medium text-muted-foreground"
          >
            Role
          </label>
          <select
            id="role-filter"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className={cn(selectClass, "h-8 w-auto py-1 text-xs")}
          >
            <option value="ALL">All roles</option>
            <option value="NONE">— No role —</option>
            {roleOptions.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
                {!r.active ? " (inactive)" : ""}
              </option>
            ))}
          </select>
        </div>
      </div>

      {guests.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border py-14 text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Users className="size-6" />
          </div>
          <p className="text-sm text-muted-foreground">
            No guests yet. Add invitees and track their RSVPs.
          </p>
        </div>
      ) : t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          No guests match your filters.
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {t.pageItems.map((guest) => (
            <GuestRow
              key={guest.id}
              projectId={projectId}
              guest={guest}
              onEdit={() => setForm({ open: true, guest })}
            />
          ))}
        </Card>
      )}

      <Pagination {...t} />

      <GuestFormModal
        key={form.guest?.id ?? "new"}
        projectId={projectId}
        guest={form.guest}
        roles={roles}
        open={form.open}
        onClose={() => setForm((f) => ({ ...f, open: false }))}
      />
    </div>
  );
}

function SummaryStat({
  label,
  value,
  accent = "default",
  hint,
}: {
  label: string;
  value: number;
  accent?: "default" | "success" | "warning" | "destructive";
  hint?: string;
}) {
  const color =
    accent === "success"
      ? "text-success"
      : accent === "warning"
        ? "text-warning"
        : accent === "destructive"
          ? "text-destructive"
          : "text-foreground";
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className={cn("mt-1 text-2xl font-semibold", color)}>{value}</p>
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}

/**
 * Aggregates dietary notes across attending/pending parties so caterers get one list
 * ("Vegetarian × 3") instead of hunting through rows.
 */
function DietaryRollup({ guests }: { guests: GuestResponse[] }) {
  const counts = new Map<string, number>();
  for (const g of guests) {
    if (!g.dietaryNotes || g.rsvpStatus === "DECLINED") continue;
    const note = g.dietaryNotes.trim();
    counts.set(note, (counts.get(note) ?? 0) + (g.partySize ?? 1));
  }
  if (counts.size === 0) return null;

  return (
    <Card>
      <CardContent className="flex flex-wrap items-center gap-2 p-4">
        <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          <Utensils className="size-3.5" />
          Dietary needs
        </span>
        {[...counts.entries()]
          .sort((a, b) => b[1] - a[1])
          .map(([note, count]) => (
            <Badge key={note} variant="secondary">
              {note} × {count}
            </Badge>
          ))}
      </CardContent>
    </Card>
  );
}

function GuestRow({
  projectId,
  guest,
  onEdit,
}: {
  projectId: string;
  guest: GuestResponse;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const changeStatus = (rsvpStatus: RsvpStatus) => {
    startTransition(async () => {
      await updateGuestAction(projectId, guest.id, {
        firstName: guest.firstName,
        lastName: guest.lastName,
        title: guest.title,
        gender: guest.gender,
        email: guest.email,
        phone: guest.phone,
        rsvpStatus,
        partySize: guest.partySize,
        dietaryNotes: guest.dietaryNotes,
        tableNumber: guest.tableNumber,
        priority: guest.priority,
        relatedTo: guest.relatedTo,
        relationship: guest.relationship,
        roleId: guest.roleId,
      });
      toast(`RSVP set to ${humanizeEnum(rsvpStatus)}`);
    });
  };

  const copyRsvpLink = async () => {
    const link = `${window.location.origin}/rsvp/${guest.rsvpToken}`;
    await navigator.clipboard.writeText(link);
    toast("Invitation link copied — share it with this guest");
  };

  const remove = () => {
    startTransition(async () => {
      const res = await deleteGuestAction(projectId, guest.id);
      if (res.error) {
        toast(res.error, "error");
        return;
      }
      toast("Guest removed", "success", {
        action: {
          label: "Undo",
          onClick: () => {
            startTransition(async () => {
              const restoreRes = await restoreGuestAction(projectId, guest.id);
              toast(restoreRes.error ?? "Guest restored", restoreRes.error ? "error" : "success");
            });
          },
        },
      });
    });
  };

  const meta = [
    (guest.partySize ?? 1) > 1 ? `Party of ${guest.partySize}` : null,
    guest.email,
    guest.relatedTo ? `${humanizeEnum(guest.relatedTo)}'s side` : null,
    guest.relationship ? humanizeEnum(guest.relationship) : null,
    guest.dietaryNotes ? `🍽 ${guest.dietaryNotes}` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className={cn("flex flex-wrap items-center gap-3 p-4", pending && "opacity-50")}>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate font-medium">{guestFullName(guest)}</p>
          <Badge variant={RSVP_VARIANT[guest.rsvpStatus]}>
            {humanizeEnum(guest.rsvpStatus)}
          </Badge>
          {guest.priority && (
            <Badge variant={PRIORITY_VARIANT[guest.priority]}>Priority {guest.priority}</Badge>
          )}
          {guest.roleName && <Badge variant="secondary">{guest.roleName}</Badge>}
          {guest.tableNumber != null && (
            <Badge variant="outline">
              <Armchair className="size-3" />
              Table {guest.tableNumber}
            </Badge>
          )}
        </div>
        {meta && <p className="truncate text-xs text-muted-foreground">{meta}</p>}
      </div>
      <button
        type="button"
        onClick={copyRsvpLink}
        disabled={pending}
        aria-label={`Copy invitation link for ${guestFullName(guest)}`}
        title="Copy invitation link"
        className="text-muted-foreground transition-colors hover:text-primary"
      >
        <Link2 className="size-4" />
      </button>
      <select
        aria-label={`RSVP for ${guestFullName(guest)}`}
        value={guest.rsvpStatus}
        disabled={pending}
        onChange={(e) => changeStatus(e.target.value as RsvpStatus)}
        className="h-9 rounded-md border border-input bg-card px-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {RSVP_OPTIONS.map((s) => (
          <option key={s} value={s}>
            {humanizeEnum(s)}
          </option>
        ))}
      </select>
      <button
        type="button"
        onClick={onEdit}
        disabled={pending}
        aria-label="Edit guest"
        className="text-muted-foreground transition-colors hover:text-foreground"
      >
        <Pencil className="size-4" />
      </button>
      <button
        type="button"
        onClick={remove}
        disabled={pending}
        aria-label="Delete guest"
        className="text-muted-foreground transition-colors hover:text-destructive"
      >
        <Trash2 className="size-4" />
      </button>
    </div>
  );
}

function GuestFormModal({
  projectId,
  guest,
  roles,
  open,
  onClose,
}: {
  projectId: string;
  guest: GuestResponse | null;
  roles: GuestRoleResponse[];
  open: boolean;
  onClose: () => void;
}) {
  const isEdit = guest !== null;
  const action = guest
    ? editGuestAction.bind(null, projectId, guest.id)
    : createGuestAction.bind(null, projectId);

  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast(isEdit ? "Guest updated" : "Guest added");
      formRef.current?.reset();
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  // Keep an existing guest's (possibly deactivated) role selectable.
  const roleOptions =
    guest?.roleId && !roles.some((r) => r.id === guest.roleId)
      ? [{ id: guest.roleId, name: guest.roleName ?? "", slug: "", active: false }, ...roles]
      : roles;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? "Edit guest" : "Add guest"}
      description="Track an invitee and their RSVP."
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
        <div className="grid grid-cols-4 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="title">Title</Label>
            <Input id="title" name="title" placeholder="Mr." defaultValue={guest?.title ?? ""} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="firstName">First name</Label>
            <Input
              id="firstName"
              name="firstName"
              placeholder="Alex"
              defaultValue={guest?.firstName}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="lastName">Last name</Label>
            <Input
              id="lastName"
              name="lastName"
              placeholder="Jamie"
              defaultValue={guest?.lastName ?? ""}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="gender">Gender</Label>
            <select
              id="gender"
              name="gender"
              defaultValue={guest?.gender ?? ""}
              className={selectClass}
            >
              <option value="">— Not set —</option>
              {GENDER_OPTIONS.map((g) => (
                <option key={g} value={g}>
                  {humanizeEnum(g)}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="rsvpStatus">RSVP</Label>
            <select
              id="rsvpStatus"
              name="rsvpStatus"
              defaultValue={guest?.rsvpStatus ?? "PENDING"}
              className={selectClass}
            >
              {RSVP_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {humanizeEnum(s)}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="partySize">Party size</Label>
            <Input
              id="partySize"
              name="partySize"
              type="number"
              min="1"
              placeholder="1"
              defaultValue={guest?.partySize ?? ""}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="tableNumber">Table</Label>
            <Input
              id="tableNumber"
              name="tableNumber"
              type="number"
              min="1"
              placeholder="—"
              defaultValue={guest?.tableNumber ?? ""}
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="priority">Priority</Label>
            <select
              id="priority"
              name="priority"
              defaultValue={guest?.priority ?? ""}
              className={selectClass}
            >
              <option value="">— Not set —</option>
              {PRIORITY_OPTIONS.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="relatedTo">Related to</Label>
            <select
              id="relatedTo"
              name="relatedTo"
              defaultValue={guest?.relatedTo ?? ""}
              className={selectClass}
            >
              <option value="">— Not set —</option>
              {RELATED_TO_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {humanizeEnum(r)}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="relationship">Relationship</Label>
            <select
              id="relationship"
              name="relationship"
              defaultValue={guest?.relationship ?? ""}
              className={selectClass}
            >
              <option value="">— Not set —</option>
              {RELATIONSHIP_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {humanizeEnum(r)}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="roleId">Role</Label>
            <select
              id="roleId"
              name="roleId"
              defaultValue={guest?.roleId ?? ""}
              className={selectClass}
            >
              <option value="">— No role —</option>
              {roleOptions.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              placeholder="guest@example.com"
              defaultValue={guest?.email ?? ""}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="phone">Phone</Label>
            <Input
              id="phone"
              name="phone"
              placeholder="+63 900 000 0000"
              defaultValue={guest?.phone ?? ""}
            />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="dietaryNotes">Dietary notes</Label>
          <Textarea
            id="dietaryNotes"
            name="dietaryNotes"
            placeholder="Vegetarian, nut allergy…"
            defaultValue={guest?.dietaryNotes ?? ""}
          />
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : "Add guest"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

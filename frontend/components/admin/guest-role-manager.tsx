"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import {
  createGuestRoleAction,
  deleteGuestRoleAction,
  renameGuestRoleAction,
} from "@/app/actions/guest-catalog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { useTableControls, type SortDir } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import type { GuestRoleResponse } from "@/lib/types";

/** Matches the search box against a role's own name/slug and (for a sub-role) its parent's name. */
function matchesSearch(r: GuestRoleResponse, query: string): boolean {
  return `${r.name} ${r.slug} ${r.parentName ?? ""}`.toLowerCase().includes(query);
}

function compareByField(a: GuestRoleResponse, b: GuestRoleResponse, sortKey: string): number {
  if (sortKey === "active") {
    if (a.active === b.active) return 0;
    return a.active ? -1 : 1; // true first, matching this app's usual boolean-sort convention
  }
  return a.name
    .toLowerCase()
    .localeCompare(b.name.toLowerCase(), undefined, { numeric: true, sensitivity: "base" });
}

/**
 * Orders roles so a sub-role always renders directly beneath its own parent: rows are grouped
 * by their top-level ancestor (looked up by id, since a sub-role only carries its parent's
 * name, not the parent's other fields); groups and same-group siblings are ordered by the
 * chosen field, direction-aware; but the parent row is always the first row of its own group —
 * that relationship doesn't flip when the direction does, since the parent is the group's
 * header, not a sortable peer of its children.
 */
function compareGuestRoles(
  a: GuestRoleResponse,
  b: GuestRoleResponse,
  sortKey: string,
  sortDir: SortDir,
  rolesById: Map<string, GuestRoleResponse>,
): number {
  const dir = sortDir === "asc" ? 1 : -1;
  const topA = a.parentId ? (rolesById.get(a.parentId) ?? a) : a;
  const topB = b.parentId ? (rolesById.get(b.parentId) ?? b) : b;

  if (topA.id !== topB.id) {
    const cmp = compareByField(topA, topB, sortKey);
    return (cmp !== 0 ? cmp : topA.id.localeCompare(topB.id)) * dir;
  }

  const aIsChild = a.parentId !== null;
  const bIsChild = b.parentId !== null;
  if (aIsChild !== bIsChild) return aIsChild ? 1 : -1;
  if (!aIsChild) return 0;

  return compareByField(a, b, sortKey) * dir;
}

export function GuestRoleManager({
  roles,
}: {
  roles: GuestRoleResponse[];
}) {
  const [editing, setEditing] = useState<GuestRoleResponse | null>(null);
  const [state, action, pending] = useActionState(createGuestRoleAction, {});
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  // Only top-level roles can be picked as a parent — one level of nesting only.
  const topLevelRoles = roles.filter((r) => r.parentId === null);
  const rolesById = new Map(roles.map((r) => [r.id, r]));

  const t = useTableControls(roles, {
    search: (r) => `${r.name} ${r.slug} ${r.parentName ?? ""}`,
    sortOptions: [
      { key: "name", label: "Name", get: (r) => r.name },
      { key: "active", label: "Active", get: (r) => r.active },
    ],
  });

  // The hook's own pageItems sort flatly and don't nest sub-roles under their parent, so the
  // actual displayed rows are computed here instead — reusing the hook only for search/sort/
  // pagination *state* (query, sortKey/sortDir, page/pageSize) and filteredCount.
  const q = t.query.trim().toLowerCase();
  const filtered = q ? roles.filter((r) => matchesSearch(r, q)) : roles;
  const ordered = [...filtered].sort((a, b) =>
    compareGuestRoles(a, b, t.sortKey, t.sortDir, rolesById),
  );
  const displayedRoles = t.paginate
    ? ordered.slice((t.page - 1) * t.pageSize, (t.page - 1) * t.pageSize + t.pageSize)
    : ordered;

  useEffect(() => {
    if (state.ok) {
      toast("Role added");
      formRef.current?.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  return (
    <div className="space-y-4">
      <form ref={formRef} action={action} className="flex flex-wrap items-end gap-3">
        {state.error && (
          <p role="alert" className="w-full rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.error}
          </p>
        )}
        <div className="min-w-56 flex-1 space-y-1.5">
          <Label htmlFor="role-name">New role</Label>
          <Input id="role-name" name="name" placeholder="Emcee" required />
        </div>
        <div className="min-w-56 space-y-1.5">
          <Label htmlFor="role-parent">Sub-role of</Label>
          <select
            id="role-parent"
            name="parentId"
            defaultValue=""
            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none"
          >
            <option value="">(None — top-level)</option>
            {topLevelRoles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            name="entourageEligible"
            className="size-4 rounded border-input"
          />
          Show in entourage picker
        </label>
        <Button type="submit" disabled={pending}>
          <Plus />
          {pending ? "Adding…" : "Add role"}
        </Button>
      </form>

      {roles.length > 0 && (
        <TableToolbar>
          <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search roles…" />
          <SortControl {...t} />
        </TableToolbar>
      )}

      {t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          {roles.length === 0 ? "No roles yet." : "No roles match your search."}
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {displayedRoles.map((role) => (
            <RoleRow key={role.id} role={role} onEdit={() => setEditing(role)} />
          ))}
        </Card>
      )}

      <Pagination {...t} />

      <EditRoleModal
        key={editing?.id ?? "none"}
        role={editing}
        topLevelRoles={topLevelRoles.filter((r) => r.id !== editing?.id)}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

function RoleRow({
  role,
  onEdit,
}: {
  role: GuestRoleResponse;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const remove = () => {
    startTransition(async () => {
      const result = await deleteGuestRoleAction(role.id);
      if (result.error) toast(result.error, "error");
      else toast("Role removed (deactivated if still in use)");
    });
  };

  return (
    <div
      className={cn(
        "flex items-center gap-3 p-4",
        role.parentId && "pl-8",
        pending && "opacity-50",
      )}
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          {role.parentId && <span className="text-muted-foreground">↳</span>}
          <p className="truncate font-medium">{role.name}</p>
          {!role.active && <Badge variant="destructive">Inactive</Badge>}
          {role.entourageEligible && <Badge variant="secondary">Entourage</Badge>}
        </div>
        <p className="text-xs text-muted-foreground">
          {role.slug}
          {role.parentName && ` · Sub-role of ${role.parentName}`}
        </p>
      </div>
      <Button size="sm" variant="outline" onClick={onEdit} disabled={pending}>
        <Pencil />
        Edit
      </Button>
      <Button size="sm" variant="ghost" onClick={remove} disabled={pending}>
        <Trash2 />
        Delete
      </Button>
    </div>
  );
}

function EditRoleModal({
  role,
  topLevelRoles,
  onClose,
}: {
  role: GuestRoleResponse | null;
  topLevelRoles: GuestRoleResponse[];
  onClose: () => void;
}) {
  const [name, setName] = useState(role?.name ?? "");
  const [active, setActive] = useState(role?.active ?? true);
  const [entourageEligible, setEntourageEligible] = useState(role?.entourageEligible ?? false);
  const [parentId, setParentId] = useState(role?.parentId ?? "");
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  if (!role) return null;

  const save = () => {
    startTransition(async () => {
      const result = await renameGuestRoleAction(
        role.id,
        name.trim(),
        active,
        entourageEligible,
        parentId || null,
      );
      if (result.error) toast(result.error, "error");
      else {
        toast("Role updated");
        onClose();
      }
    });
  };

  return (
    <Modal open onClose={onClose} title="Edit role" description="Rename or (de)activate.">
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="edit-role-name">Name</Label>
          <Input
            id="edit-role-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="edit-role-parent">Sub-role of</Label>
          <select
            id="edit-role-parent"
            value={parentId ?? ""}
            onChange={(e) => setParentId(e.target.value)}
            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none"
          >
            <option value="">(None — top-level)</option>
            {topLevelRoles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            className="size-4 rounded border-input"
          />
          Active (shown in guest pickers)
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={entourageEligible}
            onChange={(e) => setEntourageEligible(e.target.checked)}
            className="size-4 rounded border-input"
          />
          Show in entourage picker
        </label>
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} disabled={pending || !name.trim()}>
            {pending ? "Saving…" : "Save changes"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

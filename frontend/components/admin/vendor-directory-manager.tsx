"use client";

import { useActionState, useEffect, useState, useTransition } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import {
  deleteDirectoryEntryAction,
  saveDirectoryEntryAction,
} from "@/app/actions/vendor-catalog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/format";
import type { VendorCategoryResponse, VendorDirectoryResponse } from "@/lib/types";

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function VendorDirectoryManager({
  entries,
  categories,
}: {
  entries: VendorDirectoryResponse[];
  categories: VendorCategoryResponse[];
}) {
  const [form, setForm] = useState<{ open: boolean; entry: VendorDirectoryResponse | null }>({
    open: false,
    entry: null,
  });

  const t = useTableControls(entries, {
    search: (e) => `${e.name} ${e.categoryName} ${e.contactEmail ?? ""} ${e.phone ?? ""}`,
    sortOptions: [
      { key: "name", label: "Name", get: (e) => e.name },
      { key: "category", label: "Category", get: (e) => e.categoryName },
      { key: "typicalPrice", label: "Typical price", get: (e) => e.typicalPrice },
      { key: "active", label: "Active", get: (e) => e.active },
    ],
  });

  return (
    <div className="space-y-4">
      <TableToolbar>
        <div className="flex flex-wrap items-center gap-2">
          {entries.length > 0 && (
            <>
              <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search directory…" />
              <SortControl {...t} />
            </>
          )}
        </div>
        <Button size="sm" onClick={() => setForm({ open: true, entry: null })}>
          <Plus />
          New vendor
        </Button>
      </TableToolbar>

      {entries.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-14 text-center text-sm text-muted-foreground">
          No directory vendors yet. Add suppliers planners can reuse across weddings.
        </p>
      ) : t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          No directory vendors match your search.
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {t.pageItems.map((entry) => (
            <DirectoryRow
              key={entry.id}
              entry={entry}
              onEdit={() => setForm({ open: true, entry })}
            />
          ))}
        </Card>
      )}

      <Pagination {...t} />

      <DirectoryFormModal
        key={form.entry?.id ?? "new"}
        entry={form.entry}
        categories={categories}
        open={form.open}
        onClose={() => setForm((f) => ({ ...f, open: false }))}
      />
    </div>
  );
}

function DirectoryRow({
  entry,
  onEdit,
}: {
  entry: VendorDirectoryResponse;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const remove = () => {
    startTransition(async () => {
      const result = await deleteDirectoryEntryAction(entry.id);
      if (result.error) toast(result.error, "error");
      else toast("Directory vendor removed (deactivated if in use)");
    });
  };

  const contact = [entry.contactEmail, entry.phone].filter(Boolean).join(" · ");

  return (
    <div className={cn("flex items-center gap-3 p-4", pending && "opacity-50")}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-medium">{entry.name}</p>
          <Badge variant="secondary">{entry.categoryName}</Badge>
          {entry.typicalPrice != null && (
            <Badge variant="primary">{formatMoney(entry.typicalPrice)}</Badge>
          )}
          {!entry.active && <Badge variant="destructive">Inactive</Badge>}
        </div>
        <p className="truncate text-xs text-muted-foreground">{contact || "No contact info"}</p>
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

function DirectoryFormModal({
  entry,
  categories,
  open,
  onClose,
}: {
  entry: VendorDirectoryResponse | null;
  categories: VendorCategoryResponse[];
  open: boolean;
  onClose: () => void;
}) {
  const isEdit = entry !== null;
  const [state, action, pending] = useActionState(
    saveDirectoryEntryAction.bind(null, entry?.id ?? null),
    {},
  );
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast(isEdit ? "Directory vendor updated" : "Directory vendor added");
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? "Edit directory vendor" : "New directory vendor"}
      description="A supplier planners can add to any wedding."
    >
      <form action={action} className="space-y-4">
        {state.error && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.error}
          </p>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="dir-name">Vendor name</Label>
          <Input id="dir-name" name="name" defaultValue={entry?.name} placeholder="Blooms & Co" required />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="dir-category">Category</Label>
            <select
              id="dir-category"
              name="categoryId"
              defaultValue={entry?.categoryId ?? categories[0]?.id}
              className={selectClass}
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="dir-price">Typical price</Label>
            <Input
              id="dir-price"
              name="typicalPrice"
              type="number"
              min="0"
              step="0.01"
              placeholder="45000"
              defaultValue={entry?.typicalPrice ?? ""}
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="dir-email">Email</Label>
            <Input id="dir-email" name="contactEmail" type="email" defaultValue={entry?.contactEmail ?? ""} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="dir-phone">Phone</Label>
            <Input id="dir-phone" name="phone" defaultValue={entry?.phone ?? ""} />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="dir-notes">Notes</Label>
          <Textarea id="dir-notes" name="notes" defaultValue={entry?.notes ?? ""} placeholder="Anything planners should know…" />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name="active"
            defaultChecked={entry?.active ?? true}
            className="size-4 rounded border-input"
          />
          Active (shown to planners)
        </label>
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : "Add vendor"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

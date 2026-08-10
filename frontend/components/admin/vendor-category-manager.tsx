"use client";

import { useActionState, useEffect, useRef, useState, useTransition } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import {
  createVendorCategoryAction,
  deleteVendorCategoryAction,
  renameVendorCategoryAction,
} from "@/app/actions/vendor-catalog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import type { VendorCategoryResponse } from "@/lib/types";

export function VendorCategoryManager({
  categories,
}: {
  categories: VendorCategoryResponse[];
}) {
  const [editing, setEditing] = useState<VendorCategoryResponse | null>(null);
  const [state, action, pending] = useActionState(createVendorCategoryAction, {});
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  const t = useTableControls(categories, {
    search: (c) => `${c.name} ${c.slug}`,
    sortOptions: [
      { key: "name", label: "Name", get: (c) => c.name },
      { key: "active", label: "Active", get: (c) => c.active },
    ],
  });

  useEffect(() => {
    if (state.ok) {
      toast("Category added");
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
          <Label htmlFor="cat-name">New category</Label>
          <Input id="cat-name" name="name" placeholder="Officiant" required />
        </div>
        <Button type="submit" disabled={pending}>
          <Plus />
          {pending ? "Adding…" : "Add category"}
        </Button>
      </form>

      {categories.length > 0 && (
        <TableToolbar>
          <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search categories…" />
          <SortControl {...t} />
        </TableToolbar>
      )}

      {t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          {categories.length === 0 ? "No categories yet." : "No categories match your search."}
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {t.pageItems.map((category) => (
            <CategoryRow
              key={category.id}
              category={category}
              onEdit={() => setEditing(category)}
            />
          ))}
        </Card>
      )}

      <Pagination {...t} />

      <EditCategoryModal
        key={editing?.id ?? "none"}
        category={editing}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

function CategoryRow({
  category,
  onEdit,
}: {
  category: VendorCategoryResponse;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const remove = () => {
    startTransition(async () => {
      const result = await deleteVendorCategoryAction(category.id);
      if (result.error) toast(result.error, "error");
      else toast("Category removed (deactivated if still in use)");
    });
  };

  return (
    <div className={cn("flex items-center gap-3 p-4", pending && "opacity-50")}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-medium">{category.name}</p>
          {!category.active && <Badge variant="destructive">Inactive</Badge>}
        </div>
        <p className="text-xs text-muted-foreground">{category.slug}</p>
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

function EditCategoryModal({
  category,
  onClose,
}: {
  category: VendorCategoryResponse | null;
  onClose: () => void;
}) {
  const [name, setName] = useState(category?.name ?? "");
  const [active, setActive] = useState(category?.active ?? true);
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  if (!category) return null;

  const save = () => {
    startTransition(async () => {
      const result = await renameVendorCategoryAction(category.id, name.trim(), active);
      if (result.error) toast(result.error, "error");
      else {
        toast("Category updated");
        onClose();
      }
    });
  };

  return (
    <Modal open onClose={onClose} title="Edit category" description="Rename or (de)activate.">
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="edit-cat-name">Name</Label>
          <Input
            id="edit-cat-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            className="size-4 rounded border-input"
          />
          Active (shown in vendor pickers)
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

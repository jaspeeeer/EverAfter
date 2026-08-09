"use client";

import { useActionState, useEffect, useState, useTransition } from "react";
import { ListChecks, Pencil, Plus, Store, Trash2, X } from "lucide-react";
import {
  deleteChecklistTemplateAction,
  deleteVendorTemplateAction,
  saveChecklistTemplateAction,
  saveVendorTemplateAction,
} from "@/app/actions/templates";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import { SearchInput } from "@/components/ui/search-input";
import { Pagination, SortControl, TableToolbar } from "@/components/ui/table-controls";
import { useToast } from "@/components/ui/toast";
import { useTableControls } from "@/lib/use-table-controls";
import { cn } from "@/lib/utils";
import type {
  ChecklistTemplateResponse,
  VendorCategoryResponse,
  VendorTemplateResponse,
} from "@/lib/types";

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function TemplateManager({
  checklistTemplates,
  vendorTemplates,
  categories,
}: {
  checklistTemplates: ChecklistTemplateResponse[];
  vendorTemplates: VendorTemplateResponse[];
  categories: VendorCategoryResponse[];
}) {
  const [checklistForm, setChecklistForm] = useState<{
    open: boolean;
    template: ChecklistTemplateResponse | null;
  }>({ open: false, template: null });
  const [vendorForm, setVendorForm] = useState<{
    open: boolean;
    template: VendorTemplateResponse | null;
  }>({ open: false, template: null });

  const ct = useTableControls(checklistTemplates, {
    search: (x) => `${x.name} ${x.description ?? ""}`,
    sortOptions: [
      { key: "name", label: "Name", get: (x) => x.name },
      { key: "items", label: "Items", get: (x) => x.items.length },
    ],
  });
  const vt = useTableControls(vendorTemplates, {
    search: (x) => `${x.name} ${x.description ?? ""}`,
    sortOptions: [
      { key: "name", label: "Name", get: (x) => x.name },
      { key: "items", label: "Items", get: (x) => x.items.length },
    ],
  });

  return (
    <div className="space-y-10">
      {/* Checklist templates */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <ListChecks className="size-5 text-primary" />
              Checklist templates
            </h2>
            <p className="text-sm text-muted-foreground">
              Preset task lists planners can apply to a project&apos;s checklist.
            </p>
          </div>
          <Button size="sm" onClick={() => setChecklistForm({ open: true, template: null })}>
            <Plus />
            New template
          </Button>
        </div>

        {checklistTemplates.length > 0 && (
          <TableToolbar>
            <SearchInput value={ct.query} onChange={ct.setQuery} placeholder="Search templates…" />
            <SortControl {...ct} />
          </TableToolbar>
        )}

        {checklistTemplates.length === 0 ? (
          <div className="grid gap-4 md:grid-cols-2">
            <EmptyHint kind="checklist" />
          </div>
        ) : ct.filteredCount === 0 ? (
          <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
            No templates match your search.
          </p>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {ct.pageItems.map((template) => (
              <TemplateCard
                key={template.id}
                name={template.name}
                description={template.description}
                itemCount={template.items.length}
                itemPreview={template.items.slice(0, 4).map((i) =>
                  i.daysBeforeWedding != null
                    ? `${i.title} (${i.daysBeforeWedding}d before)`
                    : i.title,
                )}
                onEdit={() => setChecklistForm({ open: true, template })}
                onDelete={() => deleteChecklistTemplateAction(template.id)}
              />
            ))}
          </div>
        )}

        <Pagination {...ct} />
      </section>

      {/* Vendor templates */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <Store className="size-5 text-primary" />
              Vendor templates
            </h2>
            <p className="text-sm text-muted-foreground">
              Preset supplier slots planners can add to a project&apos;s vendor list.
            </p>
          </div>
          <Button size="sm" onClick={() => setVendorForm({ open: true, template: null })}>
            <Plus />
            New template
          </Button>
        </div>

        {vendorTemplates.length > 0 && (
          <TableToolbar>
            <SearchInput value={vt.query} onChange={vt.setQuery} placeholder="Search templates…" />
            <SortControl {...vt} />
          </TableToolbar>
        )}

        {vendorTemplates.length === 0 ? (
          <div className="grid gap-4 md:grid-cols-2">
            <EmptyHint kind="vendor" />
          </div>
        ) : vt.filteredCount === 0 ? (
          <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
            No templates match your search.
          </p>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {vt.pageItems.map((template) => (
              <TemplateCard
                key={template.id}
                name={template.name}
                description={template.description}
                itemCount={template.items.length}
                itemPreview={template.items
                  .slice(0, 4)
                  .map((i) => `${i.name} · ${i.categoryName}`)}
                onEdit={() => setVendorForm({ open: true, template })}
                onDelete={() => deleteVendorTemplateAction(template.id)}
              />
            ))}
          </div>
        )}

        <Pagination {...vt} />
      </section>

      <ChecklistTemplateModal
        key={`c-${checklistForm.template?.id ?? "new"}`}
        template={checklistForm.template}
        open={checklistForm.open}
        onClose={() => setChecklistForm((f) => ({ ...f, open: false }))}
      />
      <VendorTemplateModal
        key={`v-${vendorForm.template?.id ?? "new"}`}
        template={vendorForm.template}
        categories={categories}
        open={vendorForm.open}
        onClose={() => setVendorForm((f) => ({ ...f, open: false }))}
      />
    </div>
  );
}

function EmptyHint({ kind }: { kind: "checklist" | "vendor" }) {
  return (
    <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground md:col-span-2">
      No {kind} templates yet. Create one to give planners a head start.
    </p>
  );
}

function TemplateCard({
  name,
  description,
  itemCount,
  itemPreview,
  onEdit,
  onDelete,
}: {
  name: string;
  description: string | null;
  itemCount: number;
  itemPreview: string[];
  onEdit: () => void;
  onDelete: () => Promise<void>;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const remove = () => {
    startTransition(async () => {
      await onDelete();
      toast("Template deleted");
    });
  };

  return (
    <Card className={cn(pending && "opacity-50")}>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle className="text-base">{name}</CardTitle>
            {description && <CardDescription>{description}</CardDescription>}
          </div>
          <Badge variant="secondary">{itemCount} items</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <ul className="space-y-1 text-xs text-muted-foreground">
          {itemPreview.map((line) => (
            <li key={line} className="truncate">
              • {line}
            </li>
          ))}
          {itemCount > itemPreview.length && (
            <li>… and {itemCount - itemPreview.length} more</li>
          )}
        </ul>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={onEdit} disabled={pending}>
            <Pencil />
            Edit
          </Button>
          <Button size="sm" variant="ghost" onClick={remove} disabled={pending}>
            <Trash2 />
            Delete
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// --- Checklist editor -------------------------------------------------------

interface ChecklistRow {
  title: string;
  description: string;
  daysBeforeWedding: string; // keep as text for the input; parsed on submit
}

function ChecklistTemplateModal({
  template,
  open,
  onClose,
}: {
  template: ChecklistTemplateResponse | null;
  open: boolean;
  onClose: () => void;
}) {
  const isEdit = template !== null;
  const [rows, setRows] = useState<ChecklistRow[]>(
    template
      ? template.items.map((i) => ({
          title: i.title,
          description: i.description ?? "",
          daysBeforeWedding: i.daysBeforeWedding?.toString() ?? "",
        }))
      : [{ title: "", description: "", daysBeforeWedding: "" }],
  );
  const [state, action, pending] = useActionState(
    saveChecklistTemplateAction.bind(null, template?.id ?? null),
    {},
  );
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast(isEdit ? "Template updated" : "Template created");
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const setRow = (index: number, patch: Partial<ChecklistRow>) =>
    setRows((r) => r.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const itemsJson = JSON.stringify(
    rows.map((r) => ({
      title: r.title,
      description: r.description || null,
      daysBeforeWedding: r.daysBeforeWedding === "" ? null : Number(r.daysBeforeWedding),
    })),
  );

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? "Edit checklist template" : "New checklist template"}
      description="Each task can count back from the wedding date (days before)."
      className="max-w-2xl"
    >
      <form action={action} className="space-y-4">
        {state.error && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.error}
          </p>
        )}
        <input type="hidden" name="items" value={itemsJson} />
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="name">Template name</Label>
            <Input id="name" name="name" defaultValue={template?.name} placeholder="Classic Wedding Checklist" required />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="description">Description</Label>
            <Input id="description" name="description" defaultValue={template?.description ?? ""} placeholder="Optional" />
          </div>
        </div>

        <div className="space-y-2">
          <div className="grid grid-cols-[1fr_8rem_2rem] items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            <span>Task</span>
            <span>Days before</span>
            <span />
          </div>
          <div className="max-h-72 space-y-2 overflow-y-auto pr-1">
            {rows.map((row, index) => (
              <div key={index} className="grid grid-cols-[1fr_8rem_2rem] items-center gap-2">
                <Input
                  aria-label={`Task ${index + 1} title`}
                  value={row.title}
                  placeholder="Book venue"
                  onChange={(e) => setRow(index, { title: e.target.value })}
                />
                <Input
                  aria-label={`Task ${index + 1} days before wedding`}
                  type="number"
                  min="0"
                  value={row.daysBeforeWedding}
                  placeholder="—"
                  onChange={(e) => setRow(index, { daysBeforeWedding: e.target.value })}
                />
                <button
                  type="button"
                  aria-label={`Remove task ${index + 1}`}
                  onClick={() => setRows((r) => r.filter((_, i) => i !== index))}
                  disabled={rows.length === 1}
                  className="text-muted-foreground transition-colors hover:text-destructive disabled:opacity-30"
                >
                  <X className="size-4" />
                </button>
              </div>
            ))}
          </div>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => setRows((r) => [...r, { title: "", description: "", daysBeforeWedding: "" }])}
          >
            <Plus />
            Add task
          </Button>
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : "Create template"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// --- Vendor editor -----------------------------------------------------------

interface VendorRow {
  name: string;
  categoryId: string;
}

function VendorTemplateModal({
  template,
  categories,
  open,
  onClose,
}: {
  template: VendorTemplateResponse | null;
  categories: VendorCategoryResponse[];
  open: boolean;
  onClose: () => void;
}) {
  const isEdit = template !== null;
  const defaultCategoryId = categories[0]?.id ?? "";
  const [rows, setRows] = useState<VendorRow[]>(
    template
      ? template.items.map((i) => ({ name: i.name, categoryId: i.categoryId }))
      : [{ name: "", categoryId: defaultCategoryId }],
  );
  const [state, action, pending] = useActionState(
    saveVendorTemplateAction.bind(null, template?.id ?? null),
    {},
  );
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      toast(isEdit ? "Template updated" : "Template created");
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const setRow = (index: number, patch: Partial<VendorRow>) =>
    setRows((r) => r.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isEdit ? "Edit vendor template" : "New vendor template"}
      description="Preset supplier slots; applying creates them unbooked."
      className="max-w-2xl"
    >
      <form action={action} className="space-y-4">
        {state.error && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {state.error}
          </p>
        )}
        <input type="hidden" name="items" value={JSON.stringify(rows)} />
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="vt-name">Template name</Label>
            <Input id="vt-name" name="name" defaultValue={template?.name} placeholder="Essential Vendors" required />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="vt-description">Description</Label>
            <Input id="vt-description" name="description" defaultValue={template?.description ?? ""} placeholder="Optional" />
          </div>
        </div>

        <div className="max-h-72 space-y-2 overflow-y-auto pr-1">
          {rows.map((row, index) => (
            <div key={index} className="grid grid-cols-[1fr_11rem_2rem] items-center gap-2">
              <Input
                aria-label={`Vendor ${index + 1} name`}
                value={row.name}
                placeholder="Photographer"
                onChange={(e) => setRow(index, { name: e.target.value })}
              />
              <select
                aria-label={`Vendor ${index + 1} category`}
                value={row.categoryId}
                onChange={(e) => setRow(index, { categoryId: e.target.value })}
                className={selectClass}
              >
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
              <button
                type="button"
                aria-label={`Remove vendor ${index + 1}`}
                onClick={() => setRows((r) => r.filter((_, i) => i !== index))}
                disabled={rows.length === 1}
                className="text-muted-foreground transition-colors hover:text-destructive disabled:opacity-30"
              >
                <X className="size-4" />
              </button>
            </div>
          ))}
        </div>
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={() => setRows((r) => [...r, { name: "", categoryId: defaultCategoryId }])}
        >
          <Plus />
          Add vendor slot
        </Button>

        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : "Create template"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

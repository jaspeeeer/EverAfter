"use client";

import { Fragment, useActionState, useEffect, useRef, useState, useTransition } from "react";
import {
  BookUser,
  Boxes,
  Check,
  CornerDownRight,
  LayoutTemplate,
  Paperclip,
  Pencil,
  Plus,
  Receipt,
  Store,
  Trash2,
} from "lucide-react";
import {
  type ActionState,
  addVendorFromDirectoryAction,
  addVendorPaymentAction,
  createVendorAction,
  deleteVendorAction,
  deleteVendorPaymentAction,
  editVendorAction,
  listVendorPaymentsAction,
  markVendorPaymentPaidAction,
  restoreVendorAction,
  updateVendorAction,
} from "@/app/actions/vendors";
import { applyVendorTemplateAction } from "@/app/actions/templates";
import { AttachmentList } from "@/components/attachments/attachment-list";
import {
  ApplyTemplateModal,
  type TemplateOption,
} from "@/components/templates/apply-template-modal";
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
import { formatDate, formatMoney, formatPercent } from "@/lib/format";
import { groupVendorsByParent } from "@/lib/vendor-tree";
import type {
  VendorCategoryResponse,
  VendorDirectoryResponse,
  VendorPaymentResponse,
  VendorResponse,
  VendorTemplateResponse,
} from "@/lib/types";

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

export function VendorList({
  projectId,
  vendors,
  categories,
  templates = [],
  directory = [],
  canManage = false,
}: {
  projectId: string;
  vendors: VendorResponse[];
  categories: VendorCategoryResponse[];
  templates?: VendorTemplateResponse[];
  directory?: VendorDirectoryResponse[];
  canManage?: boolean;
}) {
  const [form, setForm] = useState<{
    open: boolean;
    vendor: VendorResponse | null;
    parentId: string | null;
  }>({ open: false, vendor: null, parentId: null });
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [payFor, setPayFor] = useState<{ open: boolean; vendor: VendorResponse | null }>({
    open: false,
    vendor: null,
  });

  // A "package" is just a top-level vendor with items nested under it — items never page or
  // sort on their own; they always render beneath their package. Item names are folded into the
  // package's search text so searching for a bundled item still surfaces its package.
  const { topLevel, itemsByParent } = groupVendorsByParent(vendors);
  const t = useTableControls(topLevel, {
    search: (v) =>
      `${v.name} ${v.categoryName} ${v.contactEmail ?? ""} ${v.phone ?? ""} ${(
        itemsByParent.get(v.id) ?? []
      )
        .map((item) => item.name)
        .join(" ")}`,
    sortOptions: [
      { key: "name", label: "Name", get: (v) => v.name },
      { key: "category", label: "Category", get: (v) => v.categoryName },
      { key: "agreedPrice", label: "Full amount", get: (v) => v.agreedPrice },
      { key: "amountPaid", label: "Paid", get: (v) => v.amountPaid },
      { key: "booked", label: "Booked", get: (v) => v.booked },
    ],
  });

  return (
    <div className="space-y-4">
      <TableToolbar>
        <div className="flex flex-wrap items-center gap-2">
          <SearchInput value={t.query} onChange={t.setQuery} placeholder="Search vendors…" />
          <SortControl {...t} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canManage && directory.length > 0 && (
            <Button size="sm" variant="outline" onClick={() => setDirectoryOpen(true)}>
              <BookUser />
              Add from directory
            </Button>
          )}
          {canManage && (
            <Button size="sm" variant="outline" onClick={() => setTemplatesOpen(true)}>
              <LayoutTemplate />
              Add from template
            </Button>
          )}
          <Button size="sm" onClick={() => setForm({ open: true, vendor: null, parentId: null })}>
            <Plus />
            Add vendor
          </Button>
        </div>
      </TableToolbar>

      {vendors.length === 0 ? (
        <EmptyState />
      ) : t.filteredCount === 0 ? (
        <p className="rounded-xl border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
          No vendors match “{t.query}”.
        </p>
      ) : (
        <Card className="divide-y divide-border">
          {t.pageItems.map((vendor) => (
            <Fragment key={vendor.id}>
              <VendorRow
                projectId={projectId}
                vendor={vendor}
                hasItems={(itemsByParent.get(vendor.id)?.length ?? 0) > 0}
                onEdit={() => setForm({ open: true, vendor, parentId: vendor.parentId })}
                onPayments={() => setPayFor({ open: true, vendor })}
                onAddItem={() => setForm({ open: true, vendor: null, parentId: vendor.id })}
              />
              {(itemsByParent.get(vendor.id) ?? []).map((item) => (
                <ItemRow
                  key={item.id}
                  projectId={projectId}
                  vendor={item}
                  onEdit={() => setForm({ open: true, vendor: item, parentId: item.parentId })}
                />
              ))}
            </Fragment>
          ))}
        </Card>
      )}

      <Pagination {...t} />

      <VendorFormModal
        key={`vendor-form-${form.vendor?.id ?? "new"}-${form.parentId ?? "none"}`}
        projectId={projectId}
        vendor={form.vendor}
        parentId={form.parentId}
        categories={categories}
        open={form.open}
        canManage={canManage}
        onClose={() => setForm((f) => ({ ...f, open: false }))}
      />

      <PaymentsModal
        key={`payments-${payFor.vendor?.id ?? "none"}`}
        projectId={projectId}
        vendor={payFor.vendor}
        open={payFor.open}
        canManage={canManage}
        onClose={() => setPayFor((f) => ({ ...f, open: false }))}
      />

      {canManage && (
        <ApplyTemplateModal
          open={templatesOpen}
          onClose={() => setTemplatesOpen(false)}
          title="Add vendors from a template"
          description="Creates an unbooked slot for every supplier in the template."
          confirmLabel="Add vendors"
          successNoun="vendor"
          templates={templates.map(
            (t): TemplateOption => ({
              id: t.id,
              name: t.name,
              description: t.description,
              itemCount: t.items.length,
              preview: t.items.map((i) => `${i.name} · ${i.categoryName}`),
            }),
          )}
          onApply={(templateId) => applyVendorTemplateAction(projectId, templateId)}
        />
      )}

      {canManage && directory.length > 0 && (
        <ApplyTemplateModal
          open={directoryOpen}
          onClose={() => setDirectoryOpen(false)}
          title="Add a vendor from the directory"
          description="Copies a supplier from the shared directory into this project."
          confirmLabel="Add vendor"
          successNoun="vendor"
          templates={directory.map(
            (d): TemplateOption => ({
              id: d.id,
              name: d.name,
              description: d.categoryName,
              itemCount: 1,
              preview: [
                [d.contactEmail, d.phone].filter(Boolean).join(" · ") || "No contact",
                d.typicalPrice != null ? `Typical: ${formatMoney(d.typicalPrice)}` : "",
              ].filter(Boolean),
            }),
          )}
          onApply={(directoryId) => addVendorFromDirectoryAction(projectId, directoryId)}
        />
      )}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border py-14 text-center">
      <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <Store className="size-6" />
      </div>
      <p className="text-sm text-muted-foreground">
        No vendors yet. Add florists, caterers, photographers and more.
      </p>
    </div>
  );
}

function VendorRow({
  projectId,
  vendor,
  hasItems,
  onEdit,
  onPayments,
  onAddItem,
}: {
  projectId: string;
  vendor: VendorResponse;
  hasItems: boolean;
  onEdit: () => void;
  onPayments: () => void;
  onAddItem: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const hasPrice = vendor.agreedPrice != null;
  const fullyPaid = hasPrice && vendor.amountPaid >= (vendor.agreedPrice ?? 0);

  const toggleBooked = () => {
    startTransition(async () => {
      await updateVendorAction(projectId, vendor.id, {
        name: vendor.name,
        categoryId: vendor.categoryId,
        contactEmail: vendor.contactEmail,
        phone: vendor.phone,
        booked: !vendor.booked,
        agreedPrice: vendor.agreedPrice,
        parentId: vendor.parentId,
      });
      toast(vendor.booked ? "Marked as not booked" : "Vendor booked");
    });
  };

  const remove = () => {
    startTransition(async () => {
      const res = await deleteVendorAction(projectId, vendor.id);
      if (res.error) {
        toast(res.error, "error");
        return;
      }
      toast("Vendor deleted", "success", {
        action: {
          label: "Undo",
          onClick: () => {
            startTransition(async () => {
              const restoreRes = await restoreVendorAction(projectId, vendor.id);
              toast(restoreRes.error ?? "Vendor restored", restoreRes.error ? "error" : "success");
            });
          },
        },
      });
    });
  };

  const contact = [vendor.contactEmail, vendor.phone].filter(Boolean).join(" · ");

  return (
    <div className={cn("flex items-center gap-3 p-4", pending && "opacity-50")}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-medium">{vendor.name}</p>
          <Badge variant="secondary">{vendor.categoryName}</Badge>
          {hasItems && (
            <Badge variant="accent">
              <Boxes className="size-3" />
              Package
            </Badge>
          )}
          {hasPrice && (
            <Badge variant={fullyPaid ? "success" : "primary"}>
              {formatMoney(vendor.amountPaid)} / {formatMoney(vendor.agreedPrice)} paid (
              {formatPercent(vendor.amountPaid, vendor.agreedPrice ?? 0)})
            </Badge>
          )}
        </div>
        <p className="truncate text-xs text-muted-foreground">
          {contact || "No contact info"}
        </p>
      </div>
      <Button size="sm" variant="outline" onClick={onAddItem} disabled={pending}>
        <Plus />
        Add item
      </Button>
      {hasPrice && (
        <Button size="sm" variant="outline" onClick={onPayments} disabled={pending}>
          <Receipt />
          Payments
        </Button>
      )}
      <button type="button" onClick={toggleBooked} disabled={pending} title="Toggle booked">
        {vendor.booked ? (
          <Badge variant="success">
            <Check className="size-3" />
            Booked
          </Badge>
        ) : (
          <Badge variant="outline">Mark booked</Badge>
        )}
      </button>
      <button
        type="button"
        onClick={onEdit}
        disabled={pending}
        aria-label="Edit vendor"
        className="text-muted-foreground transition-colors hover:text-foreground"
      >
        <Pencil className="size-4" />
      </button>
      <button
        type="button"
        onClick={remove}
        disabled={pending}
        aria-label="Delete vendor"
        className="text-muted-foreground transition-colors hover:text-destructive"
      >
        <Trash2 className="size-4" />
      </button>
    </div>
  );
}

/**
 * A package item nested under a top-level vendor — no price, payments, or booked toggle of its
 * own (those live on the package). Just enough to track who's bundled in and their contact info.
 */
function ItemRow({
  projectId,
  vendor,
  onEdit,
}: {
  projectId: string;
  vendor: VendorResponse;
  onEdit: () => void;
}) {
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const remove = () => {
    startTransition(async () => {
      const res = await deleteVendorAction(projectId, vendor.id);
      if (res.error) {
        toast(res.error, "error");
        return;
      }
      toast("Item removed", "success", {
        action: {
          label: "Undo",
          onClick: () => {
            startTransition(async () => {
              const restoreRes = await restoreVendorAction(projectId, vendor.id);
              toast(restoreRes.error ?? "Item restored", restoreRes.error ? "error" : "success");
            });
          },
        },
      });
    });
  };

  const contact = [vendor.contactEmail, vendor.phone].filter(Boolean).join(" · ");

  return (
    <div className={cn("flex items-center gap-3 py-3 pr-4 pl-10", pending && "opacity-50")}>
      <CornerDownRight aria-hidden className="size-3.5 shrink-0 text-muted-foreground" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-medium">{vendor.name}</p>
          <Badge variant="secondary">{vendor.categoryName}</Badge>
        </div>
        {contact && <p className="truncate text-xs text-muted-foreground">{contact}</p>}
      </div>
      <button
        type="button"
        onClick={onEdit}
        disabled={pending}
        aria-label="Edit item"
        className="text-muted-foreground transition-colors hover:text-foreground"
      >
        <Pencil className="size-4" />
      </button>
      <button
        type="button"
        onClick={remove}
        disabled={pending}
        aria-label="Delete item"
        className="text-muted-foreground transition-colors hover:text-destructive"
      >
        <Trash2 className="size-4" />
      </button>
    </div>
  );
}

function VendorFormModal({
  projectId,
  vendor,
  parentId,
  categories,
  open,
  canManage,
  onClose,
}: {
  projectId: string;
  vendor: VendorResponse | null;
  parentId: string | null;
  categories: VendorCategoryResponse[];
  open: boolean;
  canManage: boolean;
  onClose: () => void;
}) {
  const isEdit = vendor !== null;
  const isChildMode = parentId !== null;
  const action = vendor
    ? editVendorAction.bind(null, projectId, vendor.id)
    : createVendorAction.bind(null, projectId);

  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  useEffect(() => {
    if (state.ok) {
      const noun = isChildMode ? "Item" : "Vendor";
      toast(isEdit ? `${noun} updated` : `${noun} added`);
      formRef.current?.reset();
      onClose();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  // Keep an existing vendor's (possibly deactivated) category selectable.
  const options =
    vendor && !categories.some((c) => c.id === vendor.categoryId)
      ? [
          { id: vendor.categoryId, name: vendor.categoryName, slug: "", active: false },
          ...categories,
        ]
      : categories;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={
        isChildMode
          ? isEdit
            ? "Edit package item"
            : "Add package item"
          : isEdit
            ? "Edit vendor"
            : "Add vendor"
      }
      description={
        isChildMode
          ? "A supplier bundled under this package."
          : "Track a supplier for this wedding."
      }
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
        {parentId && <input type="hidden" name="parentId" value={parentId} />}
        <div className="space-y-1.5">
          <Label htmlFor="name">{isChildMode ? "Item name" : "Vendor name"}</Label>
          <Input id="name" name="name" placeholder="Blooms & Co" defaultValue={vendor?.name} required />
        </div>
        {isChildMode ? (
          <div className="space-y-1.5">
            <Label htmlFor="categoryId">Category</Label>
            <select
              id="categoryId"
              name="categoryId"
              defaultValue={vendor?.categoryId ?? options[0]?.id}
              className={selectClass}
            >
              {options.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="categoryId">Category</Label>
              <select
                id="categoryId"
                name="categoryId"
                defaultValue={vendor?.categoryId ?? options[0]?.id}
                className={selectClass}
              >
                {options.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="agreedPrice">Full amount to be paid</Label>
              <Input
                id="agreedPrice"
                name="agreedPrice"
                type="number"
                min="0"
                step="0.01"
                placeholder="e.g. 120000"
                defaultValue={vendor?.agreedPrice ?? ""}
              />
            </div>
          </div>
        )}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="contactEmail">Email</Label>
            <Input
              id="contactEmail"
              name="contactEmail"
              type="email"
              placeholder="hello@vendor.com"
              defaultValue={vendor?.contactEmail ?? ""}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="phone">Phone</Label>
            <Input
              id="phone"
              name="phone"
              placeholder="+63 900 000 0000"
              defaultValue={vendor?.phone ?? ""}
            />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name="booked"
            defaultChecked={vendor?.booked ?? false}
            className="size-4 rounded border-input"
          />
          Already booked
        </label>
        {isChildMode ? (
          <p className="text-xs text-muted-foreground">
            This item is bundled under the package — it has no price of its own; payments are
            recorded on the package.
          </p>
        ) : (
          <p className="text-xs text-muted-foreground">
            The full amount shows on the Budget tab as a committed expense. Record installments (or a
            full payment) from the vendor&apos;s <span className="font-medium">Payments</span> button.
          </p>
        )}
        <div className="flex justify-end gap-3 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : isEdit ? "Save changes" : isChildMode ? "Add item" : "Add vendor"}
          </Button>
        </div>
      </form>
      {isEdit && vendor && (
        <div className="mt-4 border-t border-border pt-4">
          <AttachmentList
            projectId={projectId}
            ownerType="VENDOR"
            ownerId={vendor.id}
          />
        </div>
      )}
    </Modal>
  );
}

function SummaryChip({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <div className="rounded-lg border border-border p-2">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm font-semibold tabular-nums">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

/**
 * Records and lists a vendor's payments (installments) against the agreed full amount. Payment
 * history loads on demand; each add/delete revalidates the vendor + budget tabs.
 */
function PaymentsModal({
  projectId,
  vendor,
  open,
  canManage,
  onClose,
}: {
  projectId: string;
  vendor: VendorResponse | null;
  open: boolean;
  canManage: boolean;
  onClose: () => void;
}) {
  const [payments, setPayments] = useState<VendorPaymentResponse[] | null>(null);
  const [filesFor, setFilesFor] = useState<string | null>(null);
  const [busy, startBusy] = useTransition();
  const [state, formAction, pending] = useActionState<ActionState, FormData>(
    vendor
      ? addVendorPaymentAction.bind(null, projectId, vendor.id)
      : async () => ({}),
    {},
  );
  const formRef = useRef<HTMLFormElement>(null);
  const { toast } = useToast();

  const reload = () => {
    if (!vendor) return;
    startBusy(async () => {
      const res = await listVendorPaymentsAction(projectId, vendor.id);
      if (res.error) toast(res.error, "error");
      else setPayments(res.payments ?? []);
    });
  };

  useEffect(() => {
    if (open && vendor) reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, vendor?.id]);

  useEffect(() => {
    if (state.ok) {
      toast(kind === "paid" ? "Payment recorded" : "Installment scheduled");
      formRef.current?.reset();
      reload();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const [kind, setKind] = useState<"paid" | "planned">("paid");

  if (!vendor) return null;

  const total = vendor.agreedPrice ?? 0;
  const rows = payments ?? [];
  const paid = rows.filter((p) => p.paid).reduce((s, p) => s + p.amount, 0);
  const planned = rows.filter((p) => !p.paid).reduce((s, p) => s + p.amount, 0);
  // Headroom for a new schedule line — planned + paid together shouldn't exceed the agreed price.
  const remainingToSchedule = Math.max(0, total - paid - planned);
  const today = new Date().toISOString().slice(0, 10);

  const removePayment = (id: string) => {
    startBusy(async () => {
      const res = await deleteVendorPaymentAction(projectId, vendor.id, id);
      if (res.error) toast(res.error, "error");
      else {
        toast("Payment removed");
        reload();
      }
    });
  };

  const markPaid = (id: string) => {
    startBusy(async () => {
      const res = await markVendorPaymentPaidAction(projectId, vendor.id, id, today);
      if (res.error) toast(res.error, "error");
      else {
        toast("Marked as paid");
        reload();
      }
    });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Payments — ${vendor.name}`}
      description="Record installments or plan upcoming ones. Planned installments feed the due-soon reminders."
    >
      <div className="space-y-4">
        <div className="grid grid-cols-4 gap-3 text-center">
          <SummaryChip label="Full amount" value={formatMoney(total)} />
          <SummaryChip
            label="Paid"
            value={formatMoney(paid)}
            hint={total > 0 ? formatPercent(paid, total) : undefined}
          />
          <SummaryChip label="Planned" value={formatMoney(planned)} />
          <SummaryChip
            label="Remaining"
            value={formatMoney(remainingToSchedule)}
            hint={total > 0 ? formatPercent(remainingToSchedule, total) : undefined}
          />
        </div>

        {payments === null ? (
          <p className="text-sm text-muted-foreground">Loading payments…</p>
        ) : payments.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border py-4 text-center text-sm text-muted-foreground">
            No payments recorded yet.
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border">
            {payments.map((p) => (
              <li key={p.id} className="px-3 py-2 text-sm">
                <div className="flex items-center gap-3">
                  <span className="font-medium tabular-nums">{formatMoney(p.amount)}</span>
                  {p.paid ? (
                    <>
                      <Badge variant="secondary">Paid</Badge>
                      <span className="text-muted-foreground">
                        {p.paidOn ? formatDate(p.paidOn) : ""}
                      </span>
                    </>
                  ) : (
                    <>
                      <Badge variant="accent">Planned</Badge>
                      <span className="text-muted-foreground">
                        due {p.dueDate ? formatDate(p.dueDate) : "—"}
                      </span>
                    </>
                  )}
                  {p.note && (
                    <span className="truncate text-muted-foreground">· {p.note}</span>
                  )}
                  <div className="ml-auto flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setFilesFor((cur) => (cur === p.id ? null : p.id))}
                      aria-expanded={filesFor === p.id}
                      aria-label="Toggle attachments"
                      className={cn(
                        "rounded-md p-1 transition-colors hover:bg-muted",
                        filesFor === p.id
                          ? "text-primary"
                          : "text-muted-foreground hover:text-foreground",
                      )}
                    >
                      <Paperclip className="size-4" />
                    </button>
                    {!p.paid && (
                      <button
                        type="button"
                        onClick={() => markPaid(p.id)}
                        disabled={busy}
                        aria-label="Mark as paid"
                        className="rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                      >
                        Mark paid
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => removePayment(p.id)}
                      disabled={busy}
                      aria-label="Delete payment"
                      className="text-muted-foreground transition-colors hover:text-destructive"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </div>
                </div>
                {filesFor === p.id && (
                  <div className="mt-3 border-t border-border pt-3">
                    <AttachmentList
                      projectId={projectId}
                      ownerType="VENDOR_PAYMENT"
                      ownerId={p.id}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}

        {remainingToSchedule > 0 ? (
          <form
            ref={formRef}
            action={formAction}
            className="space-y-3 rounded-lg border border-border p-3"
          >
            {state.error && (
              <p
                role="alert"
                className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {state.error}
              </p>
            )}
            <input type="hidden" name="kind" value={kind} />
            <div className="inline-flex rounded-md border border-border p-0.5 text-xs">
              <button
                type="button"
                onClick={() => setKind("paid")}
                className={cn(
                  "rounded px-3 py-1 transition-colors",
                  kind === "paid" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                Record a payment
              </button>
              <button
                type="button"
                onClick={() => setKind("planned")}
                className={cn(
                  "rounded px-3 py-1 transition-colors",
                  kind === "planned" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                Plan an installment
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="pay-amount">Amount</Label>
                <Input
                  id="pay-amount"
                  name="amount"
                  type="number"
                  min="0"
                  step="0.01"
                  max={remainingToSchedule}
                  placeholder={String(remainingToSchedule)}
                  required
                />
              </div>
              {kind === "paid" ? (
                <div className="space-y-1.5">
                  <Label htmlFor="pay-date">Paid on</Label>
                  <Input id="pay-date" name="paidOn" type="date" defaultValue={today} required />
                </div>
              ) : (
                <div className="space-y-1.5">
                  <Label htmlFor="due-date">Due date</Label>
                  <Input id="due-date" name="dueDate" type="date" required />
                </div>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pay-note">Note (optional)</Label>
              <Input id="pay-note" name="note" placeholder="Deposit, final payment…" />
            </div>
            <div className="flex justify-end">
              <Button type="submit" size="sm" disabled={pending}>
                {pending
                  ? kind === "paid" ? "Recording…" : "Scheduling…"
                  : kind === "paid" ? "Record payment" : "Schedule installment"}
              </Button>
            </div>
          </form>
        ) : (
          <p className="rounded-lg border border-dashed border-border py-3 text-center text-sm font-medium text-success">
            Fully scheduled.
          </p>
        )}
      </div>
    </Modal>
  );
}

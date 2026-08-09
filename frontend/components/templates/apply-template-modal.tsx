"use client";

import { useState, useTransition } from "react";
import { LayoutTemplate } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/utils";

export interface TemplateOption {
  id: string;
  name: string;
  description: string | null;
  /** Pre-rendered preview lines for the items. */
  preview: string[];
  itemCount: number;
}

/**
 * Shared "pick a template and confirm" modal used by the checklist and vendor tabs. The caller
 * supplies the options and the apply callback (a server action bound to the project).
 */
export function ApplyTemplateModal({
  open,
  onClose,
  title,
  description,
  confirmLabel,
  templates,
  onApply,
  successNoun,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description: string;
  confirmLabel: string;
  templates: TemplateOption[];
  onApply: (templateId: string) => Promise<{ count?: number; error?: string }>;
  successNoun: string;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(
    templates.length === 1 ? templates[0].id : null,
  );
  const [pending, startTransition] = useTransition();
  const { toast } = useToast();

  const selected = templates.find((t) => t.id === selectedId) ?? null;

  const apply = () => {
    if (!selected) return;
    startTransition(async () => {
      const result = await onApply(selected.id);
      if (result.error) {
        toast(result.error, "error");
      } else {
        toast(`Added ${result.count} ${successNoun}${result.count === 1 ? "" : "s"}`);
        onClose();
      }
    });
  };

  return (
    <Modal open={open} onClose={onClose} title={title} description={description} className="max-w-xl">
      {templates.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          No templates available yet. Ask an admin to create one.
        </p>
      ) : (
        <div className="space-y-3">
          {templates.map((template) => {
            const isSelected = template.id === selectedId;
            return (
              <button
                key={template.id}
                type="button"
                onClick={() => setSelectedId(template.id)}
                aria-pressed={isSelected}
                className={cn(
                  "w-full rounded-lg border p-4 text-left transition-colors",
                  isSelected
                    ? "border-primary bg-primary/5"
                    : "border-border hover:bg-muted",
                )}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="flex items-center gap-2 font-medium">
                    <LayoutTemplate className="size-4 text-primary" />
                    {template.name}
                  </p>
                  <span className="text-xs text-muted-foreground">
                    {template.itemCount} items
                  </span>
                </div>
                {template.description && (
                  <p className="mt-1 text-xs text-muted-foreground">{template.description}</p>
                )}
                {isSelected && (
                  <ul className="mt-2 space-y-0.5 text-xs text-muted-foreground">
                    {template.preview.slice(0, 6).map((line) => (
                      <li key={line} className="truncate">
                        • {line}
                      </li>
                    ))}
                    {template.itemCount > 6 && (
                      <li>… and {template.itemCount - 6} more</li>
                    )}
                  </ul>
                )}
              </button>
            );
          })}

          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={apply} disabled={pending || !selected}>
              {pending ? "Applying…" : confirmLabel}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

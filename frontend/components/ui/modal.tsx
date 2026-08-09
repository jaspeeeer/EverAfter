"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: React.ReactNode;
  description?: React.ReactNode;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
}

/**
 * Accessible modal dialog rendered into a portal. Closes on Escape or backdrop click and locks
 * background scroll while open. Controlled via `open` / `onClose`.
 */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  className,
}: ModalProps) {
  const [mounted, setMounted] = React.useState(false);

  React.useEffect(() => setMounted(true), []);

  React.useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!mounted || !open) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
    >
      <div
        className="absolute inset-0 bg-foreground/40 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden
      />
      <div
        className={cn(
          "relative z-10 w-full max-w-lg rounded-xl border border-border bg-card text-card-foreground shadow-xl",
          className,
        )}
      >
        <div className="flex items-start justify-between gap-4 p-6 pb-2">
          <div className="flex flex-col gap-1">
            {title && (
              <h3 className="text-xl font-semibold tracking-tight">{title}</h3>
            )}
            {description && (
              <p className="text-sm text-muted-foreground">{description}</p>
            )}
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            aria-label="Close"
            className="-mr-2 -mt-2 size-8"
          >
            <X />
          </Button>
        </div>
        {children && <div className="p-6 pt-2">{children}</div>}
        {footer && (
          <div className="flex items-center justify-end gap-3 border-t border-border p-4">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}

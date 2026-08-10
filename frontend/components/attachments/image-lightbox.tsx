"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

export interface ImageLightboxProps {
  open: boolean;
  onClose: () => void;
  src: string;
  alt: string;
}

/**
 * Full-screen image preview, portaled like {@link Modal} but deliberately NOT built on top of it.
 * Attachments only ever render inside an already-open Modal (the vendor/payment/expense edit
 * dialogs), so a lightbox is always a *nested* overlay. Modal's Escape handler is an unscoped
 * `document` listener, so a plain nested Modal would close both layers on one Escape press — the
 * capture-phase listener below with `stopPropagation` is what keeps the parent dialog open.
 */
export function ImageLightbox({ open, onClose, src, alt }: ImageLightboxProps) {
  const [mounted, setMounted] = React.useState(false);
  const closeButtonRef = React.useRef<HTMLButtonElement>(null);
  const previouslyFocused = React.useRef<HTMLElement | null>(null);

  React.useEffect(() => setMounted(true), []);

  React.useEffect(() => {
    if (!open) return;
    previouslyFocused.current = document.activeElement as HTMLElement | null;
    closeButtonRef.current?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      // Capture + stop propagation so the parent Modal's own (unscoped) Escape listener never
      // sees this keypress — otherwise one Escape would close the lightbox AND the dialog beneath.
      e.stopPropagation();
      onClose();
    };
    document.addEventListener("keydown", onKey, { capture: true });
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey, { capture: true });
      document.body.style.overflow = previousOverflow;
      previouslyFocused.current?.focus();
    };
  }, [open, onClose]);

  if (!mounted || !open) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label={alt}
    >
      <div
        className="absolute inset-0 bg-foreground/80"
        onClick={onClose}
        aria-hidden
      />
      <button
        ref={closeButtonRef}
        type="button"
        onClick={onClose}
        aria-label="Close preview"
        className="absolute right-4 top-4 z-10 rounded-md p-2 text-white/80 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white"
      >
        <X className="size-6" />
      </button>
      {/* eslint-disable-next-line @next/next/no-img-element -- proxied, cookie-authed attachment
          bytes; next/image's optimizer refetches server-side without the httpOnly cookie and would
          401. See frontend/app/api/projects/[projectId]/attachments/[id]/route.ts. */}
      <img
        src={src}
        alt={alt}
        className="relative z-10 max-h-[85vh] max-w-[90vw] rounded-md object-contain shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      />
    </div>,
    document.body,
  );
}

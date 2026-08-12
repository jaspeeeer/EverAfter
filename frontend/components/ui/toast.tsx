"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
import { CheckCircle2, Info, X, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

type ToastVariant = "success" | "error" | "info";

interface ToastAction {
  label: string;
  onClick: () => void;
}

interface ToastOptions {
  /** Auto-dismiss delay in ms. Defaults to 3500, or 8000 when an `action` is set. */
  duration?: number;
  action?: ToastAction;
}

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
  action?: ToastAction;
}

interface ToastContextValue {
  /** `toast(message)` / `toast(message, "error")` still work exactly as before — `options` is new. */
  toast: (message: string, variant?: ToastVariant, options?: ToastOptions) => number;
  dismiss: (id: number) => void;
}

const DEFAULT_DURATION = 3500;
const ACTION_DURATION = 8000;

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return ctx;
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [mounted, setMounted] = useState(false);
  const idRef = useRef(0);
  const timersRef = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  useEffect(() => setMounted(true), []);

  const dismiss = useCallback((id: number) => {
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, variant: ToastVariant = "success", options?: ToastOptions) => {
      const id = (idRef.current += 1);
      setToasts((prev) => [...prev, { id, message, variant, action: options?.action }]);
      const duration = options?.duration ?? (options?.action ? ACTION_DURATION : DEFAULT_DURATION);
      timersRef.current.set(id, setTimeout(() => dismiss(id), duration));
      return id;
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ toast, dismiss }}>
      {children}
      {mounted &&
        createPortal(
          <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
            {toasts.map((t) => (
              <div
                key={t.id}
                role="status"
                className={cn(
                  "pointer-events-auto flex items-center gap-2 rounded-lg border bg-card px-4 py-3 text-sm shadow-lg",
                  t.variant === "error"
                    ? "border-destructive/30"
                    : t.variant === "info"
                      ? "border-primary/30"
                      : "border-border",
                )}
              >
                {t.variant === "error" ? (
                  <XCircle className="size-4 shrink-0 text-destructive" />
                ) : t.variant === "info" ? (
                  <Info className="size-4 shrink-0 text-primary" />
                ) : (
                  <CheckCircle2 className="size-4 shrink-0 text-success" />
                )}
                <span className="flex-1 text-card-foreground">{t.message}</span>
                {t.action && (
                  <button
                    type="button"
                    onClick={() => {
                      t.action?.onClick();
                      dismiss(t.id);
                    }}
                    className="shrink-0 font-medium text-primary transition-colors hover:text-primary/80"
                  >
                    {t.action.label}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => dismiss(t.id)}
                  aria-label="Dismiss"
                  className="shrink-0 text-muted-foreground transition-colors hover:text-foreground"
                >
                  <X className="size-4" />
                </button>
              </div>
            ))}
          </div>,
          document.body,
        )}
    </ToastContext.Provider>
  );
}

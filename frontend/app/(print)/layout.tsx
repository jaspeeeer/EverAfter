import { requireUser } from "@/lib/session";

/**
 * Chrome-free shell for printable packets. A sibling route group to `(app)` — layout nesting
 * follows filesystem ancestry with no opt-out, so a route under `(app)` can never escape its
 * header/tabs. `(auth)/layout.tsx` is the existing precedent for a totally different shell at
 * this level. Because `(app)/layout.tsx` no longer runs for anything under here, this layout
 * must do its own auth check.
 */
export default async function PrintLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  await requireUser();
  return (
    <div className="mx-auto max-w-3xl px-8 py-10 print:max-w-none print:px-0 print:py-0">
      {children}
    </div>
  );
}

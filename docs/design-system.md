# Design System ("Ever After")

Wedding-themed, token-driven UI built on Tailwind CSS v4 (CSS-first `@theme`) — no
`tailwind.config.js`.

## Tokens (`frontend/app/globals.css`)

Semantic CSS variables mapped into Tailwind via `@theme inline`, so utilities like `bg-primary`,
`text-muted-foreground`, `border-border` resolve to theme values:

- **Brand:** primary = dusty rose, secondary = soft sage, accent = champagne gold; warm
  off-white surfaces, warm charcoal ink.
- **Feedback:** success / warning / destructive (+ foregrounds).
- **Charts:** `--chart-paid`, `--chart-outstanding` — CVD-validated per theme; dark mode has its
  own darker steps rather than reusing the UI tokens (see [budget.md](budget.md)).
- **Typography:** Playfair Display for headings (`--font-display`, applied to h1–h4), Inter for
  body — both via `next/font`.

## Dark mode

The `.dark` class on `<html>` swaps the whole variable set. `ThemeToggle`
(`components/ui/theme-toggle.tsx`) persists the choice to `localStorage('theme')`; an inline
script in `app/layout.tsx` applies the saved/system theme **before paint** (no flash), with
`suppressHydrationWarning` on `<html>`.

## Component library (`frontend/components/ui/`)

`Button` (6 variants × 4 sizes, cva), `Card` family, `Input`, `Textarea`, `Label`, `Badge`
(8 variants), `Modal` (portal, Escape/backdrop close, body scroll lock), `SearchInput`,
`Spinner`, `Toast` (`ToastProvider` + `useToast()`, auto-dismiss, success/error variants), and
`components/kanban/` (`KanbanColumn` with accent bar + count, `KanbanCard`).

## Conventions

- Merge classes with `cn()` (`lib/utils.ts` — clsx + tailwind-merge).
- Forms: `useActionState` + server action returning `{ error?, ok? }`; on `ok` → toast + close
  modal + reset form. Errors render as an inline `role="alert"` strip.
- Icons: lucide-react, sized via `[&_svg]:size-4` in Button or explicit `size-*`.
- Money: always `formatMoney()` (PHP peso, `en-PH`); dates via `formatDate()` /
  `countdownToWedding()`; enum labels via `humanizeEnum()` — all in `lib/format.ts`.
- Keep marks/labels readable in both themes; text always uses ink tokens, never series colors.

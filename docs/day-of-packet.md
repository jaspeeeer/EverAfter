# Day-Of Packet

A single printable page for the wedding day — run sheet, vendor contacts, and seating/dietary in
one packet — reached from a **"Day-of sheet"** button in the project header.

## Route

`/projects/{id}/day-of` lives under a **new sibling route group**, `app/(print)/`, not under
`app/(app)/`. Layout nesting in the App Router follows filesystem ancestry with no opt-out — a
route under `(app)` can never escape its header/tabs — so a chrome-free page needs its own root
sibling. `(auth)/layout.tsx` is the existing precedent for exactly this (a route group with a
totally different shell). Because `(app)/layout.tsx` no longer runs for anything under `(print)/`,
**`(print)/layout.tsx` does its own `requireUser()`** — the edge `proxy.ts` still redirects a
tokenless request to `/login` (the route isn't in `PUBLIC_PATHS`), but `requireUser()` is the real
server-side check, same as everywhere else.

The page is a plain Server Component: one `Promise.all` over `getProject`, `getTimeline`,
`getVendors`, `getGuests`, with the same 403/404 → `notFound()` mapping used by
`projects/[id]/layout.tsx`.

## Rendering rules

- **Timeline order is preserved, never re-sorted.** `TimelineService.list` (backend) already
  sorts by `wrappedMinutes(startTime)` (`EARLY_MORNING_CUTOFF = 04:00`), so after-midnight events
  correctly land at the end of the day. Re-sorting client-side by raw `startTime` would put the
  after-party back at the top.
- **`partySize` null → 1** for the party-size column, same convention as the Guests tab.
- **`rsvpToken` is never rendered.** `GuestResponse` carries it, but a printed sheet is a
  shareable physical/PDF object — printing the token would hand out a live write primitive for
  that guest's RSVP to anyone who sees the paper. Covered by `e2e/day-of-packet.spec.ts`
  (asserts the token string doesn't appear anywhere in the page HTML).
- Vendors are grouped package-then-items via `lib/vendor-tree.ts`'s `orderVendorsForPicker` (the
  same helper the vendor form's picker uses), so a package's bundled items stay visually
  attached to it instead of scattering through an alphabetical list.

## Print stylesheet

`app/globals.css` adds one `@media print` block that re-points every raw CSS variable `@theme
inline` maps color utilities through (`--background`, `--primary`, `--success`, etc.) to an
ink-on-paper palette. Because `.dark` is a class on `<html>` (not a media feature), printing from
dark mode would otherwise print dark backgrounds — the block targets `:root, .dark` together so
its specificity beats `.dark` in either theme, flipping the whole design system with zero
per-utility overrides. `--chart-paid` / `--chart-outstanding` are overridden too even though this
page has no chart, since the block is global (any page can be Cmd+P'd) and those two tokens
aren't exposed via `@theme` at all — they're consumed as inline styles by the budget breakdown
chart.

Semantic Badge colors (booked/not-booked, RSVP status) collapse to one ink shade under print —
intentional, not a gap: the app's own rule is "identity never color alone," so the text label is
already the real signal and losing hue differentiation on a printed/monochrome page costs
nothing.

`print:hidden` (Tailwind's built-in `print:` variant, no config needed) hides the "Print / Save as
PDF" button and the intro line on the printed output itself.

## Key files

- `frontend/app/(print)/layout.tsx`, `app/(print)/projects/[id]/day-of/page.tsx`,
  `components/print/print-button.tsx`
- `app/globals.css` (`@media print` block)
- `lib/format.ts` (`guestFullName` — also used by the Guests tab), `lib/vendor-tree.ts`
- Entry point: the "Day-of sheet" link in `app/(app)/projects/[id]/layout.tsx`
- Tests: `e2e/day-of-packet.spec.ts`

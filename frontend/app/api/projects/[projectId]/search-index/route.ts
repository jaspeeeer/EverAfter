import { NextResponse } from "next/server";
import { ApiError } from "@/lib/api";
import { getExpenses, getGuests, getTasks, getVendors } from "@/lib/data";
import { guestFullName } from "@/lib/format";

export type SearchItemType = "guest" | "vendor" | "task" | "expense";

export interface SearchIndexItem {
  type: SearchItemType;
  id: string;
  label: string;
  sublabel: string | null;
  href: string;
}

/**
 * A flat, pre-trimmed search index for one project — guests, vendors, tasks, and expenses in a
 * single payload. The client fetches this once when the search box first opens and filters it in
 * memory, matching the app's existing per-tab search architecture (every list receives its full
 * data as a prop; see `lib/use-table-controls.ts`). A wedding's dataset is small enough that this
 * is cheaper than debouncing a per-keystroke endpoint.
 *
 * No extra `@PreAuthorize` surface: each `lib/data.ts` getter already calls a `canAccess`-gated
 * backend endpoint, so a user without project access gets the same 403 here that they'd get on
 * any other tab.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ projectId: string }> },
) {
  const { projectId } = await params;
  try {
    const [guests, vendors, tasks, expenses] = await Promise.all([
      getGuests(projectId),
      getVendors(projectId),
      getTasks(projectId),
      getExpenses(projectId),
    ]);

    const base = `/projects/${projectId}`;
    const items: SearchIndexItem[] = [
      // rsvpToken is deliberately left off — no reason to widen its exposure.
      ...guests.map((g) => ({
        type: "guest" as const,
        id: g.id,
        label: guestFullName(g),
        sublabel: g.roles.map((r) => r.name).join(", ") || g.email,
        href: `${base}/guests`,
      })),
      ...vendors.map((v) => ({
        type: "vendor" as const,
        id: v.id,
        label: v.name,
        sublabel: v.categoryName,
        href: `${base}/vendors`,
      })),
      ...tasks.map((t) => ({
        type: "task" as const,
        id: t.id,
        label: t.title,
        sublabel: t.description,
        href: `${base}/checklist`,
      })),
      ...expenses.map((e) => ({
        type: "expense" as const,
        id: e.id,
        label: e.description,
        sublabel: e.categoryName,
        href: `${base}/budget`,
      })),
    ];

    return NextResponse.json(items);
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 500;
    return NextResponse.json([], { status });
  }
}

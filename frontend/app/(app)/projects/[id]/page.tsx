import Link from "next/link";
import { CalendarClock, ListChecks, Store, Users } from "lucide-react";
import {
  getBudget,
  getGuests,
  getInvitations,
  getProject,
  getTasks,
  getTimeline,
  getVendors,
} from "@/lib/data";
import { getCurrentUser } from "@/lib/session";
import { isCouple } from "@/lib/types";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { InviteCoupleCard } from "@/components/projects/invite-couple-card";
import { formatMoney, formatPercent, formatTime } from "@/lib/format";
import { cn } from "@/lib/utils";

export default async function ProjectOverviewPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [user, project, budget, tasks, vendors, guests, timeline] = await Promise.all([
    getCurrentUser(),
    getProject(id),
    getBudget(id),
    getTasks(id),
    getVendors(id),
    getGuests(id),
    getTimeline(id),
  ]);

  // Planners/admins manage invitations; couples never see this surface.
  const canInvite = user !== null && !isCouple(user.roles) && project.ownerEmail === null;
  const invitations = canInvite ? await getInvitations(id) : [];

  const doneTasks = tasks.filter((t) => t.status === "DONE").length;
  const bookedVendors = vendors.filter((v) => v.booked).length;
  const attendingHeadcount = guests
    .filter((g) => g.rsvpStatus === "ATTENDING")
    .reduce((sum, g) => sum + (g.partySize ?? 1), 0);
  // Scale the progress bar against the budget when set, else against total committed spend.
  const budgetDenom = budget.totalBudget ?? budget.totalExpenses;
  const paidPct =
    budgetDenom > 0 ? Math.min(100, (budget.totalPaid / budgetDenom) * 100) : 0;
  const unpaidCommittedPct =
    budgetDenom > 0
      ? Math.min(
          100 - paidPct,
          Math.max(0, ((budget.totalExpenses - budget.totalPaid) / budgetDenom) * 100),
        )
      : 0;
  const showProgress = budget.totalBudget !== null || budget.totalExpenses > 0;

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      {canInvite && <InviteCoupleCard projectId={id} invitations={invitations} />}

      {/* Budget */}
      <Card className="lg:col-span-3">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Budget</CardTitle>
              <CardDescription>Planned vs. committed spend.</CardDescription>
            </div>
            {budget.overBudget ? (
              <Badge variant="destructive">Over budget</Badge>
            ) : (
              <Badge variant="success">On track</Badge>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Stat label="Budget" value={formatMoney(budget.totalBudget)} />
            <Stat label="Committed" value={formatMoney(budget.totalExpenses)} />
            <Stat label="Paid" value={formatMoney(budget.totalPaid)} />
            <Stat
              label="Remaining"
              value={formatMoney(budget.remaining)}
              emphasis={budget.overBudget ? "danger" : "default"}
            />
          </div>
          {showProgress && (
            <>
              <div
                className="flex h-2 w-full overflow-hidden rounded-full bg-muted"
                role="img"
                aria-label={`${formatMoney(budget.totalPaid)} paid, ${formatMoney(
                  budget.totalExpenses - budget.totalPaid,
                )} outstanding${
                  budget.totalBudget !== null
                    ? ` of ${formatMoney(budget.totalBudget)} budget`
                    : ""
                }`}
              >
                {paidPct > 0 && (
                  <span
                    className="h-full"
                    style={{ width: `${paidPct}%`, background: "var(--chart-paid)" }}
                  />
                )}
                {paidPct > 0 && unpaidCommittedPct > 0 && (
                  <span className="h-full w-[2px] shrink-0 bg-card" aria-hidden />
                )}
                {unpaidCommittedPct > 0 && (
                  <span
                    className={cn("h-full", budget.overBudget && "bg-destructive")}
                    style={
                      budget.overBudget
                        ? { width: `${unpaidCommittedPct}%` }
                        : { width: `${unpaidCommittedPct}%`, background: "var(--chart-outstanding)" }
                    }
                  />
                )}
              </div>
              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <span
                    aria-hidden
                    className="inline-block size-2 rounded-full"
                    style={{ background: "var(--chart-paid)" }}
                  />
                  Paid
                </span>
                <span className="flex items-center gap-1.5">
                  <span
                    aria-hidden
                    className={cn(
                      "inline-block size-2 rounded-full",
                      budget.overBudget && "bg-destructive",
                    )}
                    style={budget.overBudget ? undefined : { background: "var(--chart-outstanding)" }}
                  />
                  Outstanding
                </span>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* Checklist summary */}
      <Link href={`/projects/${id}/checklist`} className="block">
        <Card className="h-full transition-shadow hover:shadow-md">
          <CardHeader>
            <ListChecks className="size-5 text-primary" />
            <CardTitle>Checklist</CardTitle>
            <CardDescription>
              {doneTasks} of {tasks.length} tasks done
              {tasks.length > 0 && ` (${formatPercent(doneTasks, tasks.length)})`}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <span className="text-sm font-medium text-primary">
              Open board →
            </span>
          </CardContent>
        </Card>
      </Link>

      {/* Vendors summary */}
      <Link href={`/projects/${id}/vendors`} className="block">
        <Card className="h-full transition-shadow hover:shadow-md">
          <CardHeader>
            <Store className="size-5 text-primary" />
            <CardTitle>Vendors</CardTitle>
            <CardDescription>
              {bookedVendors} of {vendors.length} booked
              {vendors.length > 0 && ` (${formatPercent(bookedVendors, vendors.length)})`}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <span className="text-sm font-medium text-primary">
              Manage vendors →
            </span>
          </CardContent>
        </Card>
      </Link>

      {/* Guests summary */}
      <Link href={`/projects/${id}/guests`} className="block">
        <Card className="h-full transition-shadow hover:shadow-md">
          <CardHeader>
            <Users className="size-5 text-primary" />
            <CardTitle>Guests</CardTitle>
            <CardDescription>
              {attendingHeadcount} attending
              {guests.length > 0 && ` (${formatPercent(attendingHeadcount, guests.length)})`} ·{" "}
              {guests.length} invites
            </CardDescription>
          </CardHeader>
          <CardContent>
            <span className="text-sm font-medium text-primary">
              Manage guest list →
            </span>
          </CardContent>
        </Card>
      </Link>

      {/* Timeline summary */}
      <Link href={`/projects/${id}/timeline`} className="block">
        <Card className="h-full transition-shadow hover:shadow-md">
          <CardHeader>
            <CalendarClock className="size-5 text-primary" />
            <CardTitle>Day timeline</CardTitle>
            <CardDescription>
              {timeline.length === 0
                ? "The day isn't mapped yet"
                : `${timeline.length} events · starts ${formatTime(timeline[0].startTime)}`}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <span className="text-sm font-medium text-primary">
              Open run sheet →
            </span>
          </CardContent>
        </Card>
      </Link>
    </div>
  );
}

function Stat({
  label,
  value,
  emphasis = "default",
}: {
  label: string;
  value: string;
  emphasis?: "default" | "danger";
}) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-muted-foreground">
        {label}
      </p>
      <p
        className={
          emphasis === "danger"
            ? "mt-1 text-lg font-semibold text-destructive"
            : "mt-1 text-lg font-semibold"
        }
      >
        {value}
      </p>
    </div>
  );
}

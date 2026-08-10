import { notFound } from "next/navigation";
import { getGuests, getProject, getTimeline, getVendors } from "@/lib/data";
import { ApiError } from "@/lib/api";
import { PrintButton } from "@/components/print/print-button";
import { Badge } from "@/components/ui/badge";
import {
  countdownToWedding,
  formatDate,
  formatTime,
  guestFullName,
  humanizeEnum,
} from "@/lib/format";
import { orderVendorsForPicker } from "@/lib/vendor-tree";
import type { EventVendorResponse, RsvpStatus } from "@/lib/types";

const RSVP_VARIANT: Record<
  RsvpStatus,
  "default" | "success" | "warning" | "destructive" | "accent"
> = {
  PENDING: "warning",
  ATTENDING: "success",
  MAYBE: "accent",
  DECLINED: "destructive",
};

export default async function DayOfPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let project;
  try {
    project = await getProject(id);
  } catch (error) {
    // 403 (not your project) and 404 both render as "not found" to avoid leaking existence —
    // same rule as the authenticated project layout.
    if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
      notFound();
    }
    throw error;
  }

  const [timeline, vendors, guests] = await Promise.all([
    getTimeline(id),
    getVendors(id),
    getGuests(id),
  ]);

  const seating = [...guests].sort((a, b) => {
    const ta = a.tableNumber ?? Number.MAX_SAFE_INTEGER;
    const tb = b.tableNumber ?? Number.MAX_SAFE_INTEGER;
    if (ta !== tb) return ta - tb;
    return guestFullName(a).localeCompare(guestFullName(b));
  });

  return (
    <div className="space-y-10">
      <div className="flex items-start justify-between gap-4 print:hidden">
        <p className="text-sm text-muted-foreground">
          A printable run sheet for the wedding day — vendor contacts, timeline, and seating in
          one packet.
        </p>
        <PrintButton />
      </div>

      <header className="space-y-1 border-b border-border pb-6 text-center">
        <p className="text-sm uppercase tracking-wide text-muted-foreground">Day-of packet</p>
        <h1 className="text-3xl font-bold tracking-tight">{project.name}</h1>
        <p className="text-muted-foreground">
          {formatDate(project.weddingDate)}
          {countdownToWedding(project.weddingDate) && (
            <> · {countdownToWedding(project.weddingDate)}</>
          )}
        </p>
      </header>

      <Section title="Run sheet">
        {timeline.length === 0 ? (
          <EmptyNote>No timeline events yet.</EmptyNote>
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th className="w-32 py-2 pr-3">Time</th>
                <th className="py-2 pr-3">Event</th>
                <th className="py-2 pr-3">Location</th>
                <th className="py-2">Suppliers</th>
              </tr>
            </thead>
            <tbody>
              {/* Preserve the order the backend already returned — TimelineService sorts by
                  wrappedMinutes(startTime) so after-midnight events correctly land last. */}
              {timeline.map((event) => (
                <tr key={event.id} className="border-b border-border/60 align-top [break-inside:avoid]">
                  <td className="py-2 pr-3 font-medium tabular-nums">
                    {formatTime(event.startTime)}
                    {event.endTime && <> – {formatTime(event.endTime)}</>}
                  </td>
                  <td className="py-2 pr-3">
                    <p className="font-medium">{event.title}</p>
                    {event.description && (
                      <p className="text-muted-foreground">{event.description}</p>
                    )}
                  </td>
                  <td className="py-2 pr-3 text-muted-foreground">{event.location ?? "—"}</td>
                  <td className="py-2">
                    {event.vendors.length === 0 ? (
                      <span className="text-muted-foreground">—</span>
                    ) : (
                      <ul className="space-y-0.5">
                        {event.vendors.map((v) => (
                          <li key={v.id}>{vendorContactLine(v)}</li>
                        ))}
                      </ul>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>

      <Section title="Vendor contacts">
        {vendors.length === 0 ? (
          <EmptyNote>No vendors yet.</EmptyNote>
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th className="py-2 pr-3">Vendor</th>
                <th className="py-2 pr-3">Category</th>
                <th className="py-2 pr-3">Phone</th>
                <th className="py-2 pr-3">Email</th>
                <th className="py-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {orderVendorsForPicker(vendors).map(({ vendor: v, indent }) => (
                <tr key={v.id} className="border-b border-border/60 [break-inside:avoid]">
                  <td className="py-2 pr-3 font-medium">
                    {indent ? <span className="text-muted-foreground">— </span> : null}
                    {v.name}
                  </td>
                  <td className="py-2 pr-3 text-muted-foreground">{v.categoryName}</td>
                  <td className="py-2 pr-3">{v.phone ?? "—"}</td>
                  <td className="py-2 pr-3">{v.contactEmail ?? "—"}</td>
                  <td className="py-2">
                    <Badge variant={v.booked ? "success" : "warning"}>
                      {v.booked ? "Booked" : "Not booked"}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>

      <Section title="Seating & dietary">
        {seating.length === 0 ? (
          <EmptyNote>No guests yet.</EmptyNote>
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th className="w-20 py-2 pr-3">Table</th>
                <th className="py-2 pr-3">Guest</th>
                <th className="w-20 py-2 pr-3">Party</th>
                <th className="py-2 pr-3">Dietary</th>
                <th className="w-24 py-2">RSVP</th>
              </tr>
            </thead>
            <tbody>
              {seating.map((g) => (
                <tr key={g.id} className="border-b border-border/60 [break-inside:avoid]">
                  <td className="py-2 pr-3 tabular-nums">{g.tableNumber ?? "—"}</td>
                  <td className="py-2 pr-3 font-medium">{guestFullName(g)}</td>
                  <td className="py-2 pr-3 tabular-nums">{g.partySize ?? 1}</td>
                  <td className="py-2 pr-3 text-muted-foreground">{g.dietaryNotes ?? "—"}</td>
                  <td className="py-2">
                    <Badge variant={RSVP_VARIANT[g.rsvpStatus]}>
                      {humanizeEnum(g.rsvpStatus)}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>
    </div>
  );
}

function vendorContactLine(vendor: EventVendorResponse): string {
  const contact = vendor.phone ?? vendor.contactEmail;
  return contact ? `${vendor.name} (${contact})` : vendor.name;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="[break-inside:avoid] space-y-3">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      {children}
    </section>
  );
}

function EmptyNote({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>;
}

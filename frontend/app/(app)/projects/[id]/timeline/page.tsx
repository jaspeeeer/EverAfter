import { getTimeline, getVendors } from "@/lib/data";
import { getCurrentUser } from "@/lib/session";
import { isCouple } from "@/lib/types";
import { TimelineView } from "@/components/timeline/timeline-view";

export default async function TimelinePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [events, vendors, user] = await Promise.all([
    getTimeline(id),
    getVendors(id),
    getCurrentUser(),
  ]);

  // Planners/admins shape the day; the couple follows it read-only.
  const canEdit = user !== null && !isCouple(user.roles);

  return (
    <TimelineView
      projectId={id}
      events={events}
      vendors={vendors}
      canEdit={canEdit}
    />
  );
}

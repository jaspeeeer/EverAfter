import { getActivity } from "@/lib/data";
import { ActivityFeed } from "@/components/activity/activity-feed";

export const metadata = { title: "Activity" };

interface ActivityPageProps {
  params: Promise<{ id: string }>;
}

export default async function ActivityPage({ params }: ActivityPageProps) {
  const { id } = await params;
  const entries = await getActivity(id, { limit: 100 });

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Activity</h2>
        <p className="text-sm text-muted-foreground">
          Everything that happened on this project, most recent first.
        </p>
      </div>
      <ActivityFeed entries={entries} />
    </section>
  );
}

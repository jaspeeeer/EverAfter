import { getNotificationPreferences } from "@/lib/data";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { NotificationPreferencesForm } from "@/components/notifications/notification-preferences-form";

export const metadata = { title: "Notification settings" };

export default async function NotificationSettingsPage() {
  const prefs = await getNotificationPreferences();

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          Notification settings
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Choose which reminders you receive in the in-app bell.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>In-app notifications</CardTitle>
          <CardDescription>
            The bell shows these in your top nav. Reminders are generated once
            daily.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <NotificationPreferencesForm initial={prefs} />
        </CardContent>
      </Card>
    </div>
  );
}

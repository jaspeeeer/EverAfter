import Link from "next/link";
import { Heart, LogOut, ShieldCheck } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getUnreadNotificationCount, listNotifications } from "@/lib/data";
import { isAdmin, isPlanner, type RoleName } from "@/lib/types";
import { logoutAction } from "@/app/actions/auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/ui/theme-toggle";
import { ToastProvider } from "@/components/ui/toast";
import { NotificationBell } from "@/components/notifications/notification-bell";

function roleLabel(roles: RoleName[]): string {
  if (isAdmin(roles)) return "Admin";
  if (isPlanner(roles)) return "Planner";
  return "Couple";
}

export default async function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const user = await requireUser();
  const label = roleLabel(user.roles);
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ");

  // Fetch the bell payload in parallel; treat failures as an empty feed so the header still
  // renders if the notifications service is unavailable.
  const [initialNotifications, unreadCountResp] = await Promise.all([
    listNotifications(false, 20).catch(() => []),
    getUnreadNotificationCount().catch(() => ({ count: 0 })),
  ]);

  return (
    <ToastProvider>
      <div className="flex min-h-screen flex-col">
        <header className="sticky top-0 z-40 border-b border-border bg-card/80 backdrop-blur">
          <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-6">
            <div className="flex items-center gap-4">
              <Link href="/dashboard" className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <Heart className="size-4" />
                </span>
                <span className="text-lg font-semibold tracking-tight">Ever After</span>
              </Link>
              {/* Role-based nav: the admin console only exists for admins. */}
              {isAdmin(user.roles) && (
                <Link
                  href="/admin"
                  className="flex items-center gap-1 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
                >
                  <ShieldCheck className="size-4" />
                  Admin
                </Link>
              )}
            </div>

            <div className="flex items-center gap-2 sm:gap-3">
              <div className="hidden text-right sm:block">
                {fullName && (
                  <p className="text-sm font-medium leading-none">{fullName}</p>
                )}
                <p className="text-xs text-muted-foreground">{user.email}</p>
              </div>
              <Badge variant={isAdmin(user.roles) ? "accent" : "secondary"}>
                {label}
              </Badge>
              <NotificationBell
                initial={initialNotifications}
                initialUnreadCount={unreadCountResp.count}
              />
              <ThemeToggle />
              <form action={logoutAction}>
                <Button variant="ghost" size="sm" type="submit">
                  <LogOut />
                  <span className="hidden sm:inline">Log out</span>
                </Button>
              </form>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">{children}</main>
      </div>
    </ToastProvider>
  );
}

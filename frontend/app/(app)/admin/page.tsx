import Link from "next/link";
import { redirect } from "next/navigation";
import {
  BarChart3,
  BookUser,
  ChevronRight,
  FolderHeart,
  LayoutTemplate,
  ListChecks,
  Store,
  Tags,
  UserCog,
  Users,
  UtensilsCrossed,
  Wallet,
} from "lucide-react";
import { requireUser } from "@/lib/session";
import { getAdminStats, getAdminUsers } from "@/lib/data";
import { isAdmin } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { UserTable } from "@/components/admin/user-table";
import { formatPercent } from "@/lib/format";

export default async function AdminPage() {
  const user = await requireUser();
  // Defense in depth: the API also rejects non-admins with 403.
  if (!isAdmin(user.roles)) redirect("/dashboard");

  const [stats, users] = await Promise.all([getAdminStats(), getAdminUsers()]);

  const cards = [
    { label: "Users", value: stats.totalUsers, icon: Users },
    { label: "Projects", value: stats.totalProjects, icon: FolderHeart },
    { label: "Tasks", value: stats.totalTasks, icon: ListChecks },
    { label: "Vendors", value: stats.totalVendors, icon: Store },
    { label: "Expenses", value: stats.totalExpenses, icon: Wallet },
    { label: "Guests", value: stats.totalGuests, icon: UtensilsCrossed },
  ];

  const roleBreakdown = [
    { label: "Admins", value: stats.usersByRole["ROLE_ADMIN"] ?? 0 },
    { label: "Planners", value: stats.usersByRole["ROLE_PLANNER"] ?? 0 },
    { label: "Couples", value: stats.usersByRole["ROLE_USER"] ?? 0 },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Admin</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Platform overview and user management.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        {cards.map(({ label, value, icon: Icon }) => (
          <Card key={label}>
            <CardContent className="p-4">
              <Icon className="size-4 text-primary" />
              <p className="mt-2 text-2xl font-semibold">{value}</p>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">
                {label}
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
        {roleBreakdown.map(({ label, value }) => (
          <span key={label}>
            <span className="font-semibold text-foreground">{value}</span> {label}
            {stats.totalUsers > 0 && ` (${formatPercent(value, stats.totalUsers)})`}
          </span>
        ))}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <AdminNavCard
          href="/admin/templates"
          icon={LayoutTemplate}
          title="Templates"
          description="Preset checklists and vendor lists planners can apply."
        />
        <AdminNavCard
          href="/admin/vendor-categories"
          icon={Tags}
          title="Vendor categories"
          description="The categories planners choose from when adding vendors."
        />
        <AdminNavCard
          href="/admin/vendor-directory"
          icon={BookUser}
          title="Vendor directory"
          description="A shared list of suppliers planners can reuse."
        />
        <AdminNavCard
          href="/admin/reports"
          icon={BarChart3}
          title="Vendor reports"
          description="By category, in-demand vendors, and booking conversion."
        />
        <AdminNavCard
          href="/admin/guest-roles"
          icon={UserCog}
          title="Guest roles"
          description="Wedding-day roles planners assign to guests (Best Man, Principal Sponsor, …)."
        />
      </div>

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Users</h2>
        <UserTable users={users} currentUserId={user.id} />
      </div>
    </div>
  );
}

function AdminNavCard({
  href,
  icon: Icon,
  title,
  description,
}: {
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
}) {
  return (
    <Link href={href} className="block">
      <Card className="h-full transition-shadow hover:shadow-md">
        <CardContent className="flex items-center gap-3 p-4">
          <Icon className="size-5 text-primary" />
          <div className="flex-1">
            <p className="font-medium">{title}</p>
            <p className="text-xs text-muted-foreground">{description}</p>
          </div>
          <ChevronRight className="size-4 text-muted-foreground" />
        </CardContent>
      </Card>
    </Link>
  );
}

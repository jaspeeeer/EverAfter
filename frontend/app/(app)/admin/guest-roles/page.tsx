import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getAdminGuestRoles } from "@/lib/data";
import { isAdmin } from "@/lib/types";
import { GuestRoleManager } from "@/components/admin/guest-role-manager";

export default async function AdminGuestRolesPage() {
  const user = await requireUser();
  if (!isAdmin(user.roles)) redirect("/dashboard");

  const roles = await getAdminGuestRoles();

  return (
    <div className="space-y-8">
      <div>
        <Link
          href="/admin"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="size-4" />
          Admin
        </Link>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Guest roles</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          The wedding-day roles planners assign to guests (Principal Sponsor, Best Man, …).
          Deleting one that&apos;s in use deactivates it instead.
        </p>
      </div>

      <GuestRoleManager roles={roles} />
    </div>
  );
}

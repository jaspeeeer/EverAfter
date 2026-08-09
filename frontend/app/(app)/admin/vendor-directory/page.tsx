import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getAdminVendorDirectory, getVendorCategories } from "@/lib/data";
import { isAdmin } from "@/lib/types";
import { VendorDirectoryManager } from "@/components/admin/vendor-directory-manager";

export default async function AdminVendorDirectoryPage() {
  const user = await requireUser();
  if (!isAdmin(user.roles)) redirect("/dashboard");

  const [entries, categories] = await Promise.all([
    getAdminVendorDirectory(),
    getVendorCategories(),
  ]);

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
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Vendor directory</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          A shared list of suppliers planners can add to any wedding — the source for the
          in-demand vendor report.
        </p>
      </div>

      <VendorDirectoryManager entries={entries} categories={categories} />
    </div>
  );
}

import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getAdminVendorCategories } from "@/lib/data";
import { isAdmin } from "@/lib/types";
import { VendorCategoryManager } from "@/components/admin/vendor-category-manager";

export default async function AdminVendorCategoriesPage() {
  const user = await requireUser();
  if (!isAdmin(user.roles)) redirect("/dashboard");

  const categories = await getAdminVendorCategories();

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
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Vendor categories</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          The categories planners choose from when adding vendors. Deleting one that&apos;s in use
          deactivates it instead.
        </p>
      </div>

      <VendorCategoryManager categories={categories} />
    </div>
  );
}

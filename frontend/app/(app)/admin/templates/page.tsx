import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { requireUser } from "@/lib/session";
import {
  getChecklistTemplates,
  getVendorCategories,
  getVendorTemplates,
} from "@/lib/data";
import { isAdmin } from "@/lib/types";
import { TemplateManager } from "@/components/admin/template-manager";

export default async function AdminTemplatesPage() {
  const user = await requireUser();
  // Defense in depth: the API rejects non-admin writes with 403 regardless.
  if (!isAdmin(user.roles)) redirect("/dashboard");

  const [checklistTemplates, vendorTemplates, categories] = await Promise.all([
    getChecklistTemplates(),
    getVendorTemplates(),
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
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Templates</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Preset checklists and vendor lists that planners can apply to their projects.
        </p>
      </div>

      <TemplateManager
        checklistTemplates={checklistTemplates}
        vendorTemplates={vendorTemplates}
        categories={categories}
      />
    </div>
  );
}

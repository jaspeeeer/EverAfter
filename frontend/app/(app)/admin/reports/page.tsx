import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { requireUser } from "@/lib/session";
import {
  getBookingConversionReport,
  getInDemandVendorsReport,
  getVendorCategories,
  getVendorsByCategoryReport,
} from "@/lib/data";
import { isAdmin } from "@/lib/types";
import { ReportsView } from "@/components/admin/reports-view";

export default async function AdminReportsPage() {
  const user = await requireUser();
  if (!isAdmin(user.roles)) redirect("/dashboard");

  const [vendorsByCategory, bookingConversion, initialInDemand, categories] = await Promise.all([
    getVendorsByCategoryReport(),
    getBookingConversionReport(),
    getInDemandVendorsReport(),
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
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Vendor reports</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Cross-project vendor insights. Each report exports to CSV.
        </p>
      </div>

      <ReportsView
        vendorsByCategory={vendorsByCategory}
        bookingConversion={bookingConversion}
        initialInDemand={initialInDemand}
        categories={categories}
      />
    </div>
  );
}

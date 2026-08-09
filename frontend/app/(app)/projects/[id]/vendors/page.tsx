import {
  getVendorCategories,
  getVendorDirectory,
  getVendorTemplates,
  getVendors,
} from "@/lib/data";
import { getCurrentUser } from "@/lib/session";
import { isCouple } from "@/lib/types";
import { VendorList } from "@/components/vendors/vendor-list";

export default async function VendorsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [vendors, categories, user] = await Promise.all([
    getVendors(id),
    getVendorCategories(),
    getCurrentUser(),
  ]);

  // Templates and the directory are planner/admin tools; the API 403s couples anyway.
  const canManage = user !== null && !isCouple(user.roles);
  const [templates, directory] = canManage
    ? await Promise.all([getVendorTemplates(), getVendorDirectory()])
    : [[], []];

  return (
    <VendorList
      projectId={id}
      vendors={vendors}
      categories={categories}
      templates={templates}
      directory={directory}
      canManage={canManage}
    />
  );
}

import { getBudget, getExpenses, getVendorCategories, getVendors } from "@/lib/data";
import { BudgetTracker } from "@/components/budget/budget-tracker";

export default async function BudgetPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [budget, expenses, categories, vendors] = await Promise.all([
    getBudget(id),
    getExpenses(id),
    getVendorCategories(),
    getVendors(id),
  ]);

  return (
    <BudgetTracker
      projectId={id}
      budget={budget}
      expenses={expenses}
      categories={categories}
      vendors={vendors}
    />
  );
}

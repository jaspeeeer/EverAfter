import { getChecklistTemplates, getTasks } from "@/lib/data";
import { getCurrentUser } from "@/lib/session";
import { isCouple } from "@/lib/types";
import { ChecklistBoard } from "@/components/checklist/checklist-board";

export default async function ChecklistPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [tasks, user] = await Promise.all([getTasks(id), getCurrentUser()]);

  // Templates are a planner/admin tool; the browse endpoint 403s couples anyway.
  const canApplyTemplates = user !== null && !isCouple(user.roles);
  const templates = canApplyTemplates ? await getChecklistTemplates() : [];

  return (
    <ChecklistBoard
      projectId={id}
      tasks={tasks}
      templates={templates}
      canApplyTemplates={canApplyTemplates}
    />
  );
}

import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getProject } from "@/lib/data";
import { ApiError } from "@/lib/api";
import { countdownToWedding, formatDate, formatMoney } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { ProjectTabs } from "@/components/projects/project-tabs";

export default async function ProjectLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ id: string }>;
}) {
  await requireUser();
  const { id } = await params;

  let project;
  try {
    project = await getProject(id);
  } catch (error) {
    // 403 (not your project) and 404 both render as "not found" to avoid leaking existence.
    if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
      notFound();
    }
    throw error;
  }

  return (
    <div className="space-y-6">
      <div>
        <Link
          href="/dashboard"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="size-4" />
          All projects
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-bold tracking-tight">{project.name}</h1>
          {countdownToWedding(project.weddingDate) && (
            <Badge variant="primary">{countdownToWedding(project.weddingDate)}</Badge>
          )}
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          {formatDate(project.weddingDate)} · {formatMoney(project.totalBudget)} budget
        </p>
      </div>

      <ProjectTabs projectId={id} />

      <div>{children}</div>
    </div>
  );
}

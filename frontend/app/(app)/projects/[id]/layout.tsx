import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft, Printer } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getProject } from "@/lib/data";
import { ApiError } from "@/lib/api";
import { countdownToWedding, formatDate, formatMoney } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ProjectTabs } from "@/components/projects/project-tabs";
import { ProjectSearch } from "@/components/search/project-search";

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
      <div className="flex flex-wrap items-start justify-between gap-4">
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
        <Link
          href={`/projects/${id}/day-of`}
          className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
        >
          <Printer />
          Day-of sheet
        </Link>
      </div>

      <div className="flex items-center justify-between gap-4 border-b border-border">
        <ProjectTabs projectId={id} />
        <ProjectSearch projectId={id} />
      </div>

      <div>{children}</div>
    </div>
  );
}

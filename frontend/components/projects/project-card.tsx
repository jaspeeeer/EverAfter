import Link from "next/link";
import { CalendarDays, User, Wallet } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDate, formatMoney } from "@/lib/format";
import type { ProjectResponse } from "@/lib/types";

export function ProjectCard({
  project,
  showPlanner = false,
}: {
  project: ProjectResponse;
  showPlanner?: boolean;
}) {
  return (
    <Link href={`/projects/${project.id}`} className="block">
      <Card className="h-full transition-shadow hover:shadow-md">
        <CardHeader>
          <CardTitle className="text-lg">{project.name}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-muted-foreground">
          <div className="flex items-center gap-2">
            <CalendarDays className="size-4 shrink-0" />
            {formatDate(project.weddingDate)}
          </div>
          <div className="flex items-center gap-2">
            <Wallet className="size-4 shrink-0" />
            {formatMoney(project.totalBudget)} budget
          </div>
          {project.ownerEmail && (
            <div className="flex items-center gap-2">
              <User className="size-4 shrink-0" />
              <span className="truncate">{project.ownerEmail}</span>
            </div>
          )}
          {showPlanner && project.plannerEmail && (
            <div className="border-t border-border pt-2 text-xs">
              Planner: {project.plannerEmail}
            </div>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}

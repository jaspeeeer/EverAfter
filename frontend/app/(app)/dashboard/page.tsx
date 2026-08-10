import { Sparkles } from "lucide-react";
import { requireUser } from "@/lib/session";
import { getProjects } from "@/lib/data";
import { isAdmin, isPlanner } from "@/lib/types";
import { ProjectCard } from "@/components/projects/project-card";
import { NewProjectButton } from "@/components/projects/new-project-button";

export default async function DashboardPage() {
  const user = await requireUser();
  const projects = await getProjects();

  const admin = isAdmin(user.roles);
  const planner = isPlanner(user.roles);

  const heading = admin
    ? "All projects"
    : planner
      ? "Your projects"
      : "Your wedding";
  const subtitle = admin
    ? "Every wedding across the platform."
    : planner
      ? "Weddings you manage."
      : "Everything for your big day, in one place.";

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{heading}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
        </div>
        {/* Role-based UI: only planners get the create-project action. */}
        {planner && <NewProjectButton />}
      </div>

      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border py-16 text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Sparkles className="size-6" />
          </div>
          <h2 className="text-lg font-semibold">
            {planner
              ? "No projects yet"
              : admin
                ? "No projects on the platform yet"
                : "No wedding project yet"}
          </h2>
          <p className="mt-1 max-w-sm text-sm text-muted-foreground">
            {planner
              ? "Create your first wedding project to start planning."
              : admin
                ? "Projects created by planners will appear here."
                : "Your planner will set up your wedding project and invite you."}
          </p>
          {planner && (
            <div className="mt-5">
              <NewProjectButton />
            </div>
          )}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} showPlanner={admin} />
          ))}
        </div>
      )}
    </div>
  );
}

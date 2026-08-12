import { getProject } from "@/lib/data";
import { ProjectSettingsForm } from "@/components/projects/project-settings-form";

export default async function ProjectSettingsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const project = await getProject(id);

  return <ProjectSettingsForm project={project} />;
}

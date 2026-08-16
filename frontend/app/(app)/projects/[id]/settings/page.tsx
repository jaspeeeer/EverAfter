import { getEntourage, getProject } from "@/lib/data";
import { ProjectSettingsForm } from "@/components/projects/project-settings-form";

export default async function ProjectSettingsPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [project, entourage] = await Promise.all([getProject(id), getEntourage(id)]);

  return <ProjectSettingsForm project={project} entourage={entourage} />;
}

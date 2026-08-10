"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { TaskStatus } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export async function createTaskAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const title = String(formData.get("title") ?? "").trim();
  const description = String(formData.get("description") ?? "").trim() || null;
  const status = (String(formData.get("status") ?? "TODO") as TaskStatus);
  const dueDate = String(formData.get("dueDate") ?? "").trim() || null;

  if (!title) return { error: "Title is required." };

  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/tasks`, {
      method: "POST",
      token,
      body: { title, description, status, dueDate },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/checklist`);
  return { ok: true };
}

/** Moves/edits a task. PUT replaces the whole task, so all fields are sent. */
export async function updateTaskAction(
  projectId: string,
  taskId: string,
  body: {
    title: string;
    description: string | null;
    status: TaskStatus;
    dueDate: string | null;
  },
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/tasks/${taskId}`, {
    method: "PUT",
    token,
    body,
  });
  revalidatePath(`/projects/${projectId}/checklist`);
}

export async function deleteTaskAction(
  projectId: string,
  taskId: string,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/tasks/${taskId}`, {
    method: "DELETE",
    token,
  });
  revalidatePath(`/projects/${projectId}/checklist`);
}

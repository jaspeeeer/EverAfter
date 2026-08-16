"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { EntourageMemberResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export async function addEntourageMemberAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const role = String(formData.get("role") ?? "").trim();
  const name = String(formData.get("name") ?? "").trim();

  if (!role || !name) return { error: "Role and name are both required." };

  try {
    const token = await getToken();
    await apiFetch<EntourageMemberResponse>(`/api/projects/${projectId}/entourage`, {
      method: "POST",
      token,
      body: { role, name },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/settings`);
  return { ok: true };
}

export async function removeEntourageMemberAction(
  projectId: string,
  memberId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/entourage/${memberId}`, {
      method: "DELETE",
      token,
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/settings`);
  return {};
}

export async function moveEntourageMemberAction(
  projectId: string,
  memberId: string,
  direction: "up" | "down",
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/entourage/${memberId}/move-${direction}`, {
      method: "PUT",
      token,
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/settings`);
  return {};
}

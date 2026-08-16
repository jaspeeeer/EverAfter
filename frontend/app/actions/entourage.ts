"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { EntourageMemberResponse, ImportFromGuestsResult } from "@/lib/types";

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

export interface ImportActionState {
  error?: string;
  ok?: string;
}

export async function importEntourageFromGuestsAction(
  projectId: string,
  _prev: ImportActionState,
  formData: FormData,
): Promise<ImportActionState> {
  const entries = formData
    .getAll("entry")
    .map(String)
    .filter(Boolean)
    .map((raw) => {
      const [guestId, roleId] = raw.split(":");
      return { guestId, roleId };
    });
  if (entries.length === 0) return { error: "Select at least one guest to import." };

  let result: ImportFromGuestsResult;
  try {
    const token = await getToken();
    result = await apiFetch<ImportFromGuestsResult>(
      `/api/projects/${projectId}/entourage/import-from-guests`,
      { method: "POST", token, body: { entries } },
    );
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/settings`);
  const parts = [`Added ${result.added}.`];
  if (result.skippedAlreadyPresent > 0) {
    parts.push(`Skipped ${result.skippedAlreadyPresent} already in the entourage.`);
  }
  if (result.skippedNotEligible > 0) {
    parts.push(`Skipped ${result.skippedNotEligible} not eligible.`);
  }
  return { ok: parts.join(" ") };
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

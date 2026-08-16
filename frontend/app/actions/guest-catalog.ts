"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export async function createGuestRoleAction(
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  const entourageEligible = formData.get("entourageEligible") === "on";
  const parentId = String(formData.get("parentId") ?? "").trim() || null;
  if (!name) return { error: "Role name is required." };
  try {
    const token = await getToken();
    await apiFetch("/api/admin/guest-roles", {
      method: "POST",
      token,
      body: { name, entourageEligible, parentId },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/guest-roles");
  return { ok: true };
}

export async function renameGuestRoleAction(
  roleId: string,
  name: string,
  active: boolean,
  entourageEligible: boolean,
  parentId: string | null,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/admin/guest-roles/${roleId}`, {
      method: "PUT",
      token,
      body: { name, active, entourageEligible, parentId },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/guest-roles");
  return {};
}

export async function deleteGuestRoleAction(roleId: string): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/admin/guest-roles/${roleId}`, { method: "DELETE", token });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/guest-roles");
  return {};
}

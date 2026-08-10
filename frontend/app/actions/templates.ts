"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { TaskResponse, VendorResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

// --- Admin authoring -------------------------------------------------------
// The editor modal keeps item rows in React state and serializes them into a
// hidden `items` JSON field; these actions parse and re-validate server-side.

interface ChecklistItemRow {
  title: string;
  description: string | null;
  daysBeforeWedding: number | null;
}

interface VendorItemRow {
  name: string;
  categoryId: string;
}

function parseItemsJson<T>(formData: FormData): T[] | null {
  try {
    const parsed = JSON.parse(String(formData.get("items") ?? "[]"));
    return Array.isArray(parsed) ? (parsed as T[]) : null;
  } catch {
    return null;
  }
}

export async function saveChecklistTemplateAction(
  templateId: string | null,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  const description = String(formData.get("description") ?? "").trim() || null;
  if (!name) return { error: "Template name is required." };

  const rows = parseItemsJson<ChecklistItemRow>(formData);
  if (!rows) return { error: "Invalid item data." };
  const items = rows
    .map((r) => ({
      title: (r.title ?? "").trim(),
      description: r.description?.trim() || null,
      daysBeforeWedding:
        r.daysBeforeWedding != null && !Number.isNaN(Number(r.daysBeforeWedding))
          ? Math.max(0, Math.floor(Number(r.daysBeforeWedding)))
          : null,
    }))
    .filter((r) => r.title !== "");
  if (items.length === 0) return { error: "Add at least one task." };

  try {
    const token = await getToken();
    await apiFetch(
      templateId ? `/api/templates/checklist/${templateId}` : "/api/templates/checklist",
      { method: templateId ? "PUT" : "POST", token, body: { name, description, items } },
    );
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath("/admin/templates");
  return { ok: true };
}

export async function saveVendorTemplateAction(
  templateId: string | null,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  const description = String(formData.get("description") ?? "").trim() || null;
  if (!name) return { error: "Template name is required." };

  const rows = parseItemsJson<VendorItemRow>(formData);
  if (!rows) return { error: "Invalid item data." };
  const items = rows
    .map((r) => ({ name: (r.name ?? "").trim(), categoryId: r.categoryId ?? "" }))
    .filter((r) => r.name !== "" && r.categoryId !== "");
  if (items.length === 0) return { error: "Add at least one vendor slot with a category." };

  try {
    const token = await getToken();
    await apiFetch(
      templateId ? `/api/templates/vendors/${templateId}` : "/api/templates/vendors",
      { method: templateId ? "PUT" : "POST", token, body: { name, description, items } },
    );
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath("/admin/templates");
  return { ok: true };
}

export async function deleteChecklistTemplateAction(templateId: string): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/templates/checklist/${templateId}`, { method: "DELETE", token });
  revalidatePath("/admin/templates");
}

export async function deleteVendorTemplateAction(templateId: string): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/templates/vendors/${templateId}`, { method: "DELETE", token });
  revalidatePath("/admin/templates");
}

// --- Applying (planner/admin) ----------------------------------------------

export async function applyChecklistTemplateAction(
  projectId: string,
  templateId: string,
): Promise<{ count?: number; error?: string }> {
  try {
    const token = await getToken();
    const created = await apiFetch<TaskResponse[]>(
      `/api/projects/${projectId}/tasks/apply-template`,
      { method: "POST", token, body: { templateId } },
    );
    revalidatePath(`/projects/${projectId}/checklist`);
    return { count: created.length };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

export async function applyVendorTemplateAction(
  projectId: string,
  templateId: string,
): Promise<{ count?: number; error?: string }> {
  try {
    const token = await getToken();
    const created = await apiFetch<VendorResponse[]>(
      `/api/projects/${projectId}/vendors/apply-template`,
      { method: "POST", token, body: { templateId } },
    );
    revalidatePath(`/projects/${projectId}/vendors`);
    return { count: created.length };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

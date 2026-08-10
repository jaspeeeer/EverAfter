"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { ProjectResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
}

export async function createProjectAction(
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  const weddingDate = String(formData.get("weddingDate") ?? "").trim() || null;
  const budgetRaw = String(formData.get("totalBudget") ?? "").trim();
  const ownerEmail = String(formData.get("ownerEmail") ?? "").trim() || null;

  if (!name) return { error: "Project name is required." };

  const totalBudget = budgetRaw ? Number(budgetRaw) : null;
  if (budgetRaw && Number.isNaN(totalBudget)) {
    return { error: "Budget must be a number." };
  }

  let created: ProjectResponse;
  try {
    const token = await getToken();
    created = await apiFetch<ProjectResponse>("/api/projects", {
      method: "POST",
      token,
      body: { name, weddingDate, totalBudget, ownerEmail },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath("/dashboard");
  redirect(`/projects/${created.id}`);
}

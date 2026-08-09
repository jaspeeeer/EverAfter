"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

function revalidateBudget(projectId: string) {
  revalidatePath(`/projects/${projectId}/budget`);
  revalidatePath(`/projects/${projectId}`); // overview shows the budget roll-up
}

export async function createExpenseAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const description = String(formData.get("description") ?? "").trim();
  const amountRaw = String(formData.get("amount") ?? "").trim();
  const categoryId = String(formData.get("categoryId") ?? "").trim();
  const vendorId = String(formData.get("vendorId") ?? "").trim() || null;
  const paid = formData.get("paid") === "on";

  if (!description) return { error: "Description is required." };
  if (!categoryId) return { error: "A category is required." };
  const amount = Number(amountRaw);
  if (!amountRaw || Number.isNaN(amount) || amount < 0) {
    return { error: "Amount must be a non-negative number." };
  }

  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/expenses`, {
      method: "POST",
      token,
      body: { description, amount, categoryId, vendorId, paid },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidateBudget(projectId);
  return { ok: true };
}

/** Form-based edit of an expense (used by the edit modal). */
export async function editExpenseAction(
  projectId: string,
  expenseId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const description = String(formData.get("description") ?? "").trim();
  const amountRaw = String(formData.get("amount") ?? "").trim();
  const categoryId = String(formData.get("categoryId") ?? "").trim();
  const vendorId = String(formData.get("vendorId") ?? "").trim() || null;
  const paid = formData.get("paid") === "on";

  if (!description) return { error: "Description is required." };
  if (!categoryId) return { error: "A category is required." };
  const amount = Number(amountRaw);
  if (!amountRaw || Number.isNaN(amount) || amount < 0) {
    return { error: "Amount must be a non-negative number." };
  }

  try {
    await updateExpenseAction(projectId, expenseId, {
      description,
      amount,
      categoryId,
      vendorId,
      paid,
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  return { ok: true };
}

export async function updateExpenseAction(
  projectId: string,
  expenseId: string,
  body: {
    description: string;
    amount: number;
    categoryId: string;
    vendorId: string | null;
    paid: boolean;
  },
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/expenses/${expenseId}`, {
    method: "PUT",
    token,
    body,
  });
  revalidateBudget(projectId);
}

export async function deleteExpenseAction(
  projectId: string,
  expenseId: string,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/expenses/${expenseId}`, {
    method: "DELETE",
    token,
  });
  revalidateBudget(projectId);
}

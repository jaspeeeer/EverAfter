"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

// --- Vendor categories (admin) ---

export async function createVendorCategoryAction(
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  if (!name) return { error: "Category name is required." };
  try {
    const token = await getToken();
    await apiFetch("/api/admin/vendor-categories", {
      method: "POST",
      token,
      body: { name },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/vendor-categories");
  return { ok: true };
}

export async function renameVendorCategoryAction(
  categoryId: string,
  name: string,
  active: boolean,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/admin/vendor-categories/${categoryId}`, {
      method: "PUT",
      token,
      body: { name, active },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/vendor-categories");
  return {};
}

export async function deleteVendorCategoryAction(
  categoryId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/admin/vendor-categories/${categoryId}`, { method: "DELETE", token });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/vendor-categories");
  return {};
}

// --- Vendor directory (admin) ---

interface DirectoryBody {
  name: string;
  categoryId: string;
  contactEmail: string | null;
  phone: string | null;
  typicalPrice: number | null;
  notes: string | null;
  active: boolean;
}

function parseDirectoryForm(formData: FormData): DirectoryBody | string {
  const name = String(formData.get("name") ?? "").trim();
  const categoryId = String(formData.get("categoryId") ?? "").trim();
  const contactEmail = String(formData.get("contactEmail") ?? "").trim() || null;
  const phone = String(formData.get("phone") ?? "").trim() || null;
  const notes = String(formData.get("notes") ?? "").trim() || null;
  const active = formData.get("active") === "on";
  const priceRaw = String(formData.get("typicalPrice") ?? "").trim();

  if (!name) return "Vendor name is required.";
  if (!categoryId) return "Please choose a category.";
  let typicalPrice: number | null = null;
  if (priceRaw) {
    const parsed = Number(priceRaw);
    if (Number.isNaN(parsed) || parsed < 0) return "Typical price must be a non-negative number.";
    typicalPrice = parsed;
  }
  return { name, categoryId, contactEmail, phone, typicalPrice, notes, active };
}

export async function saveDirectoryEntryAction(
  entryId: string | null,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const parsed = parseDirectoryForm(formData);
  if (typeof parsed === "string") return { error: parsed };

  try {
    const token = await getToken();
    await apiFetch(
      entryId ? `/api/admin/vendor-directory/${entryId}` : "/api/admin/vendor-directory",
      { method: entryId ? "PUT" : "POST", token, body: parsed },
    );
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/vendor-directory");
  return { ok: true };
}

export async function deleteDirectoryEntryAction(
  entryId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/admin/vendor-directory/${entryId}`, { method: "DELETE", token });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin/vendor-directory");
  return {};
}

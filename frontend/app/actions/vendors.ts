"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { VendorPaymentResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export interface VendorBody {
  name: string;
  categoryId: string;
  contactEmail: string | null;
  phone: string | null;
  booked: boolean;
  agreedPrice: number | null;
  /** Set to nest this vendor as an item under a package (a top-level vendor). */
  parentId: string | null;
}

// A vendor's agreed price feeds the budget, so vendor writes revalidate both tabs.
function revalidateVendor(projectId: string) {
  revalidatePath(`/projects/${projectId}/vendors`);
  revalidatePath(`/projects/${projectId}/budget`);
  revalidatePath(`/projects/${projectId}`);
}

function parseVendorForm(formData: FormData): VendorBody | string {
  const name = String(formData.get("name") ?? "").trim();
  const categoryId = String(formData.get("categoryId") ?? "").trim();
  const contactEmail = String(formData.get("contactEmail") ?? "").trim() || null;
  const phone = String(formData.get("phone") ?? "").trim() || null;
  const booked = formData.get("booked") === "on";
  const priceRaw = String(formData.get("agreedPrice") ?? "").trim();
  const parentId = String(formData.get("parentId") ?? "").trim() || null;

  if (!name) return "Vendor name is required.";
  if (!categoryId) return "Please choose a category.";
  let agreedPrice: number | null = null;
  if (priceRaw) {
    const parsed = Number(priceRaw);
    if (Number.isNaN(parsed) || parsed < 0) return "Agreed price must be a non-negative number.";
    agreedPrice = parsed;
  }
  return { name, categoryId, contactEmail, phone, booked, agreedPrice, parentId };
}

export async function createVendorAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const parsed = parseVendorForm(formData);
  if (typeof parsed === "string") return { error: parsed };

  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/vendors`, {
      method: "POST",
      token,
      body: parsed,
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidateVendor(projectId);
  return { ok: true };
}

/** Form-based edit of a vendor's details (used by the edit modal). */
export async function editVendorAction(
  projectId: string,
  vendorId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const parsed = parseVendorForm(formData);
  if (typeof parsed === "string") return { error: parsed };

  try {
    await updateVendorAction(projectId, vendorId, parsed);
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  return { ok: true };
}

export async function updateVendorAction(
  projectId: string,
  vendorId: string,
  body: VendorBody,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/vendors/${vendorId}`, {
    method: "PUT",
    token,
    body,
  });
  revalidateVendor(projectId);
}

export async function deleteVendorAction(
  projectId: string,
  vendorId: string,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/vendors/${vendorId}`, {
    method: "DELETE",
    token,
  });
  revalidateVendor(projectId);
}

/** Adds a global directory entry into this project as a new linked vendor. */
export async function addVendorFromDirectoryAction(
  projectId: string,
  directoryId: string,
): Promise<{ count?: number; error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/vendors/from-directory`, {
      method: "POST",
      token,
      body: { directoryId },
    });
    revalidateVendor(projectId);
    return { count: 1 };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

// --- Vendor payments (installments) ---

/** Loads a vendor's payment history on demand (for the payments modal). */
export async function listVendorPaymentsAction(
  projectId: string,
  vendorId: string,
): Promise<{ payments?: VendorPaymentResponse[]; error?: string }> {
  try {
    const token = await getToken();
    const payments = await apiFetch<VendorPaymentResponse[]>(
      `/api/projects/${projectId}/vendors/${vendorId}/payments`,
      { token },
    );
    return { payments };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

export async function addVendorPaymentAction(
  projectId: string,
  vendorId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const amountRaw = String(formData.get("amount") ?? "").trim();
  // The form's Planned/Paid toggle sends kind=planned or kind=paid.
  const kind = String(formData.get("kind") ?? "paid");
  const paidOn = String(formData.get("paidOn") ?? "").trim();
  const dueDate = String(formData.get("dueDate") ?? "").trim();
  const note = String(formData.get("note") ?? "").trim() || null;

  const amount = Number(amountRaw);
  if (!amountRaw || Number.isNaN(amount) || amount <= 0) {
    return { error: "Payment amount must be a positive number." };
  }
  const paid = kind !== "planned";
  if (paid && !paidOn) return { error: "A payment date is required." };
  if (!paid && !dueDate) return { error: "A due date is required for a planned installment." };

  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/vendors/${vendorId}/payments`, {
      method: "POST",
      token,
      body: {
        amount,
        paid,
        paidOn: paid ? paidOn : null,
        dueDate: paid ? null : dueDate,
        note,
      },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidateVendor(projectId);
  return { ok: true };
}

/** Flips a planned installment to paid on the given date. */
export async function markVendorPaymentPaidAction(
  projectId: string,
  vendorId: string,
  paymentId: string,
  paidOn: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(
      `/api/projects/${projectId}/vendors/${vendorId}/payments/${paymentId}/mark-paid`,
      { method: "POST", token, body: { paidOn } },
    );
    revalidateVendor(projectId);
    return {};
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

export async function deleteVendorPaymentAction(
  projectId: string,
  vendorId: string,
  paymentId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/vendors/${vendorId}/payments/${paymentId}`, {
      method: "DELETE",
      token,
    });
    revalidateVendor(projectId);
    return {};
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

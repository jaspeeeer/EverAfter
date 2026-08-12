"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type {
  Gender,
  GuestPriority,
  GuestRelationship,
  GuestResponse,
  RelatedTo,
  RsvpStatus,
} from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export interface GuestBody {
  firstName: string;
  lastName: string | null;
  title: string | null;
  gender: Gender | null;
  email: string | null;
  phone: string | null;
  rsvpStatus: RsvpStatus;
  partySize: number | null;
  dietaryNotes: string | null;
  tableNumber: number | null;
  priority: GuestPriority | null;
  relatedTo: RelatedTo | null;
  relationship: GuestRelationship | null;
  roleId: string | null;
}

/** Parses the shared guest form fields; returns an error message on invalid input. */
function parseGuestForm(formData: FormData): GuestBody | string {
  const firstName = String(formData.get("firstName") ?? "").trim();
  const lastName = String(formData.get("lastName") ?? "").trim() || null;
  const title = String(formData.get("title") ?? "").trim() || null;
  const gender = (String(formData.get("gender") ?? "").trim() || null) as Gender | null;
  const email = String(formData.get("email") ?? "").trim() || null;
  const phone = String(formData.get("phone") ?? "").trim() || null;
  const rsvpStatus = String(formData.get("rsvpStatus") ?? "PENDING") as RsvpStatus;
  // Blank party size means "just this guest" — sent through as null, not defaulted to 1.
  const partySizeRaw = String(formData.get("partySize") ?? "").trim();
  const dietaryNotes = String(formData.get("dietaryNotes") ?? "").trim() || null;
  const tableRaw = String(formData.get("tableNumber") ?? "").trim();
  // Optional planner-internal classification; a blank select submits "".
  const priority = (String(formData.get("priority") ?? "").trim() || null) as GuestPriority | null;
  const relatedTo = (String(formData.get("relatedTo") ?? "").trim() || null) as RelatedTo | null;
  const relationship = (String(formData.get("relationship") ?? "").trim() ||
    null) as GuestRelationship | null;
  const roleId = String(formData.get("roleId") ?? "").trim() || null;

  if (!firstName) return "Guest first name is required.";
  let partySize: number | null = null;
  if (partySizeRaw) {
    partySize = Number(partySizeRaw);
    if (Number.isNaN(partySize) || partySize < 1) {
      return "Party size must be at least 1.";
    }
  }
  const tableNumber = tableRaw ? Number(tableRaw) : null;
  if (tableNumber !== null && (Number.isNaN(tableNumber) || tableNumber < 1)) {
    return "Table number must be a positive number.";
  }

  return {
    firstName,
    lastName,
    title,
    gender,
    email,
    phone,
    rsvpStatus,
    partySize,
    dietaryNotes,
    tableNumber,
    priority,
    relatedTo,
    relationship,
    roleId,
  };
}

export async function createGuestAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const parsed = parseGuestForm(formData);
  if (typeof parsed === "string") return { error: parsed };

  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/guests`, {
      method: "POST",
      token,
      body: parsed,
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/guests`);
  return { ok: true };
}

/** Form-based edit of a guest's details (used by the edit modal). */
export async function editGuestAction(
  projectId: string,
  guestId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const parsed = parseGuestForm(formData);
  if (typeof parsed === "string") return { error: parsed };

  try {
    await updateGuestAction(projectId, guestId, parsed);
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  return { ok: true };
}

export async function updateGuestAction(
  projectId: string,
  guestId: string,
  body: GuestBody,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/guests/${guestId}`, {
    method: "PUT",
    token,
    body,
  });
  revalidatePath(`/projects/${projectId}/guests`);
}

export async function deleteGuestAction(
  projectId: string,
  guestId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/guests/${guestId}`, {
      method: "DELETE",
      token,
    });
    revalidatePath(`/projects/${projectId}/guests`);
    return {};
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

/** Reverses a delete within its undo window. */
export async function restoreGuestAction(
  projectId: string,
  guestId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/guests/${guestId}/restore`, {
      method: "POST",
      token,
    });
    revalidatePath(`/projects/${projectId}/guests`);
    return {};
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

/** Bulk import (CSV rows already parsed client-side). Returns how many were created. */
export async function importGuestsAction(
  projectId: string,
  rows: GuestBody[],
): Promise<{ count?: number; error?: string }> {
  if (rows.length === 0) return { error: "No valid rows found in the file." };
  try {
    const token = await getToken();
    const created = await apiFetch<GuestResponse[]>(
      `/api/projects/${projectId}/guests/import`,
      { method: "POST", token, body: rows },
    );
    revalidatePath(`/projects/${projectId}/guests`);
    return { count: created.length };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Import failed." };
  }
}

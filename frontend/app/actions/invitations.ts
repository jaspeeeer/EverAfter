"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { RsvpStatus, RsvpViewResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export async function createInvitationAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const email = String(formData.get("email") ?? "").trim();
  if (!email) return { error: "The couple's email is required." };

  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/invitations`, {
      method: "POST",
      token,
      body: { email },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}`);
  return { ok: true };
}

/** Public RSVP submission — no auth; the unguessable token IS the credential. */
export async function submitRsvpAction(
  rsvpToken: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const rsvpStatus = String(formData.get("rsvpStatus") ?? "PENDING") as RsvpStatus;
  const dietaryNotes = String(formData.get("dietaryNotes") ?? "").trim() || null;

  try {
    await apiFetch<RsvpViewResponse>(`/api/public/rsvp/${rsvpToken}`, {
      method: "PUT",
      body: { rsvpStatus, dietaryNotes },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/rsvp/${rsvpToken}`);
  return { ok: true };
}

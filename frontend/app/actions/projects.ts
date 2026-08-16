"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { ProjectResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
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

/**
 * Full replace via PUT — every field must be threaded through, or the omitted ones null out
 * server-side (this is the same gotcha called out for guests in docs/guests.md).
 */
export async function updateProjectAction(
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  const weddingDate = String(formData.get("weddingDate") ?? "").trim() || null;
  const budgetRaw = String(formData.get("totalBudget") ?? "").trim();
  const ceremonyVenueName = String(formData.get("ceremonyVenueName") ?? "").trim() || null;
  const ceremonyVenueAddress = String(formData.get("ceremonyVenueAddress") ?? "").trim() || null;
  const receptionVenueName = String(formData.get("receptionVenueName") ?? "").trim() || null;
  const receptionVenueAddress = String(formData.get("receptionVenueAddress") ?? "").trim() || null;
  const ceremonyTime = String(formData.get("ceremonyTime") ?? "").trim() || null;
  const receptionTime = String(formData.get("receptionTime") ?? "").trim() || null;
  const allowGuestPartySize = formData.get("allowGuestPartySize") === "on";
  const maxPartySizeRaw = String(formData.get("maxPartySize") ?? "").trim();
  const dressCode = String(formData.get("dressCode") ?? "").trim() || null;
  const attireNotesMen = String(formData.get("attireNotesMen") ?? "").trim() || null;
  const attireNotesWomen = String(formData.get("attireNotesWomen") ?? "").trim() || null;
  const attirePalette = String(formData.get("attirePalette") ?? "").trim() || null;
  const rsvpDeadline = String(formData.get("rsvpDeadline") ?? "").trim() || null;
  const kidsPolicy = String(formData.get("kidsPolicy") ?? "").trim() || null;
  const socialHashtag = String(formData.get("socialHashtag") ?? "").trim() || null;

  if (!name) return { error: "Project name is required." };

  const totalBudget = budgetRaw ? Number(budgetRaw) : null;
  if (budgetRaw && Number.isNaN(totalBudget)) {
    return { error: "Budget must be a number." };
  }

  const maxPartySize = maxPartySizeRaw ? Number(maxPartySizeRaw) : null;
  if (maxPartySizeRaw && Number.isNaN(maxPartySize)) {
    return { error: "Max party size must be a number." };
  }

  try {
    const token = await getToken();
    await apiFetch<ProjectResponse>(`/api/projects/${projectId}`, {
      method: "PUT",
      token,
      body: {
        name,
        weddingDate,
        totalBudget,
        ceremonyVenueName,
        ceremonyVenueAddress,
        receptionVenueName,
        receptionVenueAddress,
        ceremonyTime,
        receptionTime,
        allowGuestPartySize,
        maxPartySize,
        dressCode,
        attireNotesMen,
        attireNotesWomen,
        attirePalette,
        rsvpDeadline,
        kidsPolicy,
        socialHashtag,
      },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}`);
  revalidatePath(`/projects/${projectId}/settings`);
  return { ok: true };
}

/** The three independent, single-photo slots a project can carry. See docs/project-photos.md. */
export type ProjectPhotoSlot =
  | "cover"
  | "ceremony-photo"
  | "reception-photo"
  | "attire-men-photo"
  | "attire-women-photo";

/**
 * Uploads (or replaces) one of the project's photo slots. `formData` must contain a "file" entry
 * (a browser File) — the caller's <input type="file" name="file"> already provides this.
 */
export async function setProjectPhotoAction(
  slot: ProjectPhotoSlot,
  projectId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const file = formData.get("file");
  if (!(file instanceof File) || file.size === 0) {
    return { error: "Choose an image to upload." };
  }

  const upload = new FormData();
  upload.set("file", file);

  try {
    const token = await getToken();
    await apiFetch<ProjectResponse>(`/api/projects/${projectId}/${slot}`, {
      method: "POST",
      token,
      body: upload,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      const message =
        error.status === 415
          ? "That file type isn't supported. Use a JPEG, PNG, GIF, or WebP image."
          : error.status === 413
            ? "That file is too large."
            : error.message;
      return { error: message };
    }
    return { error: "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/settings`);
  return { ok: true };
}

export async function removeProjectPhotoAction(
  slot: ProjectPhotoSlot,
  projectId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/${slot}`, { method: "DELETE", token });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/settings`);
  return {};
}

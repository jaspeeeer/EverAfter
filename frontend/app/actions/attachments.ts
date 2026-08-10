"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getAttachments } from "@/lib/data";
import { getToken } from "@/lib/session";
import type { AttachmentOwnerType, AttachmentResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
  attachment?: AttachmentResponse;
}

/** Loads an owner's attachments on demand (mirrors {@code listVendorPaymentsAction}). */
export async function listAttachmentsAction(
  projectId: string,
  ownerType: AttachmentOwnerType,
  ownerId: string,
): Promise<{ attachments?: AttachmentResponse[]; error?: string }> {
  try {
    const attachments = await getAttachments(projectId, ownerType, ownerId);
    return { attachments };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

function ownerPathSegment(ownerType: AttachmentOwnerType): string {
  switch (ownerType) {
    case "VENDOR":
      return "vendors";
    case "VENDOR_PAYMENT":
      return "vendor-payments";
    case "EXPENSE":
      return "expenses";
  }
}

/**
 * Uploads a file against a vendor, vendor payment, or expense. `formData` must contain a "file"
 * entry (a browser File) — the caller's <input type="file" name="file"> already provides this.
 */
export async function uploadAttachmentAction(
  projectId: string,
  ownerType: AttachmentOwnerType,
  ownerId: string,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const file = formData.get("file");
  if (!(file instanceof File) || file.size === 0) {
    return { error: "Choose a file to upload." };
  }

  const upload = new FormData();
  upload.set("file", file);

  try {
    const token = await getToken();
    const attachment = await apiFetch<AttachmentResponse>(
      `/api/projects/${projectId}/${ownerPathSegment(ownerType)}/${ownerId}/attachments`,
      { method: "POST", token, body: upload },
    );
    revalidatePath(`/projects/${projectId}/vendors`);
    revalidatePath(`/projects/${projectId}/budget`);
    return { ok: true, attachment };
  } catch (error) {
    if (error instanceof ApiError) {
      const message =
        error.status === 415
          ? "That file type isn't supported. Use a PDF or an image (JPEG, PNG, GIF, WebP)."
          : error.status === 413
            ? "That file is too large."
            : error.message;
      return { error: message };
    }
    return { error: "Something went wrong." };
  }
}

export async function deleteAttachmentAction(
  projectId: string,
  attachmentId: string,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/projects/${projectId}/attachments/${attachmentId}`, {
      method: "DELETE",
      token,
    });
    revalidatePath(`/projects/${projectId}/vendors`);
    revalidatePath(`/projects/${projectId}/budget`);
    return {};
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

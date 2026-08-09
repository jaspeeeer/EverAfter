"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { TimelineEventResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

interface EventBody {
  title: string;
  description: string | null;
  location: string | null;
  startTime: string; // "HH:mm"
  endTime: string | null;
  vendorIds: string[];
}

/** Create-or-edit from the event form modal. */
export async function saveTimelineEventAction(
  projectId: string,
  eventId: string | null,
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const title = String(formData.get("title") ?? "").trim();
  const description = String(formData.get("description") ?? "").trim() || null;
  const location = String(formData.get("location") ?? "").trim() || null;
  const startTime = String(formData.get("startTime") ?? "").trim();
  const endTime = String(formData.get("endTime") ?? "").trim() || null;
  const vendorIds = formData.getAll("vendorIds").map(String);

  if (!title) return { error: "Event title is required." };
  if (!startTime) return { error: "Start time is required." };

  try {
    const token = await getToken();
    await apiFetch(
      eventId
        ? `/api/projects/${projectId}/timeline/${eventId}`
        : `/api/projects/${projectId}/timeline`,
      {
        method: eventId ? "PUT" : "POST",
        token,
        body: { title, description, location, startTime, endTime, vendorIds },
      },
    );
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }

  revalidatePath(`/projects/${projectId}/timeline`);
  return { ok: true };
}

/** Full-body update used by drag-to-reschedule (PUT replaces everything). */
export async function updateTimelineEventAction(
  projectId: string,
  eventId: string,
  body: EventBody,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/timeline/${eventId}`, {
    method: "PUT",
    token,
    body,
  });
  revalidatePath(`/projects/${projectId}/timeline`);
}

export async function deleteTimelineEventAction(
  projectId: string,
  eventId: string,
): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/projects/${projectId}/timeline/${eventId}`, {
    method: "DELETE",
    token,
  });
  revalidatePath(`/projects/${projectId}/timeline`);
}

/** Quick-start: seeds the typical day (only valid while the timeline is empty). */
export async function applyTypicalDayAction(
  projectId: string,
): Promise<{ count?: number; error?: string }> {
  try {
    const token = await getToken();
    const created = await apiFetch<TimelineEventResponse[]>(
      `/api/projects/${projectId}/timeline/typical-day`,
      { method: "POST", token },
    );
    revalidatePath(`/projects/${projectId}/timeline`);
    return { count: created.length };
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
}

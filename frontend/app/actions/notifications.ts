"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { NotificationPreferencesResponse } from "@/lib/types";

export interface ActionState {
  error?: string;
  ok?: boolean;
}

export async function markNotificationReadAction(id: string): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/notifications/${id}/read`, { method: "POST", token });
  revalidatePath("/", "layout");
}

export async function markAllNotificationsReadAction(): Promise<void> {
  const token = await getToken();
  await apiFetch(`/api/notifications/read-all`, { method: "POST", token });
  revalidatePath("/", "layout");
}

export async function updateNotificationPreferencesAction(
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const body = {
    inappTaskDue: formData.get("inappTaskDue") === "on",
    inappPaymentDue: formData.get("inappPaymentDue") === "on",
    inappCountdown: formData.get("inappCountdown") === "on",
  };
  try {
    const token = await getToken();
    await apiFetch<NotificationPreferencesResponse>(
      `/api/notification-preferences`,
      { method: "PUT", token, body },
    );
  } catch (error) {
    return {
      error: error instanceof ApiError ? error.message : "Something went wrong.",
    };
  }
  revalidatePath("/settings/notifications");
  return { ok: true };
}

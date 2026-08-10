"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";

export async function setUserEnabledAction(
  userId: string,
  enabled: boolean,
): Promise<{ error?: string }> {
  try {
    const token = await getToken();
    await apiFetch(`/api/admin/users/${userId}/enabled`, {
      method: "PUT",
      token,
      body: { enabled },
    });
  } catch (error) {
    return { error: error instanceof ApiError ? error.message : "Something went wrong." };
  }
  revalidatePath("/admin");
  return {};
}

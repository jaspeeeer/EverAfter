"use server";

import { redirect } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/api";
import { clearTokenCookie, setTokenCookie } from "@/lib/session";
import type { AuthResponse, RoleName } from "@/lib/types";

export interface AuthFormState {
  error?: string;
}

const REGISTERABLE_ROLES: RoleName[] = ["ROLE_PLANNER", "ROLE_USER"];

export async function loginAction(
  _prev: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  if (!email || !password) {
    return { error: "Email and password are required." };
  }

  try {
    const auth = await apiFetch<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: { email, password },
    });
    await setTokenCookie(auth.token);
  } catch (error) {
    if (error instanceof ApiError) {
      return {
        error:
          error.status === 401
            ? "Invalid email or password."
            : error.message,
      };
    }
    return { error: "Unable to reach the server. Is the backend running?" };
  }

  redirect("/dashboard");
}

export async function registerAction(
  _prev: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  const firstName = String(formData.get("firstName") ?? "").trim();
  const lastName = String(formData.get("lastName") ?? "").trim();
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const role = String(formData.get("role") ?? "") as RoleName;
  const inviteToken = String(formData.get("inviteToken") ?? "").trim() || null;

  if (!firstName || !lastName || !email || !password) {
    return { error: "All fields are required." };
  }
  if (password.length < 8) {
    return { error: "Password must be at least 8 characters." };
  }
  if (!REGISTERABLE_ROLES.includes(role)) {
    return { error: "Please choose an account type." };
  }

  try {
    const auth = await apiFetch<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: { firstName, lastName, email, password, role, inviteToken },
    });
    await setTokenCookie(auth.token);
  } catch (error) {
    if (error instanceof ApiError) {
      return {
        error:
          error.status === 409
            ? "That email is already registered."
            : error.message,
      };
    }
    return { error: "Unable to reach the server. Is the backend running?" };
  }

  redirect("/dashboard");
}

export async function logoutAction(): Promise<void> {
  await clearTokenCookie();
  redirect("/login");
}

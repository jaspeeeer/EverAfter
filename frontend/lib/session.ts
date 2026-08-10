import "server-only";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import { apiFetch, ApiError } from "./api";
import { TOKEN_COOKIE } from "./constants";
import type { UserResponse } from "./types";

export async function getToken(): Promise<string | undefined> {
  const store = await cookies();
  return store.get(TOKEN_COOKIE)?.value;
}

export async function setTokenCookie(token: string): Promise<void> {
  const store = await cookies();
  store.set(TOKEN_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 60 * 60 * 24, // 24h, matches the backend token lifetime
  });
}

export async function clearTokenCookie(): Promise<void> {
  const store = await cookies();
  store.delete(TOKEN_COOKIE);
}

/**
 * The authenticated user for this request, or null. Cached per request so multiple components
 * can call it without repeated /me round-trips. A 401/403 (missing/expired token) yields null.
 */
export const getCurrentUser = cache(async (): Promise<UserResponse | null> => {
  const token = await getToken();
  if (!token) return null;
  try {
    return await apiFetch<UserResponse>("/api/auth/me", { token });
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return null;
    }
    throw error;
  }
});

/** Returns the current user or redirects to /login. */
export async function requireUser(): Promise<UserResponse> {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  return user;
}

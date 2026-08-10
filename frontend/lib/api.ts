import "server-only";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

interface ApiOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  token?: string;
}

/**
 * Server-side fetch wrapper for the Spring Boot API. All calls originate from the Next server
 * (never the browser), so the JWT is attached here and there is no cross-origin/CORS concern.
 * Non-2xx responses are surfaced as {@link ApiError}, mapping the backend's RFC-7807 body.
 */
export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { method = "GET", body, token } = options;

  const headers: Record<string, string> = {};
  // FormData (file uploads) must NOT get a manual Content-Type — fetch sets
  // "multipart/form-data; boundary=..." itself, and overriding it drops the boundary.
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isFormData) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : isFormData ? (body as FormData) : JSON.stringify(body),
    cache: "no-store",
  });

  if (!res.ok) {
    let message = res.statusText;
    let fieldErrors: Record<string, string> | undefined;
    try {
      const problem = await res.json();
      if (problem?.detail) message = problem.detail;
      if (problem?.errors) fieldErrors = problem.errors;
    } catch {
      // non-JSON error body — keep statusText
    }
    throw new ApiError(res.status, message, fieldErrors);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

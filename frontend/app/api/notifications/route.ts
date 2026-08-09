import { NextResponse } from "next/server";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";
import type { NotificationResponse } from "@/lib/types";

export async function GET(request: Request) {
  const token = await getToken();
  if (!token) return NextResponse.json([], { status: 401 });
  const url = new URL(request.url);
  const qs = url.search;
  try {
    const list = await apiFetch<NotificationResponse[]>(
      `/api/notifications${qs}`,
      { token },
    );
    return NextResponse.json(list);
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 500;
    return NextResponse.json([], { status });
  }
}

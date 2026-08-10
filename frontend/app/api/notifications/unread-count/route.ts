import { NextResponse } from "next/server";
import { apiFetch, ApiError } from "@/lib/api";
import { getToken } from "@/lib/session";

export async function GET() {
  const token = await getToken();
  if (!token) return NextResponse.json({ count: 0 }, { status: 401 });
  try {
    const body = await apiFetch<{ count: number }>(
      `/api/notifications/unread-count`,
      { token },
    );
    return NextResponse.json(body);
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 500;
    return NextResponse.json({ count: 0 }, { status });
  }
}

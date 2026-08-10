import { NextResponse } from "next/server";
import { getToken } from "@/lib/session";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

/**
 * Streams an attachment's bytes from the Spring backend. A direct `<a href>` to the backend
 * can't carry the httpOnly `wp_token` cookie, so downloads route through this authenticated
 * proxy instead — it reads the JWT server-side and forwards the backend's Content-Type /
 * Content-Disposition unchanged.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ projectId: string; id: string }> },
) {
  const { projectId, id } = await params;
  const token = await getToken();
  if (!token) return new NextResponse(null, { status: 401 });

  const backendRes = await fetch(
    `${API_BASE}/api/projects/${projectId}/attachments/${id}/download`,
    { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" },
  );

  if (!backendRes.ok || !backendRes.body) {
    return new NextResponse(null, { status: backendRes.status });
  }

  const headers = new Headers();
  for (const header of ["content-type", "content-disposition", "content-length"]) {
    const value = backendRes.headers.get(header);
    if (value) headers.set(header, value);
  }

  return new NextResponse(backendRes.body, { status: 200, headers });
}

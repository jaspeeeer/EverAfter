import { NextResponse } from "next/server";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

/**
 * Streams a project's cover photo from the Spring backend for the public invitation page.
 * No auth: the RSVP token itself is the credential, same as every other `/api/public/**` route.
 * The browser never calls :8080 directly (no CORS config exists), so an `<img src>` on the
 * public RSVP page routes through this proxy the same way the authenticated attachment proxy
 * does for the app's own pages.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ token: string }> },
) {
  const { token } = await params;

  const backendRes = await fetch(`${API_BASE}/api/public/rsvp/${token}/cover`, {
    cache: "no-store",
  });

  if (!backendRes.ok || !backendRes.body) {
    return new NextResponse(null, { status: backendRes.status });
  }

  const headers = new Headers();
  for (const header of ["content-type", "content-length", "cache-control"]) {
    const value = backendRes.headers.get(header);
    if (value) headers.set(header, value);
  }

  return new NextResponse(backendRes.body, { status: 200, headers });
}

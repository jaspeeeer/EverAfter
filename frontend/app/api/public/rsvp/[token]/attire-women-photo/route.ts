import { NextResponse } from "next/server";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

/**
 * Streams the women's attire reference photo from the Spring backend for the public invitation
 * page. Same reasoning as the cover photo proxy (`../cover/route.ts`): the browser never calls
 * :8080 directly, so an `<img src>` on the public RSVP page routes through this proxy instead.
 * No auth: the RSVP token itself is the credential, same as every other `/api/public/**` route.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ token: string }> },
) {
  const { token } = await params;

  const backendRes = await fetch(`${API_BASE}/api/public/rsvp/${token}/attire-women-photo`, {
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

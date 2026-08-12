import { NextResponse } from "next/server";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

/**
 * Streams the "Add to calendar" .ics file from the Spring backend. Same reasoning as the cover
 * photo proxy (`app/api/public/rsvp/[token]/cover/route.ts`): the browser never calls :8080
 * directly, so a plain `<a href>` to the backend wouldn't work even though this route needs no
 * auth of its own — the RSVP token is the only credential, matching every other public route.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ token: string }> },
) {
  const { token } = await params;

  const backendRes = await fetch(`${API_BASE}/api/public/rsvp/${token}/calendar.ics`, {
    cache: "no-store",
  });

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

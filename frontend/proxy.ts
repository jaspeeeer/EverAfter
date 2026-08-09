import { NextResponse, type NextRequest } from "next/server";
import { TOKEN_COOKIE } from "@/lib/constants";

const PUBLIC_PATHS = ["/login", "/register", "/rsvp"];

/**
 * Coarse route protection at the edge (Next 16 "proxy", formerly "middleware"): requests without
 * a token cookie are bounced to /login. Full token validation happens server-side in
 * `requireUser()` (which calls the API), so an expired/invalid token still results in a redirect
 * there.
 *
 * Note: we deliberately do NOT redirect token-bearing requests away from /login. The proxy can't
 * verify the token, so doing so would loop an expired cookie between /login and /dashboard. The
 * login page renders fine for everyone; the login action simply overwrites the cookie.
 */
export function proxy(req: NextRequest) {
  const token = req.cookies.get(TOKEN_COOKIE)?.value;
  const { pathname } = req.nextUrl;
  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));

  if (!token && !isPublic) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    url.search = pathname === "/" ? "" : `?next=${encodeURIComponent(pathname)}`;
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  // Run on everything except Next internals and static assets.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)"],
};

import type { Metadata } from "next";
import { headers } from "next/headers";
import { notFound } from "next/navigation";
import { CalendarPlus, Heart, MapPin } from "lucide-react";
import { getPublicRsvp } from "@/lib/data";
import { ApiError } from "@/lib/api";
import { formatDate, formatTime } from "@/lib/format";
import { RsvpForm } from "@/components/rsvp/rsvp-form";

/**
 * The page's own origin, as seen by whoever is requesting it (browser or social-media scraper) —
 * read from the request headers rather than an env var, since this is the one place in the app
 * that needs an *absolute* URL (an `og:image` scraper fetches it directly, server-to-server, so a
 * relative path won't resolve for it the way it does for a same-origin `<img>`).
 */
async function currentOrigin(): Promise<string> {
  const h = await headers();
  const host = h.get("host") ?? "localhost:3000";
  const proto = h.get("x-forwarded-proto") ?? "https";
  return `${proto}://${host}`;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ token: string }>;
}): Promise<Metadata> {
  const { token } = await params;
  try {
    const rsvp = await getPublicRsvp(token);
    const title = `You're invited to ${rsvp.projectName}`;
    const description = rsvp.weddingDate
      ? `${rsvp.projectName} — ${formatDate(rsvp.weddingDate)}`
      : rsvp.projectName;
    const images = rsvp.hasCover
      ? [
          {
            url: `${await currentOrigin()}/api/public/rsvp/${token}/cover`,
            width: 1200,
            height: 630,
            alt: rsvp.projectName,
          },
        ]
      : [];

    return {
      title,
      description,
      openGraph: { title, description, type: "website", images },
      twitter: {
        card: images.length > 0 ? "summary_large_image" : "summary",
        title,
        description,
        images: images.map((i) => i.url),
      },
    };
  } catch {
    // An unknown/malformed token still 404s normally when the page itself renders — metadata
    // generation just needs to not throw first.
    return { title: "Invitation" };
  }
}

/**
 * Public, no-login RSVP page. The unguessable token in the URL identifies the guest; everything
 * else (auth pages, app shell) stays out of this route.
 */
export default async function RsvpPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;

  let rsvp;
  try {
    rsvp = await getPublicRsvp(token);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
      notFound();
    }
    throw error;
  }

  const showVenue =
    rsvp.venueName || rsvp.venueAddress || rsvp.ceremonyTime || rsvp.receptionTime;
  const directionsHref = rsvp.venueAddress
    ? `https://maps.google.com/?q=${encodeURIComponent(rsvp.venueAddress)}`
    : null;
  const addToCalendar = rsvp.weddingDate && (
    <a
      href={`/api/public/rsvp/${token}/calendar.ics`}
      download
      className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
    >
      <CalendarPlus className="size-4" />
      Add to calendar
    </a>
  );

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {rsvp.hasCover && (
          // Plain <img>, matching the rest of the app (see docs/attachments.md) — this route is
          // public anyway, so next/image's auth-cookie limitation doesn't even apply here.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={`/api/public/rsvp/${token}/cover`}
            alt=""
            className="mb-6 h-40 w-full rounded-xl object-cover shadow-sm"
          />
        )}
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Heart className="size-6" />
          </div>
          <p className="text-sm uppercase tracking-widest text-muted-foreground">
            You&apos;re invited to
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight">{rsvp.projectName}</h1>
          {rsvp.weddingDate && (
            <p className="mt-1 text-sm text-muted-foreground">
              {formatDate(rsvp.weddingDate)}
            </p>
          )}
        </div>

        {showVenue && (
          <div className="mb-6 rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm">
            <h2 className="mb-3 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              When &amp; where
            </h2>
            <div className="space-y-2 text-sm">
              {rsvp.venueName && (
                <p className="font-medium text-foreground">{rsvp.venueName}</p>
              )}
              {rsvp.venueAddress && (
                <p className="text-muted-foreground">{rsvp.venueAddress}</p>
              )}
              {(rsvp.ceremonyTime || rsvp.receptionTime) && (
                <ul className="mt-2 space-y-1 text-muted-foreground">
                  {rsvp.ceremonyTime && (
                    <li>
                      <span className="font-medium text-foreground">Ceremony</span> ·{" "}
                      {formatTime(rsvp.ceremonyTime)}
                    </li>
                  )}
                  {rsvp.receptionTime && (
                    <li>
                      <span className="font-medium text-foreground">Reception</span> ·{" "}
                      {formatTime(rsvp.receptionTime)}
                    </li>
                  )}
                </ul>
              )}
              {directionsHref && (
                <a
                  href={directionsHref}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
                >
                  <MapPin className="size-4" />
                  Get directions
                </a>
              )}
              {addToCalendar}
            </div>
          </div>
        )}

        {!showVenue && addToCalendar && (
          <div className="mb-6 flex justify-center">{addToCalendar}</div>
        )}

        <RsvpForm token={token} rsvp={rsvp} />
      </div>
    </div>
  );
}

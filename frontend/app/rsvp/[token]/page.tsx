import type { ReactNode } from "react";
import type { Metadata } from "next";
import { headers } from "next/headers";
import { notFound } from "next/navigation";
import { CalendarPlus, Hash, Heart } from "lucide-react";
import { getPublicRsvp } from "@/lib/data";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { AttireSection } from "@/components/rsvp/attire-section";
import { EntourageSection } from "@/components/rsvp/entourage-section";
import { RsvpForm } from "@/components/rsvp/rsvp-form";
import { VenueSection } from "@/components/rsvp/venue-section";

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

  const hasCeremony = Boolean(
    rsvp.ceremonyVenueName || rsvp.ceremonyVenueAddress || rsvp.ceremonyTime || rsvp.hasCeremonyPhoto,
  );
  const hasReception = Boolean(
    rsvp.receptionVenueName || rsvp.receptionVenueAddress || rsvp.receptionTime || rsvp.hasReceptionPhoto,
  );
  const hasAttire = Boolean(
    rsvp.dressCode || rsvp.attireNotesMen || rsvp.attireNotesWomen || rsvp.attirePalette,
  );
  const hasEntourage = rsvp.entourage.length > 0;

  // Rendered in this order, with a divider between whichever adjacent pair are both present —
  // a project that only set up some of these doesn't leave gaps or dangling dividers.
  const sections: Array<{ key: string; node: ReactNode }> = [];
  if (hasCeremony) {
    sections.push({
      key: "ceremony",
      node: (
        <VenueSection
          kind="Ceremony"
          name={rsvp.ceremonyVenueName}
          address={rsvp.ceremonyVenueAddress}
          time={rsvp.ceremonyTime}
          photoUrl={rsvp.hasCeremonyPhoto ? `/api/public/rsvp/${token}/ceremony-photo` : null}
        />
      ),
    });
  }
  if (hasReception) {
    sections.push({
      key: "reception",
      node: (
        <VenueSection
          kind="Reception"
          name={rsvp.receptionVenueName}
          address={rsvp.receptionVenueAddress}
          time={rsvp.receptionTime}
          photoUrl={rsvp.hasReceptionPhoto ? `/api/public/rsvp/${token}/reception-photo` : null}
        />
      ),
    });
  }
  if (hasAttire) {
    sections.push({
      key: "attire",
      node: (
        <AttireSection
          dressCode={rsvp.dressCode}
          notesMen={rsvp.attireNotesMen}
          notesWomen={rsvp.attireNotesWomen}
          palette={rsvp.attirePalette}
        />
      ),
    });
  }
  if (hasEntourage) {
    sections.push({ key: "entourage", node: <EntourageSection members={rsvp.entourage} /> });
  }

  return (
    <div className="flex min-h-screen justify-center px-4 py-12 sm:py-16">
      <div className="w-full max-w-2xl">
        {rsvp.hasCover && (
          // Plain <img>, matching the rest of the app (see docs/attachments.md) — this route is
          // public anyway, so next/image's auth-cookie limitation doesn't even apply here.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={`/api/public/rsvp/${token}/cover`}
            alt=""
            className="mb-10 h-56 w-full rounded-xl object-cover shadow-sm sm:h-72"
          />
        )}

        <div className="mb-10 flex flex-col items-center text-center">
          <div className="mb-4 flex size-14 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Heart className="size-7" />
          </div>
          <p className="text-sm uppercase tracking-widest text-muted-foreground">
            You&apos;re invited to
          </p>
          <h1 className="mt-2 text-4xl font-bold tracking-tight sm:text-5xl">
            {rsvp.projectName}
          </h1>
          {rsvp.weddingDate && (
            <p className="mt-2 text-base text-muted-foreground">{formatDate(rsvp.weddingDate)}</p>
          )}
          {rsvp.weddingDate && (
            <a
              href={`/api/public/rsvp/${token}/calendar.ics`}
              download
              className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
            >
              <CalendarPlus className="size-4" />
              Add to calendar
            </a>
          )}
          {rsvp.rsvpDeadline && (
            <p className="mt-3 text-sm text-muted-foreground">
              Please RSVP by {formatDate(rsvp.rsvpDeadline)}
            </p>
          )}
          {rsvp.kidsPolicy && (
            <p className="mt-1 text-sm text-muted-foreground">{rsvp.kidsPolicy}</p>
          )}
          {rsvp.socialHashtag && (
            <p className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-primary">
              <Hash className="size-3.5" />
              {rsvp.socialHashtag}
            </p>
          )}
        </div>

        {sections.map((section, index) => (
          <div key={section.key}>
            {index > 0 && <div className="mx-auto mb-10 h-px w-24 bg-border" />}
            <div className="mb-10">{section.node}</div>
          </div>
        ))}

        <RsvpForm token={token} rsvp={rsvp} />
      </div>
    </div>
  );
}

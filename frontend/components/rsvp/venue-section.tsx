import { MapPin } from "lucide-react";
import { formatTime } from "@/lib/format";
import { VenueMap } from "./venue-map";

/**
 * One location's worth of invitation content — reused for the Ceremony and Reception sections.
 * Renders nothing when the location has no data at all, so a project that only set up one venue
 * doesn't leave an empty section on the page.
 */
export function VenueSection({
  kind,
  name,
  address,
  time,
  photoUrl,
}: {
  kind: "Ceremony" | "Reception";
  name: string | null;
  address: string | null;
  time: string | null;
  photoUrl: string | null;
}) {
  if (!name && !address && !time && !photoUrl) return null;

  const directionsHref = address
    ? `https://maps.google.com/?q=${encodeURIComponent(address)}`
    : null;

  const details = (
    <div className="flex flex-col gap-4">
      <div>
        {name && <p className="text-lg font-medium text-foreground">{name}</p>}
        {address && <p className="mt-0.5 text-sm text-muted-foreground">{address}</p>}
        {time && <p className="mt-2 text-sm text-muted-foreground">{formatTime(time)}</p>}
      </div>
      <VenueMap address={address} title={`${kind} location${name ? `: ${name}` : ""}`} />
      {directionsHref && (
        <a
          href={directionsHref}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
        >
          <MapPin className="size-4" />
          Get directions
        </a>
      )}
    </div>
  );

  return (
    <section>
      <h2 className="mb-4 text-2xl font-semibold tracking-tight text-foreground">{kind}</h2>
      {photoUrl ? (
        <div className="grid gap-6 sm:grid-cols-2">
          {/* Plain <img>, matching the rest of the app (see docs/attachments.md) — this route is
              public anyway, so next/image's auth-cookie limitation doesn't even apply here. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={photoUrl}
            alt=""
            className="aspect-[4/3] w-full rounded-xl object-cover shadow-sm"
          />
          {details}
        </div>
      ) : (
        details
      )}
    </section>
  );
}

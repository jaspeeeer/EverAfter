/**
 * An embedded, interactive Google Map for a single venue address — no API key needed via the
 * `output=embed` query form. This only accepts one query per embed (no multi-pin support without
 * a paid API), which is why the ceremony and reception each get their own map rather than one
 * combined one.
 */
export function VenueMap({ address, title }: { address: string | null; title: string }) {
  if (!address) return null;

  return (
    <div className="aspect-[4/3] overflow-hidden rounded-xl border border-border">
      <iframe
        src={`https://www.google.com/maps?q=${encodeURIComponent(address)}&output=embed`}
        title={title}
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        className="size-full border-0"
      />
    </div>
  );
}

/**
 * Dress-code guidance for the invitation — a short label, separate notes for men and women
 * (each with an optional reference photo above the notes), and a suggested color palette.
 * Renders nothing when none of the six fields are set, same "no data, no section" rule as
 * VenueSection.
 */
export function AttireSection({
  dressCode,
  notesMen,
  notesWomen,
  palette,
  menPhotoUrl,
  womenPhotoUrl,
}: {
  dressCode: string | null;
  notesMen: string | null;
  notesWomen: string | null;
  palette: string | null;
  menPhotoUrl: string | null;
  womenPhotoUrl: string | null;
}) {
  const colors = palette
    ? palette
        .split(",")
        .map((c) => c.trim())
        .filter(Boolean)
    : [];

  if (
    !dressCode &&
    !notesMen &&
    !notesWomen &&
    !menPhotoUrl &&
    !womenPhotoUrl &&
    colors.length === 0
  ) {
    return null;
  }

  const hasMenColumn = Boolean(notesMen || menPhotoUrl);
  const hasWomenColumn = Boolean(notesWomen || womenPhotoUrl);

  return (
    <section>
      <h2 className="mb-4 text-2xl font-semibold tracking-tight text-foreground">Attire</h2>
      <div className="flex flex-col gap-4">
        {dressCode && <p className="text-lg font-medium text-foreground">{dressCode}</p>}
        {(hasMenColumn || hasWomenColumn) && (
          <div className="grid gap-6 sm:grid-cols-2">
            {hasMenColumn && (
              <div className="space-y-2">
                {menPhotoUrl && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={menPhotoUrl}
                    alt=""
                    className="aspect-[4/3] w-full rounded-xl object-cover shadow-sm"
                  />
                )}
                <p className="text-sm font-medium text-foreground">Men</p>
                {notesMen && (
                  <p className="text-sm text-muted-foreground">{notesMen}</p>
                )}
              </div>
            )}
            {hasWomenColumn && (
              <div className="space-y-2">
                {womenPhotoUrl && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={womenPhotoUrl}
                    alt=""
                    className="aspect-[4/3] w-full rounded-xl object-cover shadow-sm"
                  />
                )}
                <p className="text-sm font-medium text-foreground">Women</p>
                {notesWomen && (
                  <p className="text-sm text-muted-foreground">{notesWomen}</p>
                )}
              </div>
            )}
          </div>
        )}
        {colors.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            {colors.map((color) => (
              <span
                key={color}
                role="img"
                aria-label={`Suggested color ${color}`}
                title={color}
                className="size-8 rounded-full border border-border shadow-sm"
                style={{ backgroundColor: color }}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

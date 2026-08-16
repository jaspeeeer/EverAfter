/**
 * Dress-code guidance for the invitation — a short label, separate notes for men and women, and
 * a suggested color palette. Renders nothing when none of the four fields are set, same
 * "no data, no section" rule as VenueSection.
 */
export function AttireSection({
  dressCode,
  notesMen,
  notesWomen,
  palette,
}: {
  dressCode: string | null;
  notesMen: string | null;
  notesWomen: string | null;
  palette: string | null;
}) {
  const colors = palette
    ? palette
        .split(",")
        .map((c) => c.trim())
        .filter(Boolean)
    : [];

  if (!dressCode && !notesMen && !notesWomen && colors.length === 0) return null;

  return (
    <section>
      <h2 className="mb-4 text-2xl font-semibold tracking-tight text-foreground">Attire</h2>
      <div className="flex flex-col gap-4">
        {dressCode && <p className="text-lg font-medium text-foreground">{dressCode}</p>}
        {(notesMen || notesWomen) && (
          <div className="grid gap-6 sm:grid-cols-2">
            {notesMen && (
              <div>
                <p className="text-sm font-medium text-foreground">Men</p>
                <p className="mt-1 text-sm text-muted-foreground">{notesMen}</p>
              </div>
            )}
            {notesWomen && (
              <div>
                <p className="text-sm font-medium text-foreground">Women</p>
                <p className="mt-1 text-sm text-muted-foreground">{notesWomen}</p>
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

import type { PublicEntourageMember } from "@/lib/types";

/** The wedding party, in the order the couple set in Settings. Renders nothing when empty. */
export function EntourageSection({ members }: { members: PublicEntourageMember[] }) {
  if (members.length === 0) return null;

  return (
    <section>
      <h2 className="mb-4 text-2xl font-semibold tracking-tight text-foreground">Entourage</h2>
      <ul className="grid gap-2 sm:grid-cols-2">
        {members.map((member, index) => (
          <li key={index} className="text-sm">
            <span className="font-medium text-foreground">{member.role}</span>
            <span className="text-muted-foreground"> — {member.name}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

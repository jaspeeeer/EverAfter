import type {
  Gender,
  GuestPriority,
  GuestRelationship,
  GuestResponse,
  GuestRoleResponse,
  RelatedTo,
  RsvpStatus,
} from "./types";

const HEADER = [
  "firstName",
  "lastName",
  "title",
  "gender",
  "email",
  "phone",
  "rsvpStatus",
  "partySize",
  "dietaryNotes",
  "tableNumber",
  "priority",
  "relatedTo",
  "relationship",
  "role",
] as const;

const RSVP_VALUES: RsvpStatus[] = ["PENDING", "ATTENDING", "DECLINED", "MAYBE"];
const GENDER_VALUES: Gender[] = ["MALE", "FEMALE", "OTHER"];
const PRIORITY_VALUES: GuestPriority[] = ["A", "B", "C"];
const RELATED_TO_VALUES: RelatedTo[] = ["GROOM", "BRIDE"];
const RELATIONSHIP_VALUES: GuestRelationship[] = [
  "PARENT",
  "IMMEDIATE_FAMILY",
  "CLOSE_FRIEND",
  "OFFICEMATE",
  "RELATIVE",
  "FAMILY_FRIEND",
  "CHURCHMATE",
  "COMPANION_OF_GUEST",
];

function escapeCell(value: string): string {
  return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/** Generic CSV from a header row + value rows (for report exports). */
export function rowsToCsv(
  header: string[],
  rows: Array<Array<string | number>>,
): string {
  const lines = [header.map((h) => escapeCell(h)).join(",")];
  for (const row of rows) {
    lines.push(row.map((cell) => escapeCell(String(cell))).join(","));
  }
  return lines.join("\n");
}

/** Triggers a client-side CSV download. Call from a browser event handler. */
export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** Serializes guests to a CSV string (with header row). */
export function guestsToCsv(guests: GuestResponse[]): string {
  const lines = [HEADER.join(",")];
  for (const g of guests) {
    lines.push(
      [
        g.firstName,
        g.lastName ?? "",
        g.title ?? "",
        g.gender ?? "",
        g.email ?? "",
        g.phone ?? "",
        g.rsvpStatus,
        g.partySize != null ? String(g.partySize) : "",
        g.dietaryNotes ?? "",
        g.tableNumber != null ? String(g.tableNumber) : "",
        g.priority ?? "",
        g.relatedTo ?? "",
        g.relationship ?? "",
        g.roles.map((r) => r.name).join(", "),
      ]
        .map(escapeCell)
        .join(","),
    );
  }
  return lines.join("\n");
}

/** Minimal RFC-4180 CSV parser (quotes, escaped quotes, newlines in quoted cells). */
function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cell += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(cell);
      cell = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && text[i + 1] === "\n") i++;
      row.push(cell);
      cell = "";
      if (row.some((c) => c.trim() !== "")) rows.push(row);
      row = [];
    } else {
      cell += ch;
    }
  }
  row.push(cell);
  if (row.some((c) => c.trim() !== "")) rows.push(row);
  return rows;
}

export interface ParsedGuestRow {
  firstName: string;
  lastName: string | null;
  title: string | null;
  gender: Gender | null;
  email: string | null;
  phone: string | null;
  rsvpStatus: RsvpStatus;
  partySize: number | null;
  dietaryNotes: string | null;
  tableNumber: number | null;
  priority: GuestPriority | null;
  relatedTo: RelatedTo | null;
  relationship: GuestRelationship | null;
  roleIds: string[];
}

/**
 * Parses a guest CSV. Accepts our export format; a header row is detected and skipped. Rows
 * missing a first name are dropped; unknown RSVP values fall back to PENDING; unknown/blank
 * title, gender, priority, related-to, relationship, or role values are simply left unset (all
 * optional). A blank party-size cell is left unset (null = "just this guest"). The "role" column
 * holds a comma-separated list of role display names; each is resolved case-insensitively
 * against `roles` and unresolved names are silently dropped (same leniency as a single role).
 */
export function csvToGuests(text: string, roles: GuestRoleResponse[] = []): ParsedGuestRow[] {
  const rows = parseCsv(text);
  if (rows.length === 0) return [];

  const start = rows[0][0]?.trim().toLowerCase() === "firstname" ? 1 : 0;
  const parsed: ParsedGuestRow[] = [];

  for (const row of rows.slice(start)) {
    const [
      firstName,
      lastName,
      title,
      genderRaw,
      email,
      phone,
      rsvp,
      party,
      dietary,
      table,
      priorityRaw,
      relatedToRaw,
      relationshipRaw,
      roleName,
    ] = row.map((c) => c.trim());
    if (!firstName) continue;
    const rsvpStatus = RSVP_VALUES.includes(rsvp?.toUpperCase() as RsvpStatus)
      ? (rsvp.toUpperCase() as RsvpStatus)
      : "PENDING";
    const gender = GENDER_VALUES.includes(genderRaw?.toUpperCase() as Gender)
      ? (genderRaw.toUpperCase() as Gender)
      : null;
    const partySize = party ? Math.max(1, Number(party) || 1) : null;
    const tableNumber = table && !Number.isNaN(Number(table)) ? Number(table) : null;
    const priority = PRIORITY_VALUES.includes(priorityRaw?.toUpperCase() as GuestPriority)
      ? (priorityRaw.toUpperCase() as GuestPriority)
      : null;
    const relatedTo = RELATED_TO_VALUES.includes(relatedToRaw?.toUpperCase() as RelatedTo)
      ? (relatedToRaw.toUpperCase() as RelatedTo)
      : null;
    const relationship = RELATIONSHIP_VALUES.includes(
      relationshipRaw?.toUpperCase() as GuestRelationship,
    )
      ? (relationshipRaw.toUpperCase() as GuestRelationship)
      : null;
    const roleIds = (roleName ?? "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)
      .map((n) => roles.find((r) => r.name.toLowerCase() === n.toLowerCase())?.id)
      .filter((id): id is string => !!id);
    parsed.push({
      firstName,
      lastName: lastName || null,
      title: title || null,
      gender,
      email: email || null,
      phone: phone || null,
      rsvpStatus,
      partySize,
      dietaryNotes: dietary || null,
      tableNumber,
      priority,
      relatedTo,
      relationship,
      roleIds,
    });
  }
  return parsed;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/**
 * A friendly countdown to a wedding date, e.g. "142 days to go", "Today!", "3 days ago".
 * Returns null when there's no date. Compared at day granularity (UTC) to match how dates are
 * stored/displayed.
 */
export function countdownToWedding(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const target = new Date(iso);
  if (Number.isNaN(target.getTime())) return null;

  const now = new Date();
  const dayMs = 86_400_000;
  const targetDay = Date.UTC(
    target.getUTCFullYear(),
    target.getUTCMonth(),
    target.getUTCDate(),
  );
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const days = Math.round((targetDay - today) / dayMs);

  if (days > 1) return `${days} days to go`;
  if (days === 1) return "Tomorrow!";
  if (days === 0) return "Today! 🎉";
  if (days === -1) return "Yesterday";
  return `${Math.abs(days)} days ago`;
}

/** True when the given date is strictly before today (compared at UTC day granularity). */
export function isPastDue(iso: string | null | undefined): boolean {
  if (!iso) return false;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return false;
  const now = new Date();
  const dueDay = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return dueDay < today;
}

/** "HH:mm" or "HH:mm:ss" → 12-hour display, e.g. "06:00:00" → "6:00 AM". */
export function formatTime(time: string | null | undefined): string {
  if (!time) return "—";
  const [h, m] = time.split(":").map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return "—";
  const suffix = h < 12 ? "AM" : "PM";
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return `${hour12}:${String(m).padStart(2, "0")} ${suffix}`;
}

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
    maximumFractionDigits: 0,
  }).format(value);
}

/** A whole-number percentage of `part` out of `whole`, e.g. formatPercent(40, 100) -> "40%". */
export function formatPercent(part: number, whole: number): string {
  if (!whole) return "—";
  return `${Math.round((part / whole) * 100)}%`;
}

/** A human file size, e.g. formatBytes(1536) -> "1.5 KB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`;
}

/** Composed "Title First Last" display name, skipping any unset parts. */
export function guestFullName(guest: {
  firstName: string;
  lastName: string | null;
  title: string | null;
}): string {
  return [guest.title, guest.firstName, guest.lastName].filter(Boolean).join(" ");
}

const VENDOR_LABELS: Record<string, string> = {
  VENUE: "Venue",
  CATERING: "Catering",
  PHOTOGRAPHY: "Photography",
  VIDEOGRAPHY: "Videography",
  FLORIST: "Florist",
  FLOWERS: "Flowers",
  MUSIC: "Music",
  ATTIRE: "Attire",
  BEAUTY: "Beauty",
  STATIONERY: "Stationery",
  TRANSPORT: "Transport",
  GIFTS: "Gifts",
  OTHER: "Other",
};

/** Turns an ENUM_VALUE into a human label ("PHOTOGRAPHY" -> "Photography"). */
export function humanizeEnum(value: string): string {
  return (
    VENDOR_LABELS[value] ??
    value
      .toLowerCase()
      .replace(/_/g, " ")
      .replace(/^\w/, (c) => c.toUpperCase())
  );
}

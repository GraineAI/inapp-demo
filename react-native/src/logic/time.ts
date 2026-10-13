/** Dates as the app shows them, in the device's time zone. Fixed English names, so tests are exact. */

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const MINUTE = 60_000;
export const DAY = 24 * 60 * MINUTE;

export function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

export function addDays(d: Date, days: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + days, d.getHours(), d.getMinutes());
}

export function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "6:30 pm". */
export function formatTime(d: Date): string {
  const h = d.getHours();
  const m = d.getMinutes();
  return `${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, "0")} ${h < 12 ? "am" : "pm"}`;
}

/** "Today", "Tomorrow", "Yesterday", else "Wed 15 Oct". */
export function formatDay(d: Date, now: Date): string {
  if (sameDay(d, now)) return "Today";
  if (sameDay(d, addDays(startOfDay(now), 1))) return "Tomorrow";
  if (sameDay(d, addDays(startOfDay(now), -1))) return "Yesterday";
  return `${WEEKDAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

/** "Today, 6:30 pm" / "Wed 15 Oct, 9:00 am". */
export function formatWhen(d: Date | null | undefined, now: Date): string {
  if (!d || Number.isNaN(d.getTime())) return "Time not set";
  return `${formatDay(d, now)}, ${formatTime(d)}`;
}

/**
 * Booked calls, PRESENTATION ONLY: the status chip's words and colour, who booked it, the pack's quick
 * times as moments, and the range the pickers offer inside the SDK's `schedulingRules.earliest(now)` /
 * `latest(now)`, on a 5-minute grid, with the device's time zone.
 * The booking rules themselves — what is too soon, too far, too many, what can be cancelled — are
 * the SDK's and the server's; this file never decides them.
 */
import { addDays, formatDay, formatTime, MINUTE, startOfDay } from "./time";

export type ChipTone = "neutral" | "info" | "success" | "warning" | "danger" | "muted";

const CHIPS: Record<string, { label: string; tone: ChipTone }> = {
  scheduled: { label: "Scheduled", tone: "info" },
  ringing: { label: "Ringing", tone: "warning" },
  answered: { label: "Answered", tone: "success" },
  completed: { label: "Completed", tone: "success" },
  declined: { label: "Declined", tone: "danger" },
  missed: { label: "Missed", tone: "danger" },
  failed: { label: "Failed", tone: "danger" },
  cancelled: { label: "Cancelled", tone: "muted" },
  expired: { label: "Expired", tone: "muted" },
  nodevice: { label: "No device", tone: "danger" },
};

/** The raw text of a status as an SDK hands it: a string ("no_device" / "noDevice") or an unknown-case object. */
function rawStatus(status: unknown): string {
  if (typeof status === "string") return status;
  const s = status as { raw?: unknown; unknown?: unknown } | null;
  if (s && typeof s.raw === "string") return s.raw;
  if (s && typeof s.unknown === "string") return s.unknown;
  return "";
}

/** A status chip; a status this app does not know yet is shown as it came. */
export function statusChip(status: unknown): { label: string; tone: ChipTone } {
  const raw = rawStatus(status).trim();
  const known = CHIPS[raw.replace(/[_\s-]/g, "").toLowerCase()];
  if (known) return known;
  const label = raw ? raw.replace(/_/g, " ").replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase().replace(/^./, (c) => c.toUpperCase()) : "Unknown";
  return { label, tone: "neutral" };
}

/** Who booked it, when that is worth saying; null for the person's own booking. */
export function sourceLabel(source: unknown): string | null {
  const s = rawStatus(source).toLowerCase();
  if (s === "agent") return "Booked in a conversation";
  if (s === "dashboard" || s === "api") return "Booked by the team";
  return null;
}

/** A pack's one-tap time: `inMinutes` from now, or a `day` and a local `time` ("HH:MM"). */
export interface QuickTime {
  label: string;
  inMinutes?: number;
  day?: "today" | "tomorrow";
  time?: string;
}

export interface PickerRange { min: Date; max: Date }

/** Up to the next multiple of `step` minutes, seconds dropped. */
export function roundUpToStep(d: Date, step: number): Date {
  const ms = step * MINUTE;
  return new Date(Math.ceil(d.getTime() / ms) * ms);
}

/** A pack quick time as a moment on the device's clock. */
export function quickTimeAt(q: QuickTime, now: Date): Date {
  if (typeof q.inMinutes === "number") return roundUpToStep(new Date(now.getTime() + q.inMinutes * MINUTE), 1);
  const [h, m] = (q.time ?? "00:00").split(":").map((n) => Number(n));
  const day = q.day === "tomorrow" ? addDays(startOfDay(now), 1) : startOfDay(now);
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), h, m);
}

/** What the pickers offer: the SDK's earliest (up to the next 5 minutes) to its latest; null when that is empty. */
export function pickerRange(earliest: Date, latest: Date, step = 5): PickerRange | null {
  const min = roundUpToStep(earliest, step);
  return min.getTime() <= latest.getTime() ? { min, max: latest } : null;
}

export function inRange(d: Date, range: PickerRange | null): boolean {
  return !!range && d.getTime() >= range.min.getTime() && d.getTime() <= range.max.getTime();
}

/**
 * A picked moment on the 5-minute grid and inside the range — Android's clock cannot step by five, and a
 * date picked first keeps the time it had, which may fall outside the range on the first or last day.
 */
export function snapToRange(d: Date, range: PickerRange, step = 5): Date {
  const t = roundUpToStep(d, step);
  if (t.getTime() < range.min.getTime()) return range.min;
  if (t.getTime() > range.max.getTime()) return new Date(Math.floor(range.max.getTime() / (step * MINUTE)) * step * MINUTE);
  return t;
}

/** The device's time zone as the SDK sends it (IANA), or "Local time" where the platform will not say. */
export function deviceTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "Local time";
  } catch {
    return "Local time";
  }
}

/** "Asia/Kolkata · Tomorrow, 6:00 pm" — the zone the call is booked in, then the time on this phone's clock. */
export function zoneLabel(at: Date, zone: string, now: Date): string {
  return `${zone} · ${formatDay(at, now)}, ${formatTime(at)}`;
}

/**
 * One line of the call activity log: when, which agent the call was with (as the call engine said), what
 * happened and the call's id — "6:05 pm · Asha · ended (remote) · call_1f2e3d4c".
 */
export function callEventLine(
  e: { callId: string; kind: string; detail: string | null; agent: "main" | "help" | null; at: Date },
  names: { main: string; help: string },
): string {
  const what = `${e.kind}${e.detail ? ` (${e.detail})` : ""}`;
  return [formatTime(e.at), e.agent ? names[e.agent] : null, what, e.callId.slice(0, 12)].filter(Boolean).join(" · ");
}

/** A booked call and which of the app's two agents it belongs to. */
export interface AgentCall<C> { call: C; agent: "main" | "help" }

/**
 * Both agents' calls in one list: upcoming first, soonest first; then the rest, most recent first. Which
 * calls are upcoming is the SDK's `isUpcoming`; this only orders and labels them.
 */
export function mergeAgentCalls<C extends { at: Date | null; isUpcoming: boolean }>(main: ReadonlyArray<C>, help: ReadonlyArray<C>): AgentCall<C>[] {
  const all: AgentCall<C>[] = [...main.map((call) => ({ call, agent: "main" as const })), ...help.map((call) => ({ call, agent: "help" as const }))];
  const t = (x: AgentCall<C>) => (x.call.at ? x.call.at.getTime() : 0);
  const upcoming = all.filter((x) => x.call.isUpcoming).sort((a, b) => t(a) - t(b));
  const rest = all.filter((x) => !x.call.isUpcoming).sort((a, b) => t(b) - t(a));
  return [...upcoming, ...rest];
}

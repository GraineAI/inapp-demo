/**
 * Booked calls, PRESENTATION ONLY: the status chip's words and colour, who booked it, the call log's lines
 * and both agents' calls in one list. Booking itself — the sheet, what is too soon, too far, too many —
 * is the SDK's, and what can be cancelled the SDK says; this file never decides them.
 */
import { formatTime } from "./time";

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

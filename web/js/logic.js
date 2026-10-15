/**
 * The demo's rules with no page attached: which screen a word names, which row an
 * agent's words refer to, what an action does, how a call's status and time read,
 * and what an error means in words. Pure — no DOM, no storage, no network — so
 * `node --test web/test` runs every rule here.
 *
 * Nothing in this file knows a domain. Every domain word comes from the use-case
 * pack (`usecases/*.json`, rules in `usecases/README.md`).
 */

/** The screens the agent may open, in the order it is told them. Sign-in is never one. */
export const SCREENS = ["home", "chat", "items", "calls", "memory", "profile"];

/**
 * One of the pack's two agents (usecases/README.md → Two agents). `main` falls back to the
 * top-level agentName / agentRole / starters (an older pack has no `agents`); `help` is null when
 * the pack has none.
 */
export function agentInfo(pack, which) {
  const agents = pack.agents && typeof pack.agents === "object" ? pack.agents : {};
  if (which === "help") {
    const help = agents.help;
    return help && typeof help === "object" && typeof help.name === "string" && help.name ? help : null;
  }
  const main = agents.main && typeof agents.main === "object" ? agents.main : {};
  return {
    name: main.name || pack.agentName,
    role: main.role || pack.agentRole,
    starters: Array.isArray(main.starters) && main.starters.length ? main.starters : pack.starters,
  };
}

/** A screen's title, from the pack. */
export function screenTitle(screen, pack) {
  switch (screen) {
    case "sign_in": return "Sign in";
    case "home": return pack.home.title;
    case "chat": return pack.agentName;
    case "items": return pack.items.title;
    case "calls": return pack.call.title;
    case "memory": return "Memory";
    case "profile": return "Profile";
    case "help": { const help = agentInfo(pack, "help"); return help ? help.name : "Help"; }
    default: return "";
  }
}

/** What the page reports as `screen`: the pack's own word for the list ("medicines"), else the generic key. */
export function contextName(screen, pack) {
  return screen === "items" ? pack.items.screenName : screen;
}

/** The screen names `open_screen` accepts, as the agent is told them. */
export function screenNames(pack) {
  return SCREENS.map((s) => contextName(s, pack));
}

/**
 * `open_screen`'s argument: a generic key or the pack's `items.screenName`,
 * case-insensitively. A component's tap sends a `path` ("/medicines"), so
 * leading slashes are ignored.
 */
export function parseScreen(raw, pack) {
  if (typeof raw !== "string") return null;
  const key = raw.trim().toLowerCase().replace(/^\/+/, "");
  if (!key) return null;
  if (key === pack.items.screenName.toLowerCase()) return "items";
  return SCREENS.includes(key) ? key : null;
}

// ── People and rows ─────────────────────────────────────────────────────────

/** "Riya Sharma" → "Riya". */
export function firstName(name) {
  const s = String(name || "").trim();
  return s.split(/\s+/)[0] || s;
}

/** "home_branch" → "Home branch"; "patient_id" → "Patient ID". For trait labels. */
export function humanize(key) {
  const words = String(key || "")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((w) => (w.toLowerCase() === "id" ? "ID" : w.toLowerCase()));
  if (!words.length) return "";
  return words[0].charAt(0).toUpperCase() + words[0].slice(1) + (words.length > 1 ? " " + words.slice(1).join(" ") : "");
}

/** What `identify()` is given: the identifier under the pack's field, the name, then the traits. */
export function traitsFor(pack, user) {
  return { [pack.identifierField]: user.id, name: user.name, ...(user.traits || {}) };
}

/** The signed-in user's rows: every row with no `userIds`, and the rows that name them. */
export function rowsFor(pack, userId) {
  return pack.items.rows.filter((r) => !Array.isArray(r.userIds) || r.userIds.includes(userId));
}

const norm = (s) => String(s == null ? "" : s).trim().toLowerCase();

/**
 * The row a name refers to (the pack's rule, case-insensitive, trimmed): a row
 * whose id equals it; else whose title equals it; else the ONE row whose title
 * or any field value contains it — so "7730" and "GD-55120" work. Two or more
 * is ambiguous; the agent is asked which.
 */
export function findRow(rows, query) {
  const q = norm(query);
  if (!q) return { kind: "none" };
  const byId = rows.find((r) => norm(r.id) === q);
  if (byId) return { kind: "found", row: byId };
  const byTitle = rows.find((r) => norm(r.title) === q);
  if (byTitle) return { kind: "found", row: byTitle };
  const containing = rows.filter((r) =>
    norm(r.title).includes(q) || (r.fields || []).some((f) => norm(f.value).includes(q)));
  if (containing.length === 1) return { kind: "found", row: containing[0] };
  if (containing.length > 1) return { kind: "ambiguous", rows: containing };
  return { kind: "none" };
}

/** A row's status chip now: the one set this session, else the pack's starting one. */
export function rowStatus(row, statuses) {
  const set = statuses && Object.prototype.hasOwnProperty.call(statuses, row.id) ? statuses[row.id] : undefined;
  return set !== undefined ? set : (row.status || null);
}

/** A row's chip tone: a status set this session reads as a change; the pack's starting one is plain. */
export function statusToneForRow(row, statuses) {
  return statuses && Object.prototype.hasOwnProperty.call(statuses, row.id) ? "info" : "neutral";
}

/** What the agent reads as a row's value: its status, else its subtitle. */
export function rowValue(row, statuses) {
  return rowStatus(row, statuses) || row.subtitle || "";
}

/** The pack's action by name. */
export function actionNamed(pack, name) {
  return pack.actions.find((a) => a.name === name) || null;
}

/** The status buttons a row shows: its own set_status actions, hidden while it already has that status. */
export function statusButtons(pack, row, statuses) {
  const current = rowStatus(row, statuses);
  return (row.actions || [])
    .map((name) => actionNamed(pack, name))
    .filter((a) => a && a.effect === "set_status" && a.status && a.status !== current);
}

// ── What an action does ─────────────────────────────────────────────────────

const ok = (message) => ({ status: "ok", message });
const refused = (message) => ({ status: "refused", message });

/**
 * Decide what one of the pack's actions does, for whoever is signed in. Returns
 * the answer the agent hears and the changes the page makes; the page applies
 * them. The answers are the pack's (`usecases/README.md` → Actions), word for word,
 * so every platform's agent hears the same thing.
 *
 *   effects: { screen?: "items", openRow?: "<row id>", setStatus?: { rowId, status } }
 */
export function decideAction(pack, action, args, { userId, statuses } = {}) {
  if (!userId) {
    return { result: refused("Nobody is signed in yet. Ask the user to choose who they are on the sign-in screen."), effects: {} };
  }
  const a = args && typeof args === "object" ? args : {};

  if (action.effect === "open_screen") {
    const raw = typeof a.screen === "string" ? a.screen : typeof a.path === "string" ? a.path : "";
    const screen = parseScreen(raw, pack);
    if (!screen) {
      return { result: refused(`There is no screen called "${raw.trim()}". Screens: ${screenNames(pack).join(", ")}.`), effects: {} };
    }
    return { result: ok(`Opened ${screenTitle(screen, pack)}.`), effects: { screen } };
  }

  if (action.effect === "open_item" || action.effect === "set_status") {
    const rows = rowsFor(pack, userId);
    const query = a.item == null ? "" : String(a.item).trim();
    const match = findRow(rows, query);
    if (match.kind === "none") {
      const titles = rows.map((r) => r.title).join(", ") || "none";
      return { result: refused(`No ${pack.items.itemNoun} called "${query}". ${pack.items.title}: ${titles}.`), effects: {} };
    }
    if (match.kind === "ambiguous") {
      return { result: refused(`Which one? ${match.rows.map((r) => r.title).join(", ")}.`), effects: {} };
    }
    const row = match.row;
    if (action.effect === "open_item") {
      return { result: ok(`Showing ${row.title}.`), effects: { screen: "items", openRow: row.id } };
    }
    if (!(row.actions || []).includes(action.name)) {
      return { result: refused(`${action.label} is not available for ${row.title}.`), effects: {} };
    }
    if (rowStatus(row, statuses) === action.status) {
      return { result: ok(`${row.title} is already ${action.status}.`), effects: {} };
    }
    return { result: ok(`${row.title}: ${action.status}.`), effects: { setStatus: { rowId: row.id, status: action.status } } };
  }

  return { result: refused(`This page does not do "${action.label}".`), effects: {} };
}

// ── Scheduled calls (presentation only: the SDK decides) ────────────────────
//
// A booked call reaches the page as the SDK types describe it (graine.js → normaliseCall):
// `{ id, at, status, source, reason, isUpcoming, canCancel }`, with the SDK types' names
// (`noDevice`, source `app`). Whether a call is upcoming, whether it can be cancelled and every
// booking rule are the SDK's — booking is the widget's own sheet; this section only says how they read.

/** Under a booking button while the loader says this visitor cannot book with its agent. */
export const BOOKING_UNAVAILABLE = "Booking isn't available for this agent.";

/** Every status the platform reports, in the order the page explains them. */
export const CALL_STATUSES = [
  "scheduled", "ringing", "answered", "completed", "declined", "missed", "failed", "cancelled", "expired", "noDevice",
];

const STATUS_LABELS = {
  scheduled: "Scheduled", ringing: "Ringing", answered: "Answered", completed: "Completed", declined: "Declined",
  missed: "Missed", failed: "Failed", cancelled: "Cancelled", expired: "Expired", noDevice: "No device",
};

/** One line per status, for the legend on the calls screen. */
export const STATUS_HELP = {
  scheduled: "Booked. It rings at the time shown.",
  ringing: "Ringing the phone now.",
  answered: "Picked up. The conversation is in progress.",
  completed: "The call took place.",
  declined: "Declined on the phone. Nothing rings again for at least 6 hours.",
  missed: "Rang without an answer.",
  failed: "Could not be connected.",
  cancelled: "Cancelled before it rang.",
  expired: "Could not ring in time, for example outside the hours calls are allowed.",
  noDevice: "No phone signed in as this person was seen in the last 30 days.",
};

export function statusLabel(status) {
  const s = String(status || "");
  if (STATUS_LABELS[s]) return STATUS_LABELS[s];
  // A status this page does not know yet passes through, readable ("on_hold" / "onHold" → "On hold").
  const words = s.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/_/g, " ").trim().toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : "Unknown";
}

/** How a status reads at a glance; the stylesheet picks the colour. */
export function statusTone(status) {
  switch (status) {
    case "scheduled": case "ringing": case "answered": return "info";
    case "completed": return "positive";
    case "declined": case "missed": case "expired": case "noDevice": return "warning";
    case "failed": return "negative";
    default: return "neutral";
  }
}

/** Who booked it, in words. */
export function bookedBy(source, agentName) {
  switch (source) {
    case "agent": return `Booked by ${agentName}`;
    case "app": return "Booked by you";
    case "dashboard": case "api": return "Booked by the team";
    default: return null;
  }
}

const timeOf = (call, missing) => (call.at instanceof Date ? call.at.getTime() : missing);
const ascending = (x, y) => (x === y ? 0 : x < y ? -1 : 1);

/** For the screen: the SDK's upcoming calls soonest first, then the rest most recent first. */
export function orderCalls(calls) {
  const upcoming = calls.filter((c) => c.isUpcoming).sort((a, b) => ascending(timeOf(a, Infinity), timeOf(b, Infinity)));
  const past = calls.filter((c) => !c.isUpcoming).sort((a, b) => ascending(timeOf(b, -Infinity), timeOf(a, -Infinity)));
  return { upcoming, past };
}

/** The next call that will ring, for Home. */
export function nextCall(calls) {
  return orderCalls(calls).upcoming.find((c) => c.at instanceof Date) || null;
}

// ── Times ───────────────────────────────────────────────────────────────────

/** ISO-8601 in UTC without milliseconds — how the screen context carries a time. */
export function toUtcIso(date) {
  return date.toISOString().replace(/\.\d{3}Z$/, "Z");
}

function sameDay(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "Today", "Tomorrow", "Yesterday", or null. */
export function dayWord(date, now) {
  if (sameDay(date, now)) return "Today";
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  if (sameDay(date, tomorrow)) return "Tomorrow";
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  if (sameDay(date, yesterday)) return "Yesterday";
  return null;
}

/** "Today, 18:00" · "Tomorrow, 09:30" · "Thu 16 Oct, 18:00" — in the page's time zone. */
export function formatWhen(date, now = new Date(), locale) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "Time not available";
  const time = new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit" }).format(date);
  const word = dayWord(date, now);
  if (word) return `${word}, ${time}`;
  const day = new Intl.DateTimeFormat(locale, {
    weekday: "short", day: "numeric", month: "short",
    ...(date.getFullYear() !== now.getFullYear() ? { year: "numeric" } : {}),
  }).format(date);
  return `${day}, ${time}`;
}

// ── Errors in words ─────────────────────────────────────────────────────────

/**
 * Plain words for a refusal (the platform's error codes) when listing or
 * cancelling calls, or reading memory. A booking's refusals are worded by the
 * widget's booking sheet; any code without words here reads as the SDK's own
 * sentence.
 */
export function messageFor(code, serverMessage) {
  const server = typeof serverMessage === "string" ? serverMessage.trim() : "";
  switch (code) {
    case "identity_required": return "Sign in to use this.";
    case "device_limit": return "You are signed in on the most devices allowed for one person. Sign out on another device first, or ask the team to reset your devices.";
    case "rate_limited": return "Too many requests. Try again in a few minutes.";
    case "not_found": return "That call no longer exists. Refresh the list.";
    case "network": return "No connection. Check your network and try again.";
    case "invalid_key": case "origin_refused":
      return "This website is not set up for the assistant yet. Check the publishable key and the agent's Allowed domains.";
    case "unavailable": return "The service is unavailable right now. Try again shortly.";
    default: return server || "Something went wrong. Try again.";
  }
}

// ── Screen context ──────────────────────────────────────────────────────────

/**
 * What the visitor is looking at, as the agent reads it (`usecases/README.md`
 * → Screen context: the same keys on every platform). Memory text never goes
 * in it, only the count.
 */
export function screenContext({ pack, screen, userId, memoryOn, statuses, calls, memoriesCount }) {
  if (!pack) return { screen: "sign_in", title: "Sign in", fields: [] };
  const filled = (name, label, value) => ({ name, label, value, status: "filled" });
  let fields = [];
  if (screen === "home") {
    const next = nextCall(calls || []);
    fields = [
      filled("memory", "Memory", memoryOn ? "on" : "off"),
      filled("next_call", "Next call", next ? toUtcIso(next.at) : "none"),
    ];
  } else if (screen === "items") {
    fields = rowsFor(pack, userId).map((r) => filled(r.id, r.title, rowValue(r, statuses)));
  } else if (screen === "calls") {
    fields = [filled("upcoming", "Upcoming calls", String(orderCalls(calls || []).upcoming.length))];
  } else if (screen === "memory") {
    fields = [
      filled("memory", "Memory", memoryOn ? "on" : "off"),
      filled("remembered", "Remembered", String(memoriesCount || 0)),
    ];
  }
  return { screen: contextName(screen, pack), title: screenTitle(screen, pack), fields };
}

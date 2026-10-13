/**
 * The sentence a person sees for each error code the in-app routes answer with (and the few that
 * happen before a route answers). Plain, and about what to do next.
 */

export interface ErrorContext {
  agentName?: string;
  minLeadMinutes?: number;
  maxDaysAhead?: number;
  maxPendingPerUser?: number;
  retryAfterSeconds?: number | null;
}

export function messageFor(code: string | null | undefined, fallback: string | null | undefined, ctx: ErrorContext = {}): string {
  const agent = ctx.agentName || "The assistant";
  switch (code) {
    case "scheduling_off": return `${agent} does not take bookings from the app.`;
    case "too_soon": return `Pick a time at least ${ctx.minLeadMinutes ?? 5} minutes from now.`;
    case "too_far": return `Pick a time within the next ${ctx.maxDaysAhead ?? 14} days.`;
    case "outside_window": return `That time is outside ${agent}'s calling hours. Pick another time.`;
    case "too_many_pending": return `You already have ${ctx.maxPendingPerUser ?? 3} calls booked. Cancel one to book another.`;
    case "rate_limited":
      return ctx.retryAfterSeconds ? `Too many requests. Try again in ${ctx.retryAfterSeconds} seconds.` : "Too many requests. Try again in a minute.";
    case "identity_required": return "Sign in first.";
    case "device_limit": return `This device is not one of yours for ${agent}. An admin can reset your devices in the dashboard.`;
    case "not_found": return "That no longer exists.";
    case "invalid_key":
    case "origin_refused": return "The app's publishable key was refused. Check the key, and that the agent's allowed domains include app://.";
    case "unavailable": return "The service is unavailable right now. Try again shortly.";
    case "network": return "Could not connect. Check the connection and try again.";
    case "no_install_id": return "This device could not keep an install id, so it cannot be recognised.";
    case "invalid_request": return fallback || "That request was not valid.";
    default: return fallback || "Something went wrong. Try again.";
  }
}

/** The code and message of anything thrown by the SDK, without trusting its shape. */
export function readError(err: unknown): { code: string; message: string; retryAfterSeconds: number | null } {
  const e = err as { code?: unknown; message?: unknown; retryAfterSeconds?: unknown } | null;
  return {
    code: typeof e?.code === "string" ? e.code : "unknown",
    message: typeof e?.message === "string" ? e.message : String(err ?? ""),
    retryAfterSeconds: typeof e?.retryAfterSeconds === "number" ? e.retryAfterSeconds : null,
  };
}

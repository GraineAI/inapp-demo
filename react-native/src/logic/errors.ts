/**
 * The sentence a person sees for each error code the in-app routes answer with (and the few that
 * happen before a route answers). Plain, and about what to do next. A booking's refusals are worded
 * by the SDK's booking sheet; any code without words here reads as the SDK's own sentence.
 */

export interface ErrorContext {
  agentName?: string;
  retryAfterSeconds?: number | null;
}

export function messageFor(code: string | null | undefined, fallback: string | null | undefined, ctx: ErrorContext = {}): string {
  const agent = ctx.agentName || "The assistant";
  switch (code) {
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

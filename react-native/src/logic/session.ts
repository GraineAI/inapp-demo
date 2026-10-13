/**
 * What the app shows about the agent from its session (read once per launch): whether it remembers
 * people at all, and the name on its calls. Booking rules are not read here — they are the SDK's
 * `schedulingRules`. An older server sends none of this, which means off.
 */
export interface AgentSettings {
  memoryEnabled: boolean | null;
  callName: string | null;
}

export const UNKNOWN_SETTINGS: AgentSettings = { memoryEnabled: null, callName: null };

export function settingsFromSession(raw: unknown): AgentSettings {
  const s = (raw ?? {}) as Record<string, any>;
  return {
    memoryEnabled: typeof s.memory?.enabled === "boolean" ? s.memory.enabled : false,
    callName: typeof s.calls?.callName === "string" && s.calls.callName ? s.calls.callName : null,
  };
}

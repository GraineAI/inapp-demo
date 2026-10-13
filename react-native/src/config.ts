/**
 * Publishable keys and the base URL, from react-native/.env (copy .env.example). Expo inlines
 * EXPO_PUBLIC_* at build time, and only where the name is written out in full — hence the table.
 *
 * Each use-case pack is its own agent in the dashboard, so each may have its own key; a pack without
 * one uses EXPO_PUBLIC_GRAINE_PUBLISHABLE_KEY.
 */
const PACK_KEYS: Record<string, string | undefined> = {
  "health-coach": process.env.EXPO_PUBLIC_GRAINE_KEY_HEALTH_COACH,
  "banking-support": process.env.EXPO_PUBLIC_GRAINE_KEY_BANKING_SUPPORT,
  "retail-shopping": process.env.EXPO_PUBLIC_GRAINE_KEY_RETAIL_SHOPPING,
};

/** The help agent of each pack (the Help screen) — optional; a pack without one uses EXPO_PUBLIC_GRAINE_HELP_KEY. */
const HELP_KEYS: Record<string, string | undefined> = {
  "health-coach": process.env.EXPO_PUBLIC_GRAINE_HELP_KEY_HEALTH_COACH,
  "banking-support": process.env.EXPO_PUBLIC_GRAINE_HELP_KEY_BANKING_SUPPORT,
  "retail-shopping": process.env.EXPO_PUBLIC_GRAINE_HELP_KEY_RETAIL_SHOPPING,
};

export const demoConfig = {
  defaultKey: (process.env.EXPO_PUBLIC_GRAINE_PUBLISHABLE_KEY ?? "").trim(),
  defaultHelpKey: (process.env.EXPO_PUBLIC_GRAINE_HELP_KEY ?? "").trim(),
  baseUrl: (process.env.EXPO_PUBLIC_GRAINE_BASE_URL ?? "").trim().replace(/\/+$/, "") || "https://www.graine.ai",
  appVersion: "1.0.0",
};

const KEY_SHAPE = /^pk_(live|test)_[A-Za-z0-9_]+$/;

/** The key for a pack's agent: its own, else the default. "" when neither is set. */
export function publishableKeyFor(packId: string): string {
  const own = (PACK_KEYS[packId] ?? "").trim();
  return KEY_SHAPE.test(own) ? own : demoConfig.defaultKey;
}

export function isConfigured(packId: string): boolean {
  return KEY_SHAPE.test(publishableKeyFor(packId));
}

/** The key for a pack's help agent: its own, else the default help key; "" when there is none. */
export function helpKeyFor(packId: string): string {
  const own = (HELP_KEYS[packId] ?? "").trim();
  if (KEY_SHAPE.test(own)) return own;
  return KEY_SHAPE.test(demoConfig.defaultHelpKey) ? demoConfig.defaultHelpKey : "";
}

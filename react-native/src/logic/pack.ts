/**
 * A use-case pack (../../../usecases/*.json, schema in usecases/usecase.schema.json) and the few
 * questions the app asks of it. Nothing in the app is specific to a domain: every word comes from here.
 */

export type ScreenKey = "home" | "chat" | "items" | "calls" | "memory" | "profile";

export interface PackUser {
  id: string;
  name: string;
  traits: Record<string, string>;
}

export interface PackRow {
  id: string;
  title: string;
  subtitle: string;
  status?: string;
  fields: Array<{ name: string; label: string; value: string }>;
  actions: string[];
  userIds?: string[];
}

export interface PackActionArg {
  name: string;
  type: "string" | "number" | "boolean";
  required: boolean;
  description: string;
  enum?: string[];
}

export interface PackAction {
  name: string;
  effect: "open_screen" | "open_item" | "set_status";
  label: string;
  description: string;
  status?: string;
  args: PackActionArg[];
}

export interface PackAgent {
  name: string;
  role: string;
  starters: string[];
}

export interface PackHelpAgent extends PackAgent {
  callName: string;
  placeReason: string;
  bookLabel: string;
  reasons: string[];
}

export interface UseCasePack {
  schemaVersion: 1;
  id: string;
  appTitle: string;
  agentName: string;
  agentRole: string;
  /** Main on Home and Chat, help on the Help screen — each its own agent and key. */
  agents: { main: PackAgent; help: PackHelpAgent };
  tagline: string;
  notice?: string;
  accent?: string;
  identifierField: string;
  identifierLabel: string;
  userNoun: string;
  users: PackUser[];
  home: { title: string; cards: Array<{ id: string; title: string; body: string; target: Exclude<ScreenKey, "home"> }> };
  items: { title: string; screenName: string; itemNoun: string; emptyText: string; rows: PackRow[] };
  actions: PackAction[];
  call: {
    callName: string;
    title: string;
    /** The button that opens the SDK's booking sheet. */
    bookLabel: string;
    /** What a booking is about: the first starts the sheet's "What's it about?" line, which the person may change. */
    reasons: string[];
    placeReason: string;
  };
  memoryExamples: Array<{ tell: string; ask: string; expect: string }>;
  starters: string[];
  /**
   * The agent's prompt variables and the user field each takes — `{ callee_name: "name", age: "age" }`: a key is
   * a variable the agent's prompt or welcome uses, a value is "name" or one of the person's traits (never the
   * identifier). Set through the SDK at sign-in, as an API call sends `variables`. Absent: DEFAULT_VARIABLES.
   */
  variables?: Record<string, string>;
}

export const SCREEN_KEYS: ReadonlyArray<ScreenKey> = ["home", "chat", "items", "calls", "memory", "profile"];

/** What identify() is given: the identifier under the pack's field, the name, and the person's traits. */
export function identifyTraits(pack: UseCasePack, user: PackUser): Record<string, string> {
  return { ...user.traits, name: user.name, [pack.identifierField]: user.id };
}

/** A pack that names no variables: the person's name as `callee_name`, what greetings and Call History read. */
export const DEFAULT_VARIABLES: Readonly<Record<string, string>> = { callee_name: "name" };

/**
 * The agent's prompt variables for `user`, from the pack's `variables` map — set through the SDK exactly as an
 * API call sends them. A field the person does not have is left out.
 */
export function variablesFor(pack: UseCasePack, user: PackUser): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [variable, field] of Object.entries(pack.variables ?? DEFAULT_VARIABLES)) {
    const value = field === "name" ? user.name : user.traits[field];
    if (typeof value === "string" && value.trim()) out[variable] = value;
  }
  return out;
}

/** Every variable the pack maps: what sign-out clears, so nothing of one person reaches the next. */
export function variableNames(pack: UseCasePack): string[] {
  return Object.keys(pack.variables ?? DEFAULT_VARIABLES);
}

/** The rows this person sees. */
export function rowsFor(pack: UseCasePack, userId: string | null): PackRow[] {
  if (!userId) return [];
  return pack.items.rows.filter((r) => !r.userIds || r.userIds.includes(userId));
}

/** The name a screen is reported under: the generic key, except the list screen, which the pack names. */
export function screenName(pack: UseCasePack, key: ScreenKey): string {
  return key === "items" ? pack.items.screenName : key;
}

/** A screen's title as the user sees it. */
export function screenTitle(pack: UseCasePack, key: ScreenKey): string {
  switch (key) {
    case "home": return pack.home.title;
    case "chat": return pack.agentName;
    case "items": return pack.items.title;
    case "calls": return pack.call.title;
    case "memory": return "Memory";
    case "profile": return "Profile";
  }
}

/** The generic screen for a name the agent (or a component) used, or null. Case-insensitive. */
export function screenKeyFor(pack: UseCasePack, name: unknown): ScreenKey | null {
  if (typeof name !== "string") return null;
  const n = name.trim().replace(/^\/+/, "").toLowerCase();
  if (!n) return null;
  if (n === pack.items.screenName.toLowerCase()) return "items";
  return (SCREEN_KEYS as ReadonlyArray<string>).includes(n) ? (n as ScreenKey) : null;
}

export function findPack(packs: ReadonlyArray<UseCasePack>, id: string | null | undefined): UseCasePack | null {
  return packs.find((p) => p.id === id) ?? null;
}

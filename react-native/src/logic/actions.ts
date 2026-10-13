/**
 * The pack's actions, run generically: every platform implements the same three effects with the
 * same answers (usecases/README.md, "Actions"). Pure, so the rules are tested once and the screen
 * code only applies the effect.
 */
import { screenKeyFor, screenName, screenTitle, SCREEN_KEYS, type PackAction, type PackRow, type ScreenKey, type UseCasePack } from "./pack";

export interface ActionAnswer {
  status: "ok" | "refused" | "error";
  message: string;
  data?: Record<string, unknown>;
}

export type ActionEffect =
  | { type: "navigate"; screen: ScreenKey }
  | { type: "open_item"; rowId: string }
  | { type: "set_status"; rowId: string; status: string }
  | null;

export interface ActionOutcome {
  answer: ActionAnswer;
  effect: ActionEffect;
}

export type RowMatch = { row: PackRow } | { ambiguous: PackRow[] } | { none: true };

const norm = (v: unknown): string => (typeof v === "string" || typeof v === "number" ? String(v) : "").trim().toLowerCase();

/** The row a person or the agent named: by id, else by title, else the ONE row whose title or a field contains it. */
export function findRow(rows: ReadonlyArray<PackRow>, query: unknown): RowMatch {
  const q = norm(query);
  if (!q) return { none: true };
  const byId = rows.find((r) => r.id.toLowerCase() === q);
  if (byId) return { row: byId };
  const byTitle = rows.find((r) => r.title.toLowerCase() === q);
  if (byTitle) return { row: byTitle };
  const containing = rows.filter(
    (r) => r.title.toLowerCase().includes(q) || r.fields.some((f) => f.value.toLowerCase().includes(q)),
  );
  if (containing.length === 1) return { row: containing[0] };
  if (containing.length > 1) return { ambiguous: containing };
  return { none: true };
}

/** A screen named by the agent (`screen`) or by a component's tap (`path`, e.g. "orders?id=1" or "/calls"). */
function screenArg(args: Record<string, unknown>): string {
  const raw = typeof args.screen === "string" && args.screen.trim() ? args.screen : typeof args.path === "string" ? args.path : "";
  return raw.split("?")[0].split("/").filter(Boolean)[0] ?? "";
}

export function currentStatus(row: PackRow, statuses: Readonly<Record<string, string>>): string | undefined {
  return statuses[row.id] ?? row.status;
}

/**
 * Run one of the pack's actions against the signed-in person's rows. `statuses` are the statuses
 * set so far this session (row id → status). Never throws: a wrong argument is a refusal the agent
 * can repeat to the person.
 */
export function runPackAction(
  pack: UseCasePack,
  action: PackAction,
  args: Record<string, unknown> | null | undefined,
  rows: ReadonlyArray<PackRow>,
  statuses: Readonly<Record<string, string>>,
): ActionOutcome {
  const a = args ?? {};
  if (action.effect === "open_screen") {
    const asked = screenArg(a);
    const key = screenKeyFor(pack, asked);
    if (!key) {
      const names = SCREEN_KEYS.map((k) => screenName(pack, k)).join(", ");
      return { answer: { status: "refused", message: `There is no screen called "${asked}". Screens: ${names}.` }, effect: null };
    }
    return { answer: { status: "ok", message: `Opened ${screenTitle(pack, key)}.`, data: { screen: screenName(pack, key) } }, effect: { type: "navigate", screen: key } };
  }

  const titles = rows.map((r) => r.title).join(", ");
  const match = findRow(rows, a.item);
  if ("ambiguous" in match) {
    return { answer: { status: "refused", message: `Which one? ${match.ambiguous.map((r) => r.title).join(", ")}.` }, effect: null };
  }
  if ("none" in match) {
    const asked = norm(a.item) ? String(a.item).trim() : "";
    const message = asked
      ? `No ${pack.items.itemNoun} called "${asked}". ${pack.items.title}: ${titles || "none"}.`
      : `Say which ${pack.items.itemNoun}. ${pack.items.title}: ${titles || "none"}.`;
    return { answer: { status: "refused", message }, effect: null };
  }
  const row = match.row;
  const status = currentStatus(row, statuses);

  if (action.effect === "open_item") {
    return {
      answer: { status: "ok", message: `Showing ${row.title}.`, data: { item: row.id, ...(status ? { status } : {}) } },
      effect: { type: "open_item", rowId: row.id },
    };
  }

  // set_status
  const target = action.status ?? "";
  if (!row.actions.includes(action.name) || !target) {
    return { answer: { status: "refused", message: `${action.label} is not available for ${row.title}.` }, effect: null };
  }
  if (status === target) {
    return { answer: { status: "ok", message: `${row.title} is already ${target}.`, data: { item: row.id, status: target } }, effect: null };
  }
  return {
    answer: { status: "ok", message: `${row.title}: ${target}.`, data: { item: row.id, status: target } },
    effect: { type: "set_status", rowId: row.id, status: target },
  };
}

/** The buttons a row shows: its set_status actions, minus the one whose status it already has. */
export function rowButtons(pack: UseCasePack, row: PackRow, statuses: Readonly<Record<string, string>>): PackAction[] {
  const status = currentStatus(row, statuses);
  return row.actions
    .map((name) => pack.actions.find((a) => a.name === name))
    .filter((a): a is PackAction => !!a && a.effect === "set_status" && a.status !== status);
}

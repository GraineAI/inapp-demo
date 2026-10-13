import { findRow, rowButtons, runPackAction } from "../logic/actions";
import { identifyTraits, rowsFor, screenKeyFor, type UseCasePack } from "../logic/pack";
import { PACKS } from "../usecases";

const pack = (id: string): UseCasePack => PACKS.find((p) => p.id === id)!;
const action = (p: UseCasePack, name: string) => p.actions.find((a) => a.name === name)!;

describe("the generic actions, on every pack", () => {
  for (const p of PACKS) {
    describe(p.id, () => {
      const user = p.users[0];
      const rows = rowsFor(p, user.id);
      const openScreen = p.actions.find((a) => a.effect === "open_screen")!;
      const openItem = p.actions.find((a) => a.effect === "open_item")!;

      it("opens every screen by its reported name and by its generic key", () => {
        for (const name of ["home", "chat", p.items.screenName, "items", "calls", "memory", "profile"]) {
          const { answer, effect } = runPackAction(p, openScreen, { screen: name.toUpperCase() }, rows, {});
          expect(answer.status).toBe("ok");
          expect(effect?.type).toBe("navigate");
        }
        expect(runPackAction(p, openScreen, { path: `/${p.items.screenName}?from=card` }, rows, {}).effect).toEqual({ type: "navigate", screen: "items" });
        const refused = runPackAction(p, openScreen, { screen: "settings" }, rows, {});
        expect(refused.answer.status).toBe("refused");
        expect(refused.answer.message).toContain(p.items.screenName);
      });

      it("opens one of the person's own rows, and nobody else's", () => {
        const { answer, effect } = runPackAction(p, openItem, { item: rows[0].title }, rows, {});
        expect(answer).toMatchObject({ status: "ok", message: `Showing ${rows[0].title}.` });
        expect(effect).toEqual({ type: "open_item", rowId: rows[0].id });
        const someoneElses = p.items.rows.find((r) => r.userIds && !r.userIds.includes(user.id))!;
        expect(runPackAction(p, openItem, { item: someoneElses.id }, rows, {}).answer.status).toBe("refused");
      });

      it("signs in with the pack's identifier trait", () => {
        expect(identifyTraits(p, user)).toMatchObject({ [p.identifierField]: user.id, name: user.name, ...user.traits });
      });
    });
  }
});

describe("finding the row the agent named", () => {
  const p = pack("banking-support");
  const rows = rowsFor(p, "CUST-10041");

  it("matches id, then title, then the one row containing the words", () => {
    expect(findRow(rows, "credit-7730")).toEqual({ row: rows[1] });
    expect(findRow(rows, "travel credit card ···· 7730")).toEqual({ row: rows[1] });
    expect(findRow(rows, "7730")).toEqual({ row: rows[1] });
    expect(findRow(rows, "  PLATINUM ")).toEqual({ row: rows[0] });
  });

  it("asks which one when two match, and says what exists when none does", () => {
    expect(findRow(rows, "card")).toEqual({ ambiguous: rows });
    const both = runPackAction(p, action(p, "freeze_card"), { item: "card" }, rows, {});
    expect(both.answer).toEqual({ status: "refused", message: `Which one? ${rows.map((r) => r.title).join(", ")}.` });
    const none = runPackAction(p, action(p, "freeze_card"), { item: "4444" }, rows, {});
    expect(none.answer.message).toBe(`No card called "4444". Cards: ${rows.map((r) => r.title).join(", ")}.`);
    expect(runPackAction(p, action(p, "freeze_card"), {}, rows, {}).answer.message).toMatch(/^Say which card\./);
  });
});

describe("set_status", () => {
  it("sets the status once, then says it is already set", () => {
    const p = pack("health-coach");
    const rows = rowsFor(p, "MRN-000981");
    const first = runPackAction(p, action(p, "mark_done"), { item: "metformin" }, rows, {});
    expect(first.answer).toEqual({ status: "ok", message: "Metformin 500 mg: Taken today.", data: { item: "metformin", status: "Taken today" } });
    expect(first.effect).toEqual({ type: "set_status", rowId: "metformin", status: "Taken today" });
    const again = runPackAction(p, action(p, "mark_done"), { item: "Metformin" }, rows, { metformin: "Taken today" });
    expect(again.answer.message).toBe("Metformin 500 mg is already Taken today.");
    expect(again.effect).toBeNull();
  });

  it("refuses an action the row does not offer", () => {
    const p = pack("retail-shopping");
    const rows = rowsFor(p, "SHP-20031");
    const r = runPackAction(p, action(p, "request_return"), { item: "GD-55120" }, rows, {});
    expect(r.answer).toEqual({ status: "refused", message: "Return is not available for Running shoes, size 7." });
  });

  it("hides the button for the status a row already has", () => {
    const p = pack("banking-support");
    const [frozen] = rowsFor(p, "CUST-10043").filter((r) => r.status === "Frozen");
    expect(rowButtons(p, frozen, {}).map((a) => a.name)).toEqual(["unfreeze_card"]);
    expect(rowButtons(p, frozen, { [frozen.id]: "Active" }).map((a) => a.name)).toEqual(["freeze_card"]);
  });
});

describe("screen names", () => {
  it("maps the pack's list screen and the generic keys, nothing else", () => {
    const p = pack("retail-shopping");
    expect(screenKeyFor(p, "orders")).toBe("items");
    expect(screenKeyFor(p, "Memory")).toBe("memory");
    expect(screenKeyFor(p, "medicines")).toBeNull();
    expect(screenKeyFor(p, 3)).toBeNull();
  });
});

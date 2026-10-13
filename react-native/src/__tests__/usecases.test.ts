/** The bundled packs are the shared files, valid, and each agent-setup kit declares exactly the pack's actions. */
import { execFileSync } from "child_process";
import { readdirSync } from "fs";
import path from "path";
import { DEFAULT_PACK_ID, PACKS } from "../usecases";

const root = path.resolve(__dirname, "../../..");

it("passes the shared checker, agent-setup included", () => {
  const out = execFileSync(process.execPath, [path.join(root, "scripts/check-usecases.mjs"), "--agent-setup"], { encoding: "utf8" });
  for (const p of PACKS) expect(out).toContain(`✓ ${p.id}.json`);
});

it("bundles every pack in usecases/, with health-coach first as the default", () => {
  const files = readdirSync(path.join(root, "usecases")).filter((f) => f.endsWith(".json") && f !== "usecase.schema.json");
  expect(PACKS.map((p) => `${p.id}.json`).sort()).toEqual(files.sort());
  expect(DEFAULT_PACK_ID).toBe("health-coach");
  expect(PACKS[0].id).toBe(DEFAULT_PACK_ID);
});

it("gives every pack a main agent (the top-level one) and a help agent of its own", () => {
  for (const p of PACKS) {
    expect(p.agents.main.name).toBe(p.agentName);
    expect(p.agents.help.name).not.toBe(p.agentName);
    expect(p.agents.help.reasons.length).toBeGreaterThanOrEqual(2);
  }
});

/** The bundled packs are the shared files, valid, and each agent-setup kit declares exactly the pack's actions. */
import { execFileSync } from "child_process";
import { readdirSync } from "fs";
import path from "path";
import { DEFAULT_PACK_ID, PACKS } from "../usecases";
import { variableNames, variablesFor } from "../logic/pack";

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

it("maps each pack's people to the agent's variables, as an API call would", () => {
  const health = PACKS.find((p) => p.id === "health-coach")!;
  expect(health.variables).toEqual({ callee_name: "name", age: "age" });
  expect(variablesFor(health, health.users[0])).toEqual({ callee_name: "Riya Sharma", age: "34" });
  // Any name the agent's author chose, from the name or any trait; a field the person lacks is left out.
  const custom = { ...health, variables: { name_user: "name", plan_name: "plan", dob: "date_of_birth" } };
  expect(variablesFor(custom, health.users[0])).toEqual({ name_user: "Riya Sharma", plan_name: "Diabetes care" });
  // A pack that names none: the name as callee_name — what greetings and Call History read.
  const none = { ...health, variables: undefined };
  expect(variablesFor(none, health.users[0])).toEqual({ callee_name: "Riya Sharma" });
  expect(variableNames(none)).toEqual(["callee_name"]);
});

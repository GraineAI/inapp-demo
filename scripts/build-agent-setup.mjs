#!/usr/bin/env node
/**
 * Writes agent-setup/<pack>/actions.json from each use-case pack, so the actions the agent is told
 * about are exactly the ones the apps register — and, for a pack whose journey has a first-call flow,
 * agent-setup/<pack>/journey/consultation-flow.json, the flow as the dashboard imports it. Run after
 * editing a pack; `node scripts/check-usecases.mjs --agent-setup` fails while they differ.
 */
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { actionsJsonFor, flowJsonFor } from "./usecase-tools.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const dir = join(root, "usecases");
for (const file of readdirSync(dir).filter((f) => f.endsWith(".json") && f !== "usecase.schema.json").sort()) {
  const pack = JSON.parse(readFileSync(join(dir, file), "utf8"));
  const out = join(root, "agent-setup", pack.id);
  mkdirSync(out, { recursive: true });
  writeFileSync(join(out, "actions.json"), actionsJsonFor(pack));
  console.log(`wrote agent-setup/${pack.id}/actions.json (${pack.actions.length} actions)`);
  const flow = flowJsonFor(pack);
  if (flow) {
    mkdirSync(join(out, "journey"), { recursive: true });
    writeFileSync(join(out, "journey", "consultation-flow.json"), flow);
    console.log(`wrote agent-setup/${pack.id}/journey/consultation-flow.json (${Object.keys(pack.journey.firstCall.flow.steps).length} steps)`);
  }
}

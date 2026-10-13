// The website's pack list and loader (js/packs.js), against the real packs in usecases/.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { PACK_IDS, DEFAULT_PACK_ID, packProblem, loadPacks, choosePack } from "../js/packs.js";

const here = dirname(fileURLToPath(import.meta.url));
const usecases = join(here, "..", "..", "usecases");
const files = readdirSync(usecases).filter((f) => f.endsWith(".json") && f !== "usecase.schema.json");
const read = (id) => JSON.parse(readFileSync(join(usecases, `${id}.json`), "utf8"));

test("the website offers every pack in usecases/, health-coach first", () => {
  assert.deepEqual([...PACK_IDS].sort(), files.map((f) => f.replace(/\.json$/, "")).sort());
  assert.equal(DEFAULT_PACK_ID, "health-coach");
});

test("every real pack can be drawn", () => {
  for (const id of PACK_IDS) assert.equal(packProblem(read(id), id), null, id);
});

test("what the page would crash on is caught, one pack at a time", () => {
  const good = read("health-coach");
  assert.match(packProblem(null), /not a JSON object/);
  assert.match(packProblem([]), /not a JSON object/);
  assert.match(packProblem({ ...good, schemaVersion: 2 }), /schemaVersion/);
  assert.match(packProblem(good, "banking-support"), /id is "health-coach"/);
  assert.match(packProblem({ ...good, users: [] }), /users/);
  assert.match(packProblem({ ...good, users: [{ id: 1 }] }), /user has no id or name/);
  assert.match(packProblem({ ...good, items: { ...good.items, rows: [{ id: "x" }] } }), /row has no id or title/);
  assert.match(packProblem({ ...good, actions: [{ name: "x" }] }), /action has no name or effect/);
  assert.match(packProblem({ ...good, call: { title: "Calls" } }), /"call" is incomplete/);
  assert.match(packProblem({ ...good, agentName: "" }), /agentName/);
});

test("loading keeps order, and reports each pack that failed and why", async () => {
  const answers = {
    "../usecases/health-coach.json": { ok: true, status: 200, json: async () => read("health-coach") },
    "../usecases/banking-support.json": { ok: false, status: 404, json: async () => ({}) },
    "../usecases/retail-shopping.json": { ok: true, status: 200, json: async () => { throw new SyntaxError("bad"); } },
    "../usecases/broken.json": { ok: true, status: 200, json: async () => ({ schemaVersion: 1, id: "broken" }) },
  };
  const fetchImpl = async (url) => {
    if (url === "../usecases/offline.json") throw new TypeError("Failed to fetch");
    return answers[url];
  };
  const { packs, failures } = await loadPacks(
    ["health-coach", "banking-support", "retail-shopping", "broken", "offline"], { fetchImpl });
  assert.deepEqual(packs.map((p) => p.id), ["health-coach"]);
  assert.deepEqual(failures.map((f) => f.id), ["banking-support", "retail-shopping", "broken", "offline"]);
  assert.match(failures[0].problem, /answered 404/);
  assert.match(failures[1].problem, /not valid JSON/);
  assert.match(failures[2].problem, /appTitle/);
  assert.match(failures[3].problem, /could not be fetched/);
});

test("the pack shown: a link, else the device's choice, else the default, else the first", () => {
  const packs = PACK_IDS.map(read);
  assert.equal(choosePack(packs, { fromUrl: "retail-shopping", remembered: "banking-support" }).id, "retail-shopping");
  assert.equal(choosePack(packs, { fromUrl: "nope", remembered: "banking-support" }).id, "banking-support");
  assert.equal(choosePack(packs, { remembered: "gone" }).id, "health-coach");
  assert.equal(choosePack(packs.slice(1), {}).id, "banking-support");
  assert.equal(choosePack([], {}), null);
});

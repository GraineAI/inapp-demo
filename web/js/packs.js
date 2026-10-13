/**
 * The use-case packs this website offers, read from `usecases/` beside it.
 *
 * The packs are the shared JSON every platform bundles (`usecases/README.md`,
 * schema `usecases/usecase.schema.json`). This site fetches them at run time
 * from `../usecases/`, which works both ways the README describes serving it:
 * from the `demo/` folder (the page is at /web/, the packs at /usecases/) and
 * from `web/` alone with the packs copied into `web/usecases/` (".." from the
 * root stays at the root).
 *
 * Adding a pack: put the file in usecases/ and its id in PACK_IDS.
 */

/** The website's pack list. The first is the default. */
export const PACK_IDS = ["health-coach", "banking-support", "retail-shopping"];

export const DEFAULT_PACK_ID = PACK_IDS[0];

/** Where the packs are, relative to this page. */
export const PACKS_BASE = "../usecases/";

/**
 * Why a pack cannot be drawn, or null when it can. Not the schema check (the
 * packs are checked by `node scripts/check-usecases.mjs`): only what this page
 * would crash on, so one broken file costs one pack, not the site.
 */
export function packProblem(pack, expectedId) {
  if (!pack || typeof pack !== "object" || Array.isArray(pack)) return "it is not a JSON object";
  if (pack.schemaVersion !== 1) return `schemaVersion is ${JSON.stringify(pack.schemaVersion)}, this page reads 1`;
  if (expectedId && pack.id !== expectedId) return `its id is "${pack.id}", not "${expectedId}"`;
  const strings = ["appTitle", "agentName", "agentRole", "tagline", "identifierField", "identifierLabel", "userNoun"];
  for (const key of strings) if (typeof pack[key] !== "string" || !pack[key]) return `"${key}" is missing`;
  if (!Array.isArray(pack.users) || !pack.users.length) return `"users" is missing`;
  if (pack.users.some((u) => !u || typeof u.id !== "string" || typeof u.name !== "string")) return "a user has no id or name";
  if (!pack.home || typeof pack.home.title !== "string" || !Array.isArray(pack.home.cards)) return `"home" is incomplete`;
  const items = pack.items;
  if (!items || typeof items.title !== "string" || typeof items.screenName !== "string" || !Array.isArray(items.rows)) {
    return `"items" is incomplete`;
  }
  if (items.rows.some((r) => !r || typeof r.id !== "string" || typeof r.title !== "string")) return "a row has no id or title";
  if (!Array.isArray(pack.actions)) return `"actions" is missing`;
  if (pack.actions.some((a) => !a || typeof a.name !== "string" || typeof a.effect !== "string")) return "an action has no name or effect";
  if (!pack.call || typeof pack.call.title !== "string" || !Array.isArray(pack.call.reasons)) return `"call" is incomplete`;
  if (!Array.isArray(pack.memoryExamples) || !Array.isArray(pack.starters)) return `"memoryExamples" or "starters" is missing`;
  return null;
}

/**
 * Fetch every pack in `ids`. Resolves with the packs that can be drawn, in
 * order, and a line for each that cannot.
 */
export async function loadPacks(ids = PACK_IDS, { base = PACKS_BASE, fetchImpl = globalThis.fetch } = {}) {
  const results = await Promise.all(ids.map(async (id) => {
    const url = `${base}${encodeURIComponent(id)}.json`;
    try {
      const res = await fetchImpl(url, { cache: "no-cache" });
      if (!res.ok) return { id, problem: `${url} answered ${res.status}` };
      let pack;
      try { pack = await res.json(); } catch (e) { return { id, problem: `${url} is not valid JSON` }; }
      const problem = packProblem(pack, id);
      return problem ? { id, problem: `${url}: ${problem}` } : { id, pack };
    } catch (e) {
      return { id, problem: `${url} could not be fetched` };
    }
  }));
  return {
    packs: results.filter((r) => r.pack).map((r) => r.pack),
    failures: results.filter((r) => !r.pack).map((r) => ({ id: r.id, problem: r.problem })),
  };
}

/**
 * Which pack to show: a `?pack=` link, else the one remembered on this device,
 * else the default, else the first that loaded.
 */
export function choosePack(packs, { fromUrl, remembered, fallback = DEFAULT_PACK_ID } = {}) {
  for (const id of [fromUrl, remembered, fallback]) {
    const found = id && packs.find((p) => p.id === id);
    if (found) return found;
  }
  return packs[0] || null;
}

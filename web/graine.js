/**
 * graine.js — every call this website makes to the Graine website loader goes through here.
 *
 * The loader is the script from the dashboard's snippet
 * (`<script src="https://www.graine.ai/embed/v1.js">`); it defines `window.GraineAgent`.
 * Nothing else in this site touches `window.GraineAgent`, for three reasons:
 *
 *  1. The loader arrives over the network after this page, and may not arrive at all (an ad
 *     blocker, a proxy, no connection). Calls made before it lands wait here for it; calls it can
 *     never answer fail here, with a reason the page can show.
 *  2. The loader is released on its own schedule, so a method this page uses may be missing from
 *     the loader a visitor actually got. Each method is looked up when it is called, and a missing
 *     one puts a notice on the page naming the method and the loader's version.
 *  3. The loader's errors carry a `code` (`too_soon`, `device_limit`, …). They leave here as a
 *     GraineError with that code and a message, so the screens never read the loader's own objects.
 *
 * What comes back is the SDK types (WIRE.md → "SDK types"): booked calls with `isUpcoming` and
 * `canCancel`, memories with Dates. The loader owns scheduling — its booking sheet, its rules and its
 * checks — so the page only draws what it returns.
 *
 * Two agents (MULTI-AGENT.md → Website): the main one, and a help one when configured. The loader
 * holds one at a time; useAgent() moves the page between them with load() again — only on a loader
 * that switches cleanly — and the other agent's calls are read through the public calls route.
 *
 * Notices go into the element with id `graine-notices` when the page has one, else the console.
 *
 * Loader methods used: load (again, to switch agents), open, setScreen, defineAction, identify,
 * setMemory, forgetMemory, memories, openBooking, scheduledCalls, cancelScheduledCall,
 * bookingAvailable — and, read-only when present, installId (or getInstallId), version and
 * switchesAgents.
 */

export const DEFAULT_BASE_URL = "https://www.graine.ai";

/** What a call failed with. `code` is the route's own (`too_soon`, …) or one of this file's (below). */
export class GraineError extends Error {
  constructor(code, message, extra = {}) {
    super(message);
    this.name = "GraineError";
    /**
     * The route's code, or: `not_configured` (no agent id / key), `not_loaded` (the loader never
     * arrived), `unsupported` (the loaded loader has no such method), `starting` / `not_started`
     * (the assistant has not started yet / did not start), `unknown`.
     */
    this.code = code;
    this.retryAfterSeconds = extra.retryAfterSeconds == null ? null : extra.retryAfterSeconds;
  }
}

/** In words, what each method gives this page — for the notice when it is missing. */
const FEATURES = {
  identify: "signing in to the assistant (who the visitor is, and memory)",
  setMemory: "the memory switch",
  forgetMemory: "erasing what the assistant remembers",
  memories: "the list of what the assistant remembers",
  openBooking: "booking a call (the booking sheet)",
  scheduledCalls: "the list of booked calls",
  cancelScheduledCall: "cancelling booked calls",
  bookingAvailable: "whether this visitor can book a call",
  switchAgent: "switching the page's assistant to another agent (the Help section's)",
  setScreen: "telling the assistant what is on screen",
  defineAction: "letting the assistant act on this page",
  open: "opening the assistant",
};

/** How a notice names what is missing, where that is not `GraineAgent.<name>()`. */
const DISPLAY = {
  bookingAvailable: "GraineAgent.bookingAvailable",
  switchAgent: "GraineAgent.load() with another agent",
};

/** "1.10.0" ≥ "1.3.0": compares dotted versions number by number. */
export function versionAtLeast(v, min) {
  if (typeof v !== "string") return false;
  const a = v.split(/[.-]/).map((n) => parseInt(n, 10));
  const b = min.split(".").map((n) => parseInt(n, 10));
  for (let i = 0; i < b.length; i++) {
    const x = Number.isFinite(a[i]) ? a[i] : 0;
    if (x !== b[i]) return x > b[i];
  }
  return true;
}

/**
 * Whether a loader switches the page's agent when load() is called again (MULTI-AGENT.md →
 * Website: the one on the page is taken off first). One that cannot would mount a second
 * assistant beside the first, so the page never asks it to: it says so instead. A loader that
 * switches says so by returning a promise from load() (true once the agent is on the page) —
 * the adapter notes that on the first load() — or by `switchesAgents: true`, or a version from
 * 1.3.0 on.
 */
export function loaderSwitchesAgents(a, loadReturnedPromise = false) {
  if (!a) return false;
  if (typeof a.switchesAgents === "boolean") return a.switchesAgents;
  return loadReturnedPromise || versionAtLeast(a.version, "1.3.0");
}

const CODES_BY_STATUS = { 401: "invalid_key", 403: "origin_refused", 404: "not_found", 429: "rate_limited", 503: "unavailable", 400: "invalid_request", 422: "invalid_request" };

// ── Shapes, read defensively ────────────────────────────────────────────────

function toDate(v) {
  if (v instanceof Date) return Number.isNaN(v.getTime()) ? null : v;
  if ((typeof v === "string" && v) || typeof v === "number") {
    const d = new Date(v);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  return null;
}

/** One memory — `{ id, text, createdAt, updatedAt }` — with Dates, or null without an id or text. */
export function normaliseMemory(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = typeof raw.id === "string" && raw.id ? raw.id : null;
  const text = typeof raw.text === "string" ? raw.text : typeof raw.memory === "string" ? raw.memory : null;
  if (!id || !text) return null;
  const createdAt = toDate(raw.createdAt != null ? raw.createdAt : raw.created_at);
  const updatedAt = toDate(raw.updatedAt != null ? raw.updatedAt : raw.updated_at);
  return { id, text, createdAt, updatedAt };
}

/** Wire names → the SDK types' names (WIRE.md → SDK types). */
const STATUS_NAMES = { no_device: "noDevice" };
const SOURCE_NAMES = { sdk: "app" };

/**
 * One booked call as the SDK types describe it (WIRE.md → GraineScheduledCall):
 * `{ id, at: Date|null, status, source, reason, isUpcoming, canCancel }`, or null without an id.
 *
 * The loader decides `isUpcoming` and `canCancel`. A loader from before the SDK types did not
 * say, and used the wire's names ("no_device", "sdk"); for that one they are read off the status
 * here, so the page only ever sees one shape.
 */
export function normaliseCall(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = [raw.id, raw.callId, raw.call_id].find((v) => typeof v === "string" && v);
  if (!id) return null;
  const at = toDate(raw.at != null ? raw.at : raw.scheduledAt != null ? raw.scheduledAt : raw.scheduled_at);
  // A status this page does not know yet is kept as it came (the SDK types' unknown(raw)).
  const given = typeof raw.status === "string" && raw.status ? raw.status : raw.status == null ? "scheduled" : "unknown";
  const status = STATUS_NAMES[given] || given;
  const source = typeof raw.source === "string" && raw.source ? SOURCE_NAMES[raw.source] || raw.source : null;
  return {
    id,
    at,
    status,
    source,
    reason: typeof raw.reason === "string" && raw.reason ? raw.reason : null,
    isUpcoming: typeof raw.isUpcoming === "boolean" ? raw.isUpcoming : status === "scheduled" || status === "ringing",
    canCancel: typeof raw.canCancel === "boolean" ? raw.canCancel : status === "scheduled",
  };
}

/** A status the loader returned (cancelScheduledCall's answer), in the SDK types' names; null when it said none. */
export function normaliseStatus(raw) {
  const s = typeof raw === "string" ? raw : raw && typeof raw.status === "string" ? raw.status : "";
  return s ? STATUS_NAMES[s] || s : null;
}

function listOf(value, key) {
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value[key])) return value[key];
  return [];
}

/** Whatever the loader threw, as a GraineError. */
export function toGraineError(e) {
  if (e instanceof GraineError) return e;
  const code = e && typeof e.code === "string" && e.code ? e.code : "unknown";
  const message = e && typeof e.message === "string" ? e.message : typeof e === "string" ? e : "";
  return new GraineError(code, message, { retryAfterSeconds: e && e.retryAfterSeconds });
}

function isPlaceholder(v) {
  const s = typeof v === "string" ? v.trim() : "";
  return !s || /^your[-_ ]/i.test(s) || s.indexOf("…") !== -1 || s.indexOf("...") !== -1 || s.indexOf("<") !== -1;
}

function joinUrl(base, path) {
  return String(base).replace(/\/+$/, "") + path;
}

function insertScriptTag(doc, src) {
  return new Promise((resolve, reject) => {
    if (!doc || !doc.createElement) { reject(new Error("No document to load the loader into.")); return; }
    const s = doc.createElement("script");
    s.src = src;
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("The loader did not load."));
    (doc.head || doc.documentElement).appendChild(s);
  });
}

/**
 * One adapter per page. `env` exists for the tests: a fake window, a fake
 * script loader, short waits.
 */
export function createGraine(env = {}) {
  const win = env.window || (typeof window !== "undefined" ? window : undefined);
  const doc = env.document || (win && win.document);
  const insertScript = env.insertScript || insertScriptTag;
  const later = env.setTimeout || ((fn, ms) => setTimeout(fn, ms));
  const cancel = env.clearTimeout || ((id) => clearTimeout(id));
  const scriptWaitMs = env.scriptWaitMs == null ? 15000 : env.scriptWaitMs;
  const startWaitMs = env.startWaitMs == null ? 20000 : env.startWaitMs;
  const pollMs = env.pollMs == null ? 250 : env.pollMs;

  /**
   * idle → not_configured
   *      → loading → unavailable (the loader never arrived)
   *                → starting → ready (the assistant is on the page)
   *                           → not_started (the loader refused to start it; see the console)
   */
  let state = "idle";
  let loaderUrl = "";
  let arrived = null;          // settles when the loader is in, or will not come
  let lastLoaderError = "";    // the loader's own "[Graine] …" console line, when it gave one
  const listeners = new Set();
  const noticeEls = new Map();
  const missing = new Set();   // methods the loaded loader does not have
  const waitingActions = new Map();
  let waitingScreen = null;
  let screenWaits = false;
  /** The agents this page may show — `main`, and `help` when configured — and the one on the page now. */
  let agents = { main: null, help: null };
  let current = "main";
  let generation = 0;          // a newer start or switch retires the watcher of an older one
  let loadReturnedPromise = false;

  const api = () => {
    const a = win && win.GraineAgent;
    return a && (typeof a === "object" || typeof a === "function") ? a : null;
  };
  const version = () => {
    const a = api();
    return a && typeof a.version === "string" ? a.version : null;
  };

  function setState(next) {
    if (state === next) return;
    state = next;
    for (const fn of listeners) { try { fn(state); } catch (e) { /* a listener must not stop the others */ } }
  }

  // ── Notices ───────────────────────────────────────────────────────────────

  /** Put (or update) one notice on the page. `lines` are shown as a list under the text. */
  function notice(key, kind, title, text, lines = []) {
    if (env.onNotice) env.onNotice({ key, kind, title, text, lines });
    const box = doc && doc.getElementById ? doc.getElementById("graine-notices") : null;
    if (!box) {
      if (win && win.console) win.console.warn(`[Graine demo] ${title}: ${text} ${lines.join(" ")}`.trim());
      return;
    }
    let el = noticeEls.get(key);
    if (!el || !el.isConnected) {
      el = doc.createElement("div");
      el.className = `notice notice-${kind}`;
      el.setAttribute("data-notice", key);
      const body = doc.createElement("div");
      body.className = "notice-body";
      const t = doc.createElement("p");
      t.className = "notice-title";
      const p = doc.createElement("p");
      p.className = "notice-text";
      const list = doc.createElement("ul");
      list.className = "notice-lines";
      body.append(t, p, list);
      const close = doc.createElement("button");
      close.type = "button";
      close.className = "notice-dismiss";
      close.setAttribute("aria-label", "Dismiss this notice");
      close.textContent = "×";
      close.addEventListener("click", () => el.remove());
      el.append(body, close);
      box.append(el);
      noticeEls.set(key, el);
    }
    // textContent only: these sentences can carry what the loader printed.
    el.querySelector(".notice-title").textContent = title;
    el.querySelector(".notice-text").textContent = text;
    const list = el.querySelector(".notice-lines");
    list.replaceChildren(...lines.map((line) => {
      const li = doc.createElement("li");
      li.textContent = line;
      return li;
    }));
    list.hidden = lines.length === 0;
  }

  function dismiss(key) {
    const el = noticeEls.get(key);
    if (el) el.remove();
    noticeEls.delete(key);
  }

  /** One notice for every method the loaded loader lacks, growing as the page finds them. */
  function unsupported(name) {
    missing.add(name);
    const v = version();
    notice(
      "unsupported", "warning", "This page needs a newer Graine loader",
      `The Graine loader on this page${v ? ` (version ${v})` : ""} does not have these methods yet, so what they ` +
      "do is unavailable here. They work as soon as the page loads a loader that has them.",
      Array.from(missing).map((n) => `${DISPLAY[n] || `GraineAgent.${n}()`} — ${FEATURES[n] || n}`),
    );
  }

  function notStartedText() {
    const host = win && win.location ? win.location.host : "this site";
    return `The loader is on the page but did not start the assistant. Usually the publishable key is wrong, ` +
      `or ${host} is not in the agent's Allowed domains (Embed & Widgets → Install), or the agent is switched off.` +
      (lastLoaderError ? ` The loader said: "${lastLoaderError}"` : " The browser console says which.");
  }

  function unavailableError(name) {
    const what = FEATURES[name] || "this";
    if (state === "not_configured") {
      return new GraineError("not_configured", `The assistant is not configured for this page, so ${what} is unavailable.`);
    }
    return new GraineError("not_loaded", `The Graine loader did not load, so ${what} is unavailable.`);
  }

  // ── Starting ──────────────────────────────────────────────────────────────

  /**
   * The loader reports why it did not start only in the console — it never shows a visitor an
   * error. Reading its "[Graine] …" line lets this demo say why on the page. A production site
   * does not need this.
   */
  function readLoaderErrors() {
    const c = win && win.console;
    if (!c || typeof c.error !== "function" || readLoaderErrors.done) return;
    readLoaderErrors.done = true;
    const original = c.error;
    c.error = function (...args) {
      if (typeof args[0] === "string" && args[0].indexOf("[Graine]") === 0) lastLoaderError = args[0].slice(8).trim();
      return original.apply(this, args);
    };
  }

  function define(a, name, handler) {
    if (typeof a.defineAction !== "function") { unsupported("defineAction"); return; }
    try { a.defineAction(name, handler); } catch (e) { /* the loader must never take the page down */ }
  }

  function sendScreen(a, context) {
    if (typeof a.setScreen !== "function") { unsupported("setScreen"); return; }
    try { a.setScreen(context); } catch (e) { /* as above */ }
  }

  /**
   * The loader adds open() once the assistant is on the page; until then there is nothing to open.
   * After a switch the new widget brings a new open() (or its frame shows the new agent), so the
   * old widget's open() does not count as the new one being up.
   */
  function watchStart(previousOpen, agentId) {
    const began = Date.now();
    const mine = ++generation;
    let reported = false;
    const up = (a) => typeof a.open === "function" && (a.open !== previousOpen || frameShows(agentId));
    const tick = () => {
      if (mine !== generation) return;
      const a = api();
      if (a && up(a)) {
        dismiss("not_started");
        setState("ready");
        return;
      }
      const waited = Date.now() - began;
      if (!reported && waited >= startWaitMs) {
        reported = true;
        setState("not_started");
        notice("not_started", "warning", "The assistant did not start", notStartedText());
      }
      // A slow start still counts: keep looking, less often, for a while.
      if (waited < startWaitMs * 6) later(tick, reported ? pollMs * 8 : pollMs);
    };
    tick();
  }

  /** Whether the loader's frame on the page is this agent's (its address names the agent). */
  function frameShows(agentId) {
    if (!agentId || !doc || typeof doc.querySelectorAll !== "function") return false;
    const marker = `/embed/${encodeURIComponent(agentId)}?`;
    return Array.from(doc.querySelectorAll("iframe")).some((f) => String(f.getAttribute("src") || "").indexOf(marker) !== -1);
  }

  /**
   * load() an agent and follow it onto the page: through the promise a newer loader returns
   * (true once the agent is on the page, false when it could not start), else by watching for its
   * widget's open().
   */
  function loadAgent(a, { agentId, publishableKey }, previousOpen) {
    const result = a.load({ agentId, publishableKey });
    if (!result || typeof result.then !== "function") {
      watchStart(previousOpen, agentId);
      return;
    }
    loadReturnedPromise = true;
    const mine = ++generation;
    result.then((ok) => {
      if (mine !== generation) return;          // another load() has the page now
      if (ok === false) {
        setState("not_started");
        notice("not_started", "warning", "The assistant did not start", notStartedText());
        return;
      }
      dismiss("not_started");
      setState("ready");
    }, () => {
      if (mine !== generation) return;
      setState("not_started");
      notice("not_started", "warning", "The assistant did not start", notStartedText());
    });
  }

  function begin({ agentId, publishableKey }) {
    const a = api();
    if (!a || typeof a.load !== "function") {
      setState("unavailable");
      notice("not_loaded", "warning", "The assistant is unavailable",
        `${loaderUrl} loaded, but it is not the Graine loader. Check baseUrl in config.js.`);
      return;
    }
    dismiss("not_loaded");
    setState("starting");
    // What the page set up while the loader was on its way: actions first, then the screen, so
    // the first snapshot the assistant gets already lists every action.
    for (const [name, handler] of waitingActions) define(a, name, handler);
    waitingActions.clear();
    if (screenWaits) { screenWaits = false; sendScreen(a, waitingScreen); }
    try {
      loadAgent(a, { agentId, publishableKey }, undefined);
    } catch (e) {
      setState("not_started");
      notice("not_started", "warning", "The assistant did not start", notStartedText());
    }
  }

  function pickAgent(given) {
    if (!given || typeof given !== "object") return null;
    const agentId = typeof given.agentId === "string" ? given.agentId.trim() : "";
    const publishableKey = typeof given.publishableKey === "string" ? given.publishableKey.trim() : "";
    return isPlaceholder(agentId) || isPlaceholder(publishableKey) ? null : { agentId, publishableKey };
  }

  /**
   * Load the loader and start the main agent's assistant. Once per page; useAgent() moves the
   * page to the other agent and back.
   *
   *   start({ agents: { main: { agentId, publishableKey }, help?: { agentId, publishableKey } }, baseUrl?, loaderUrl? })
   *   start({ agentId, publishableKey, baseUrl?, loaderUrl? })          // one agent
   */
  function start(cfg = {}) {
    if (arrived) return arrived;
    const given = cfg.agents && typeof cfg.agents === "object" ? cfg.agents : { main: cfg };
    agents = { main: pickAgent(given.main), help: pickAgent(given.help) };
    current = "main";
    if (!agents.main) {
      setState("not_configured");
      notice("not_configured", "info", "The assistant is not configured",
        "This page has no agent id or publishable key (web/config.js), so the assistant is off. " +
        "Everything else on the page works without it.");
      arrived = Promise.resolve();
      return arrived;
    }
    loaderUrl = typeof cfg.loaderUrl === "string" && cfg.loaderUrl.trim()
      ? cfg.loaderUrl.trim()
      : joinUrl(cfg.baseUrl || DEFAULT_BASE_URL, "/embed/v1.js");
    readLoaderErrors();
    setState("loading");
    arrived = new Promise((resolve) => {
      let settled = false;
      const settle = () => { if (!settled) { settled = true; resolve(); } };
      const fail = () => {
        if (api()) return;
        setState("unavailable");
        notice("not_loaded", "warning", "The assistant is unavailable",
          `The Graine loader did not load from ${loaderUrl}. A browser extension, a network filter or ` +
          "the connection may be blocking it. Everything else on the page works without it.");
      };
      const timer = later(() => { fail(); settle(); }, scriptWaitMs);
      insertScript(doc, loaderUrl).then(
        // A loader that lands after the wait still starts: the late arrival is better than none.
        () => { cancel(timer); begin(agents.main); settle(); },
        () => { cancel(timer); fail(); settle(); },
      );
    });
    return arrived;
  }

  // ── The agent that is not on the page ────────────────────────────────────

  /**
   * The website loader holds one agent at a time (MULTI-AGENT.md: load() again switches it). To
   * list or cancel the OTHER agent's calls without taking the visitor's assistant off the page,
   * this asks the same public route the loader uses (WIRE.md → calls/list, calls/cancel) with that
   * agent's publishable key, this browser's install id and the signed-in identifier — the identity
   * channel's only fields. It is the one place this page speaks the wire itself.
   */
  async function otherAgentPost(path, agent, identity, extra) {
    if (arrived) await arrived;
    const a = api();
    let installId = null;
    try {
      installId = a && (typeof a.installId === "string" ? a.installId : typeof a.getInstallId === "function" ? a.getInstallId() : null);
    } catch (e) { installId = null; }
    if (!a || typeof installId !== "string" || !installId) {
      throw new GraineError("unsupported", "The Graine loader on this page does not show its install id, so the other agent's calls cannot be read.");
    }
    let base;
    try { base = new URL(loaderUrl, win.location.href).origin; } catch (e) { base = DEFAULT_BASE_URL; }
    const body = { publishableKey: agent.publishableKey, installId, ...extra };
    if (identity) body.identity = { value: String(identity) };
    const send = env.fetch || (win && win.fetch && win.fetch.bind(win));
    let res;
    try {
      res = await send(`${base}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    } catch (e) {
      throw new GraineError("network", "Could not reach Graine. Check the connection and try again.");
    }
    let data = {};
    try { data = await res.json(); } catch (e) { data = {}; }
    if (!res.ok) {
      const code = data && typeof data.code === "string" && data.code ? data.code : CODES_BY_STATUS[res.status] || "unknown";
      let wait = null;
      try { const h = res.headers && res.headers.get && res.headers.get("retry-after"); if (h !== null && h !== undefined && Number.isFinite(Number(h))) wait = Number(h); } catch (e) { wait = null; }
      throw new GraineError(code, (data && data.error) || `The request was refused (${res.status}).`, { retryAfterSeconds: wait });
    }
    return data;
  }

  // ── Calls ─────────────────────────────────────────────────────────────────

  async function call(name, args) {
    if (arrived) await arrived;
    const a = api();
    if (!a) throw unavailableError(name);
    const fn = a[name];
    if (typeof fn !== "function") {
      unsupported(name);
      throw new GraineError("unsupported", `The Graine loader on this page has no ${name}().`);
    }
    try {
      return await fn.apply(a, args);
    } catch (e) {
      throw toGraineError(e);
    }
  }

  return {
    start,

    /** idle, not_configured, loading, unavailable, starting, ready or not_started. */
    get state() { return state; },

    /** The agent on the page now: "main" or "help". */
    get agent() { return current; },
    /** Whether `name` ("main" / "help") is configured for this page. */
    hasAgent(name) { return !!agents[name]; },
    /** Whether the loaded loader can move the page from one agent to another. */
    canSwitch() { return loaderSwitchesAgents(api(), loadReturnedPromise); },

    /**
     * Put `name`'s assistant on the page ("help" in the Help section, "main" everywhere else):
     * load() again with that agent, which a loader that switches agents does cleanly. Who is
     * signed in, the screen and the actions stay. Resolves when the switch is under way.
     */
    async useAgent(name) {
      const target = agents[name];
      if (!target) throw new GraineError("not_configured", `No ${name} agent is configured for this page.`);
      if (arrived) await arrived;
      if (name === current) return;
      const a = api();
      if (!a || typeof a.load !== "function") throw unavailableError("switchAgent");
      if (!loaderSwitchesAgents(a, loadReturnedPromise)) {
        unsupported("switchAgent");
        throw new GraineError("unsupported", "The Graine loader on this page cannot switch to another agent.");
      }
      const previousOpen = a.open;
      current = name;
      setState("starting");
      try {
        loadAgent(a, target, previousOpen);
      } catch (e) {
        setState("not_started");
        notice("not_started", "warning", "The assistant did not start", notStartedText());
        throw toGraineError(e);
      }
    },

    /**
     * `name`'s calls for the signed-in person (`identity` = their identifier): through the loader
     * for the agent on the page, through the public route for the other one.
     */
    async scheduledCallsOf(name, identity) {
      const target = agents[name];
      if (!target) return [];
      if (name === current) return listOf(await call("scheduledCalls", []), "calls").map(normaliseCall).filter(Boolean);
      const data = await otherAgentPost("/api/embed/calls/list", target, identity, {});
      return listOf(data, "calls").map(normaliseCall).filter(Boolean);
    },

    /** Cancel one of `name`'s calls. Resolves the call's status afterwards. */
    async cancelScheduledCallOf(name, id, identity) {
      const target = agents[name];
      if (!target) throw new GraineError("not_configured", `No ${name} agent is configured for this page.`);
      if (name === current) return normaliseStatus(await call("cancelScheduledCall", [id]));
      const data = await otherAgentPost("/api/embed/calls/cancel", target, identity, { callId: id });
      return normaliseStatus(data && data.status ? data.status : "cancelled");
    },
    onState(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    get loaderUrl() { return loaderUrl; },
    get loaderError() { return lastLoaderError; },
    version,

    /** Whether the loaded loader has `name` — for a screen that explains what is missing. */
    supports(name) { const a = api(); return !!a && typeof a[name] === "function"; },

    /** The browser's install id, when the loader shows it (read-only, never sent anywhere by this page). */
    installId() {
      const a = api();
      if (!a) return null;
      try {
        if (typeof a.installId === "string") return a.installId || null;
        const read = typeof a.installId === "function" ? a.installId : typeof a.getInstallId === "function" ? a.getInstallId : null;
        const v = read ? read.call(a) : null;
        return typeof v === "string" && v ? v : null;
      } catch (e) { /* not available */ }
      return null;
    },

    /**
     * Whether the visitor can book a call with the agent on the page (the loader's
     * `bookingAvailable`: the agent lets visitors book, and the visitor is identified). False until
     * the assistant has started, and on a loader without it — which the notice then names.
     */
    bookingAvailable() {
      const a = api();
      if (!a || state !== "ready") return false;
      // A loader without the booking sheet cannot book from here: the notice names what it lacks.
      const sheet = typeof a.openBooking === "function";
      if (!sheet) unsupported("openBooking");
      if (!("bookingAvailable" in a)) { unsupported("bookingAvailable"); return false; }
      try { return sheet && a.bookingAvailable === true; } catch (e) { return false; }
    },

    /** Open the assistant's panel (voice and chat). */
    async open() {
      if (arrived) await arrived;
      const a = api();
      if (!a) throw unavailableError("open");
      if (typeof a.open !== "function") {
        if (state === "starting") throw new GraineError("starting", "The assistant is still starting. Try again in a moment.");
        notice("not_started", "warning", "The assistant did not start", notStartedText());
        throw new GraineError("not_started", "The assistant did not start on this page.");
      }
      a.open();
    },

    /** Tell the assistant what is on screen. Never throws; held until the loader arrives. */
    setScreen(context) {
      const a = api();
      if (!a || state === "loading" || state === "idle") { waitingScreen = context; screenWaits = true; return; }
      sendScreen(a, context);
    },

    /** Let the assistant run `name` on this page. Never throws; held until the loader arrives. */
    defineAction(name, handler) {
      const safe = async (args) => {
        try { return await handler(args || {}); } catch (e) { return { status: "error", message: (e && e.message) || "failed" }; }
      };
      const a = api();
      if (!a || state === "loading" || state === "idle") { waitingActions.set(name, safe); return; }
      define(a, name, safe);
    },

    /** Who the visitor is, and whether their conversations may be remembered. */
    async identify(traits, { memory } = {}) {
      await call("identify", [traits, { memory: memory !== false }]);
    },

    /** Sign out: later conversations are anonymous, and this browser is released from the person. */
    async signOut() {
      await call("identify", [null]);
    },

    async setMemory(enabled) {
      await call("setMemory", [!!enabled]);
    },

    /** Forget everything remembered about the signed-in person. The number deleted, when the loader says. */
    async forgetMemory() {
      const r = await call("forgetMemory", []);
      if (typeof r === "number") return r;
      return r && typeof r.deleted === "number" ? r.deleted : null;
    },

    /** What the assistant remembers about the signed-in person, newest first. */
    async memories() {
      const r = await call("memories", []);
      return listOf(r, "memories").map(normaliseMemory).filter(Boolean);
    },

    /**
     * Open the widget's booking sheet for the agent on the page — the day, the time, the time zone
     * and an optional note, in the agent's design — its "What's it about?" starting with `reason`.
     * The sheet checks the agent's rules and words any refusal itself. Resolves with the booked call,
     * or null when the visitor closed it without booking.
     */
    async openBooking({ reason } = {}) {
      const r = await call("openBooking", [reason ? { reason } : {}]);
      return r ? normaliseCall(r.call ? r.call : r) : null;
    },

    /** The signed-in person's calls, upcoming first. */
    async scheduledCalls() {
      const r = await call("scheduledCalls", []);
      return listOf(r, "calls").map(normaliseCall).filter(Boolean);
    },

    /** Cancel one. Resolves with the call's status afterwards (`cancelled`), when the loader says. */
    async cancelScheduledCall(id) {
      return normaliseStatus(await call("cancelScheduledCall", [id]));
    },
  };
}

/** This page's adapter. */
export const graine = createGraine();

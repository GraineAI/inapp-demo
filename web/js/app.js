/**
 * Graine Demo — the website. Boots the page: reads config.js, loads the
 * use-case packs, starts the assistant through graine.js, and draws the
 * screens (views.js) from the chosen pack.
 *
 * Every call to the Graine loader goes through ../graine.js. Every rule that
 * can run without a page is in logic.js.
 */
import { graine, DEFAULT_BASE_URL } from "../graine.js";
import { PACK_IDS, DEFAULT_PACK_ID, loadPacks, choosePack } from "./packs.js";
import * as L from "./logic.js";
import * as V from "./views.js";
import { h, store, toast, confirmDialog, copyText } from "./dom.js";

const KEYS = {
  pack: "graine-demo.pack",
  session: "graine-demo.session",
  consent: "graine-demo.memory-consent",
  theme: "graine-demo.theme",
};

const ROUTES = ["home", "chat", "items", "calls", "memory", "profile", "help"];

/** Errors the adapter already explains in a notice; a screen shows its own line instead of repeating them. */
const EXPLAINED = new Set(["unsupported", "not_loaded", "not_configured"]);

const emptyList = () => ({ list: [], loading: false, error: null, helpError: null, loaded: false });

/** One agent's past sessions as this page holds them, and the transcript open from them. */
const emptyPast = () => ({
  sessions: [], loading: false, hasMore: false, cursor: null, error: null, message: null, loaded: false, gen: 0,
  transcript: null, opening: null, transcriptError: null,
});

const state = {
  config: {},
  packs: [],
  pack: null,
  /** The page's agents from config.js: main, and help when configured. */
  agents: { main: { agentId: "", publishableKey: "" }, help: null, baseUrl: DEFAULT_BASE_URL, loaderUrl: "" },
  user: null,
  memoryOn: true,
  route: "home",
  openRowId: null,
  /** Row statuses set this page session, per user (the pack's rule: they reset on restart). */
  statuses: {},
  calls: emptyList(),
  memories: emptyList(),
  /** Each agent's past sessions (the Chat section shows the main agent's, Help the help agent's). */
  past: { main: emptyPast(), help: emptyPast() },
  signIn: { userId: null, memory: true },
  busy: { memory: false, erase: false, cancel: null },
  theme: "system",
  /** Settles once the current identify() has been handed over (never rejects). */
  identifying: Promise.resolve(),
  /** Settles once a switch between agents is under way (never rejects): lists wait for it. */
  switching: Promise.resolve(),
};

// ── Config, theme, chrome ───────────────────────────────────────────────────

async function readConfig() {
  try {
    const mod = await import("../config.js");
    const cfg = mod.default || mod.config;
    return cfg && typeof cfg === "object" ? cfg : {};
  } catch (e) {
    return {};   // no config.js yet: everything but the assistant works
  }
}

/**
 * The agents for a pack: `main`, and `help` when one is configured. The pack's own entry in
 * `agents` wins over the top-level ones; an agent needs both its id and its key.
 */
function agentsFor(config, pack) {
  const both = (a) => (a && typeof a === "object" && typeof a.agentId === "string" && a.agentId.trim()
    && typeof a.publishableKey === "string" && a.publishableKey.trim()
    ? { agentId: a.agentId.trim(), publishableKey: a.publishableKey.trim() } : null);
  const own = config.agents && typeof config.agents === "object" && config.agents[pack.id] ? config.agents[pack.id] : {};
  return {
    main: both(own) || both(config) || { agentId: "", publishableKey: "" },
    help: both(own.help) || both(config.help),
    baseUrl: typeof config.baseUrl === "string" && config.baseUrl.trim() ? config.baseUrl.trim() : DEFAULT_BASE_URL,
    loaderUrl: typeof config.loaderUrl === "string" ? config.loaderUrl.trim() : "",
  };
}

/** The display name of the agent on the page now. */
function currentAgentName() {
  const help = L.agentInfo(state.pack, "help");
  return graine.agent === "help" && help ? help.name : L.agentInfo(state.pack, "main").name;
}

function applyTheme(theme) {
  state.theme = theme === "light" || theme === "dark" ? theme : "system";
  const root = document.documentElement;
  if (state.theme === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", state.theme);
  const toggle = document.getElementById("theme-toggle");
  if (toggle) {
    const words = { system: "Match this device", light: "Light", dark: "Dark" }[state.theme];
    toggle.setAttribute("aria-label", `Theme: ${words}. Change theme`);
    toggle.title = `Theme: ${words}`;
    toggle.dataset.theme = state.theme;
  }
}

function setTheme(theme) {
  applyTheme(theme);
  store.set(KEYS.theme, state.theme);
  render();
}

function applyPackChrome(pack) {
  document.title = `${pack.appTitle} · Graine Demo`;
  document.getElementById("app-title").textContent = pack.appTitle;
  if (/^#[0-9a-f]{6}$/i.test(pack.accent || "")) document.documentElement.style.setProperty("--brand", pack.accent);
}

const STATE_TONE = {
  ready: "positive", not_configured: "neutral", idle: "info", loading: "info", starting: "info",
  unavailable: "warning", not_started: "warning",
};

function renderChrome() {
  const pill = document.getElementById("assistant-status");
  if (pill && state.pack) {
    pill.textContent = `${currentAgentName()}: ${V.assistantWords(graine.state)}`;
    pill.className = `chip chip-${STATE_TONE[graine.state] || "neutral"} status-pill`;
  }
  const nav = document.getElementById("nav");
  if (!nav) return;
  if (!state.pack || !state.user) {
    nav.hidden = true;
    nav.replaceChildren();
    return;
  }
  nav.hidden = false;
  const label = {
    home: state.pack.home.title, chat: "Chat", items: state.pack.items.title, calls: state.pack.call.title,
    memory: "Memory", profile: "Profile", help: "Help",
  };
  nav.replaceChildren(h("ul", null, ROUTES.map((r) => h("li", null,
    h("a", { href: `#/${r}`, "aria-current": state.route === r ? "page" : null, "data-route": r }, label[r])))));
}

function appNotice(key, title, text, lines = []) {
  const box = document.getElementById("app-notices");
  if (!box) return;
  const el = h("div", { class: "notice notice-warning", "data-notice": key },
    h("div", { class: "notice-body" },
      h("p", { class: "notice-title" }, title),
      h("p", { class: "notice-text" }, text),
      lines.length ? h("ul", { class: "notice-lines" }, lines.map((l) => h("li", null, l))) : null),
    h("button", { type: "button", class: "notice-dismiss", "aria-label": "Dismiss this notice", onClick: () => el.remove() }, "×"));
  box.append(el);
}

// ── Rendering ───────────────────────────────────────────────────────────────

let lastContext = "";

function statusesFor(userId) {
  if (!state.statuses[userId]) state.statuses[userId] = {};
  return state.statuses[userId];
}

function context() {
  const pack = state.pack;
  if (!pack) return { screen: "sign_in", title: "Sign in", fields: [] };
  return L.screenContext({
    pack,
    screen: state.user ? state.route : "sign_in",
    userId: state.user && state.user.id,
    memoryOn: state.memoryOn,
    statuses: state.user ? statusesFor(state.user.id) : {},
    calls: state.calls.list,
    memoriesCount: state.memories.list.length,
  });
}

function viewContext() {
  const agents = state.agents;
  const main = agents.main;
  return {
    pack: state.pack,
    packs: state.packs,
    user: state.user,
    memoryOn: state.memoryOn,
    route: state.route,
    openRowId: state.openRowId,
    statuses: state.user ? statusesFor(state.user.id) : {},
    calls: state.calls,
    memories: state.memories,
    past: state.past,
    signIn: state.signIn,
    busy: state.busy,
    theme: state.theme,
    now: new Date(),
    // Whether the visitor can book with the agent on the page (false until it has started): only the
    // booking screens ask, and only once the page shows the agent that screen books with.
    bookable: !!state.user && graine.agent === screenAgent(state.route) && (state.route === "calls" || state.route === "help")
      && graine.bookingAvailable(),
    agents: { main: L.agentInfo(state.pack, "main"), help: L.agentInfo(state.pack, "help") },
    helpConfigured: graine.hasAgent("help"),
    assistant: {
      state: graine.state,
      agent: graine.agent,
      canSwitch: graine.canSwitch(),
      version: graine.version(),
      installId: graine.installId(),
      agentId: main.agentId,
      helpAgentId: agents.help ? agents.help.agentId : "",
      baseUrl: agents.loaderUrl || agents.baseUrl,
      supports: (name) => graine.supports(name),
    },
    on: handlers,
  };
}

function buildView() {
  const ctx = viewContext();
  if (!state.user) return V.signInView(ctx);
  switch (state.route) {
    case "chat": return V.chatView(ctx);
    case "items": return V.itemsView(ctx);
    case "calls": return V.callsView(ctx);
    case "memory": return V.memoryView(ctx);
    case "profile": return V.profileView(ctx);
    case "help": return V.helpView(ctx);
    default: return V.homeView(ctx);
  }
}

/**
 * Draw the current screen and tell the assistant what is on it. `focus` moves
 * keyboard focus to the new heading — only for the visitor's own navigation:
 * when the assistant navigates, the visitor may be typing in its chat, and
 * pulling focus out of it would eat their words.
 */
function render({ focus = false } = {}) {
  if (!state.pack) return;
  renderChrome();
  const main = document.getElementById("main");
  const activeId = document.activeElement && main.contains(document.activeElement) ? document.activeElement.id : "";
  main.replaceChildren(buildView());
  if (focus) {
    const heading = main.querySelector("h1");
    if (heading) heading.focus();
  } else if (activeId) {
    const again = document.getElementById(activeId);
    if (again) again.focus({ preventScroll: true });
  }
  const ctx = JSON.stringify(context());
  if (ctx !== lastContext) {
    lastContext = ctx;
    graine.setScreen(JSON.parse(ctx));
  }
}

function routeFromHash() {
  const m = /^#\/([a-z_]+)/.exec(window.location.hash || "");
  return m && ROUTES.includes(m[1]) ? m[1] : "home";
}

/** Go to a screen (a button, a card, or the assistant's open_screen). */
function go(screen, { focus = false } = {}) {
  if (!ROUTES.includes(screen)) return;
  const changed = state.route !== screen;
  state.route = screen;
  if (window.location.hash !== `#/${screen}`) window.location.hash = `#/${screen}`;
  if (changed) enter(screen);
  render({ focus: focus && changed });
}

/** The agent a screen talks to: the help agent in Help, the main agent everywhere else. */
function screenAgent(screen) {
  return screen === "help" ? "help" : "main";
}

/**
 * Put the screen's agent on the page. The loader switches cleanly when load() is called again
 * with the other agent; the same person stays signed in (identify again, which is the same call
 * and a no-op for a loader that already shares it across agents).
 */
function switchTo(name) {
  if (!graine.hasAgent(name) || graine.agent === name) return state.switching;
  state.switching = graine.useAgent(name).then(() => {
    if (state.user) identifyCurrent();
    render();
  }, (e) => { quietly(e); render(); });
  return state.switching;
}

function enter(screen) {
  switchTo(screenAgent(screen));
  if (screen === "calls" || screen === "home" || screen === "help") refreshCalls();
  if (screen === "memory") refreshMemories();
  if (screen === "chat") refreshPast("main");
  if (screen === "help" && graine.hasAgent("help")) refreshPast("help");
}

// ── Signing in and out ──────────────────────────────────────────────────────

function quietly(e) {
  if (!e || EXPLAINED.has(e.code)) return;
  toast(L.messageFor(e.code, e.message));
}

function identifyCurrent() {
  const { pack, user, memoryOn } = state;
  state.identifying = graine.identify(L.traitsFor(pack, user), { memory: memoryOn }).catch(quietly);
  return state.identifying;
}

function saveSession() {
  if (!state.user) { store.remove(KEYS.session); return; }
  store.setJSON(KEYS.session, { packId: state.pack.id, userId: state.user.id, memory: state.memoryOn });
  const consent = store.getJSON(KEYS.consent) || {};
  consent[`${state.pack.id}:${state.user.id}`] = state.memoryOn;
  store.setJSON(KEYS.consent, consent);
}

function rememberedConsent(userId) {
  const consent = store.getJSON(KEYS.consent) || {};
  const v = consent[`${state.pack.id}:${userId}`];
  return typeof v === "boolean" ? v : true;
}

/** What the booking sheet's "What's it about?" starts with: the help agent's first reason in Help, the pack's elsewhere. */
function bookingReason(screen) {
  const help = L.agentInfo(state.pack, "help");
  const list = screen === "help" && help ? help.reasons : state.pack.call.reasons;
  return (Array.isArray(list) && list[0]) || "";
}

function resetPersonData() {
  state.calls = emptyList();
  state.memories = emptyList();
  state.past = { main: emptyPast(), help: emptyPast() };
  state.openRowId = null;
  state.busy = { memory: false, erase: false, cancel: null };
}

function signIn() {
  const user = state.pack.users.find((u) => u.id === state.signIn.userId);
  if (!user) return;
  state.user = user;
  state.memoryOn = state.signIn.memory;
  resetPersonData();
  saveSession();
  identifyCurrent();
  state.route = "home";
  if (window.location.hash !== "#/home") window.location.hash = "#/home";
  enter("home");
  render({ focus: true });
}

async function signOut() {
  // identify(null) signs the person out of every agent; the page goes back to the main one.
  const done = graine.signOut().catch(quietly);
  switchTo("main");
  state.user = null;
  state.signIn = { userId: null, memory: true };
  resetPersonData();
  saveSession();
  history.replaceState(null, "", window.location.pathname + window.location.search);
  render({ focus: true });
  toast("Signed out.");
  await done;
}

/**
 * Another use case is another agent, and the loader mounts one agent per page:
 * a new page. Nobody is signed in on the sign-in screen, so there is nobody to
 * sign out first.
 */
function switchPack(id) {
  if (!state.packs.some((p) => p.id === id) || id === state.pack.id) return;
  store.set(KEYS.pack, id);
  window.location.assign(`${window.location.pathname}?pack=${encodeURIComponent(id)}`);
}

// ── Lists ───────────────────────────────────────────────────────────────────

/**
 * Both agents' calls for the signed-in person, each marked with its agent: the main agent's and,
 * when one is configured, the help agent's (usecases/README.md → Two agents).
 */
async function refreshCalls() {
  if (!state.user) return;
  const who = state.user.id;
  state.calls.loading = true;
  render();
  await state.switching;     // asked of the agent the screen talks to, not the one it is leaving
  await state.identifying;   // the lists are this person's: identity first
  const names = ["main", "help"].filter((n) => graine.hasAgent(n));
  const results = await Promise.all(names.map((agent) => graine.scheduledCallsOf(agent, who).then(
    (list) => ({ agent, list: list.map((c) => ({ ...c, agent })) }),
    (error) => ({ agent, error }))));
  if (!state.user || state.user.id !== who) return;
  const words = (e) => (EXPLAINED.has(e.code) ? null : L.messageFor(e.code, e.message));
  const failed = (agent) => { const r = results.find((x) => x.agent === agent && x.error); return r ? words(r.error) : null; };
  state.calls = {
    list: results.flatMap((r) => r.list || []),
    loading: false,
    loaded: true,
    error: failed("main"),
    helpError: failed("help"),
  };
  render();
}

async function refreshMemories() {
  if (!state.user) return;
  const who = state.user.id;
  state.memories.loading = true;
  render();
  try {
    await state.switching;
    await state.identifying;
    const list = await graine.memories();
    if (!state.user || state.user.id !== who) return;
    state.memories = { list, loading: false, error: null, loaded: true };
  } catch (e) {
    if (!state.user || state.user.id !== who) return;
    state.memories = { ...state.memories, loading: false, loaded: true, error: EXPLAINED.has(e.code) ? null : L.messageFor(e.code, e.message) };
  }
  render();
}

// ── Past sessions ───────────────────────────────────────────────────────────

/**
 * `agent`'s past sessions with the signed-in person: the first page again (a refresh, which replaces
 * what was loaded and supersedes a page still on its way), or with `more` the next one, appended —
 * only while there is one and nothing is on its way. A refusal keeps what was loaded, and the page
 * asked for: asking again asks for it again.
 */
async function refreshPast(agent, { more = false } = {}) {
  if (!state.user || !graine.hasAgent(agent)) return;
  const p = state.past[agent];
  if (more && (p.loading || !p.hasMore || !p.cursor)) return;
  if (!more) p.gen += 1;
  const gen = p.gen;
  const who = state.user.id;
  const stale = () => !state.user || state.user.id !== who || state.past[agent] !== p || p.gen !== gen;
  p.loading = true;
  p.error = null;
  p.message = null;
  render();
  await state.switching;
  await state.identifying;
  try {
    const page = await graine.pastSessionsOf(agent, who, more ? p.cursor : null);
    if (stale()) return;
    p.sessions = L.pastSessionsAfter(p.sessions, page.sessions, { more });
    p.cursor = page.next;
    p.hasMore = page.next != null;
    p.error = null;
  } catch (e) {
    if (stale()) return;
    p.error = (e && e.code) || "unknown";
    p.message = (e && e.message) || null;
  }
  p.loading = false;
  p.loaded = true;
  render();
}

/** A past session's transcript, read only; or why it could not be opened, in plain words. */
async function openTranscript(agent, id) {
  if (!state.user) return;
  const p = state.past[agent];
  const who = state.user.id;
  const stale = () => !state.user || state.user.id !== who || state.past[agent] !== p || p.opening !== id;
  p.opening = id;
  p.transcriptError = null;
  render();
  try {
    const transcript = await graine.sessionTranscriptOf(agent, who, id);
    if (stale()) return;
    p.transcript = transcript;
  } catch (e) {
    if (stale()) return;
    p.transcriptError = L.transcriptError(e && e.code) || L.messageFor(e && e.code, e && e.message);
  }
  p.opening = null;
  render();
}

// ── The agent's actions ─────────────────────────────────────────────────────

/** Apply what an action decided: a status, an open row, a screen. */
function apply(effects) {
  if (effects.setStatus && state.user) statusesFor(state.user.id)[effects.setStatus.rowId] = effects.setStatus.status;
  if (effects.openRow) state.openRowId = effects.openRow;
  if (effects.screen) go(effects.screen);
  else render();
  if (effects.openRow) {
    const el = document.getElementById(`row-${effects.openRow}`);
    if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
  }
}

/** Every action in the pack, registered under its name and run by its effect (usecases/README.md). */
function registerActions(pack) {
  for (const action of pack.actions) {
    graine.defineAction(action.name, (args) => {
      const { result, effects } = L.decideAction(state.pack, action, args, {
        userId: state.user && state.user.id,
        statuses: state.user ? statusesFor(state.user.id) : {},
      });
      apply(effects);
      if (result.status === "ok" && effects.setStatus) toast(`${state.pack.agentName}: ${result.message}`);
      return result;
    });
  }
}

// ── What the screens can ask for ────────────────────────────────────────────

const handlers = {
  go: (screen) => go(screen, { focus: true }),
  switchPack,
  pickUser(userId) {
    state.signIn = { userId, memory: rememberedConsent(userId) };
    render();
  },
  setSignInMemory(on) {
    state.signIn.memory = !!on;
    render();
  },
  signIn,
  signOut,
  setTheme,

  async talk() {
    try {
      await graine.open();
    } catch (e) {
      toast(e.message || L.messageFor(e.code));
    }
  },

  async starter(text) {
    const copied = await copyText(text);
    try {
      await graine.open();
      toast(copied ? "Copied. Paste it into the chat." : `Type this into the chat: ${text}`);
    } catch (e) {
      toast(e.message || L.messageFor(e.code));
    }
  },

  toggleRow(rowId) {
    state.openRowId = state.openRowId === rowId ? null : rowId;
    render();
  },

  /** A status button: the same rule the assistant's action follows. */
  setRowStatus(rowId, actionName) {
    const action = L.actionNamed(state.pack, actionName);
    if (!action || !state.user) return;
    const { result, effects } = L.decideAction(state.pack, action, { item: rowId }, {
      userId: state.user.id,
      statuses: statusesFor(state.user.id),
    });
    if (effects.setStatus) apply({ setStatus: effects.setStatus });
    else if (result.status !== "ok") toast(result.message);
  },

  /**
   * The pack's booking button: the widget's own booking sheet for the agent this screen talks to,
   * its "What's it about?" starting with the pack's reason. The sheet checks the time and words any
   * refusal itself; a call booked there joins the list, which is then read again.
   */
  async book() {
    const agent = screenAgent(state.route);
    try {
      await state.identifying;
      // The booking goes to the agent on the page, so it must be this screen's.
      if (graine.agent !== agent) await graine.useAgent(agent);
      const call = await graine.openBooking({ reason: bookingReason(state.route) });
      if (!call) return;
      toast(`Call booked for ${L.formatWhen(call.at, new Date())}.`);
      state.calls.list = [...state.calls.list.filter((c) => c.id !== call.id), { ...call, agent }];
      render();
      refreshCalls();
    } catch (e) {
      quietly(e);
    }
  },

  async cancelCall(call) {
    const yes = await confirmDialog({
      title: "Cancel this call?",
      text: `${L.formatWhen(call.at, new Date())}${call.reason ? ` · ${call.reason}` : ""}. It will not ring.`,
      confirmLabel: "Cancel the call",
      cancelLabel: "Keep it",
    });
    if (!yes) return;
    state.busy.cancel = call.id;
    render();
    try {
      await state.identifying;
      await graine.cancelScheduledCallOf(call.agent || "main", call.id, state.user.id);
      toast("Call cancelled.");
    } catch (e) {
      toast(L.messageFor(e.code, e.message));
    }
    state.busy.cancel = null;
    await refreshCalls();
  },

  refreshCalls: () => refreshCalls(),
  refreshMemories: () => refreshMemories(),

  refreshPast: (agent) => refreshPast(agent),
  /** The next page: from Load more, Try again, or the end of the list coming into view. */
  loadMorePast: (agent) => refreshPast(agent, { more: true }),
  openTranscript: (agent, id) => openTranscript(agent, id),
  closeTranscript(agent) {
    const p = state.past[agent];
    p.transcript = null;
    p.opening = null;
    p.transcriptError = null;
    render({ focus: false });
  },
  async copySessionId(id) {
    toast((await copyText(id)) ? "Session id copied." : `Session id: ${id}`);
  },

  async setMemory(on) {
    const name = state.pack.agentName;
    state.memoryOn = !!on;
    saveSession();
    state.busy.memory = true;
    render();
    try {
      await state.identifying;
      await graine.setMemory(state.memoryOn);
      toast(state.memoryOn
        ? `${name} will remember your conversations, from the next one on.`
        : `${name} will not remember your conversations, from the next one on.`);
    } catch (e) {
      quietly(e);
    }
    state.busy.memory = false;
    render();
  },

  async erase() {
    const name = state.pack.agentName;
    const yes = await confirmDialog({
      title: `Erase everything ${name} remembers about you?`,
      text: `${name} will no longer recall anything from your earlier conversations. This cannot be undone.`,
      confirmLabel: "Erase",
      cancelLabel: "Keep",
    });
    if (!yes) return;
    state.busy.erase = true;
    render();
    try {
      await state.identifying;
      await graine.forgetMemory();
      state.memories = { list: [], loading: false, error: null, loaded: true };
      toast(`Everything ${name} remembered about you has been erased.`);
    } catch (e) {
      toast(L.messageFor(e.code, e.message));
    }
    state.busy.erase = false;
    render();
  },
};

// ── Boot ────────────────────────────────────────────────────────────────────

async function boot() {
  applyTheme(store.get(KEYS.theme));
  document.getElementById("theme-toggle").addEventListener("click", () => {
    setTheme({ system: "light", light: "dark", dark: "system" }[state.theme]);
  });

  state.config = await readConfig();
  const ids = Array.isArray(state.config.packs) && state.config.packs.length ? state.config.packs : PACK_IDS;
  const { packs, failures } = await loadPacks(ids);
  state.packs = packs;
  if (failures.length) {
    appNotice("packs", "Some use cases could not be loaded",
      packs.length ? "The others work. Check the files below with node scripts/check-usecases.mjs." : "", failures.map((f) => f.problem));
  }

  const fromUrl = new URL(window.location.href).searchParams.get("pack");
  state.pack = choosePack(packs, {
    fromUrl,
    remembered: store.get(KEYS.pack),
    fallback: typeof state.config.defaultPack === "string" ? state.config.defaultPack : DEFAULT_PACK_ID,
  });
  if (!state.pack) {
    document.getElementById("main").replaceChildren(V.noPacksView(failures));
    document.documentElement.dataset.ready = "1";
    return;
  }
  store.set(KEYS.pack, state.pack.id);
  applyPackChrome(state.pack);

  // Actions before start(): the first screen the assistant sees already lists them.
  registerActions(state.pack);
  graine.onState(() => render());
  state.agents = agentsFor(state.config, state.pack);
  graine.start({
    agents: { main: state.agents.main, help: state.agents.help },
    baseUrl: state.agents.baseUrl,
    loaderUrl: state.agents.loaderUrl,
  });

  // A website signs its visitor in again on every page load: the loader's state is per page.
  const saved = store.getJSON(KEYS.session);
  const user = saved && saved.packId === state.pack.id ? state.pack.users.find((u) => u.id === saved.userId) : null;
  if (user) {
    state.user = user;
    state.memoryOn = saved.memory !== false;
    resetPersonData();
    identifyCurrent();
  }

  window.addEventListener("hashchange", () => {
    const r = routeFromHash();
    if (r === state.route) return;
    state.route = r;
    if (state.user) enter(r);
    render({ focus: true });
  });
  state.route = routeFromHash();
  if (state.user) enter(state.route);
  render();
  document.documentElement.dataset.ready = "1";
}

boot().catch((e) => {
  // The last line of defence: say so on the page rather than leave it blank.
  const main = document.getElementById("main");
  if (main) main.replaceChildren(h("p", { class: "issue", role: "alert" }, "The demo could not start. The browser console has the details."));
  console.error(e);
});

#!/usr/bin/env node
/**
 * Loads the demo website in headless Chrome and checks it from the outside — no agent, no key,
 * and no request leaves this machine: Chrome resolves no host but 127.0.0.1, and every request to
 * anything else is failed before it is sent (and recorded).
 *
 *   blocked       config points at https://www.graine.ai; the loader request is blocked. The
 *                 adapter's notice shows, the site still works, nothing throws.
 *   unconfigured  no config.js: the "not configured" notice, and no loader is requested.
 *   released      a stand-in for the released loader (test/loader-1.1-stub.js): the "needs a newer
 *                 loader" notice names each missing method; actions, screen context and open() work.
 *   sdk-types     a stand-in with identity, memory, scheduling and the booking sheet in the SDK types
 *                 (test/fake-loader.js): the whole demo, end to end, at phone width and in dark mode.
 *   every pack    each use-case pack signs in and draws every screen.
 *
 *   node web/test/headless-check.mjs
 *   CHROME_PATH=…   another Chrome or Chromium
 *   SHOTS_DIR=…     also save screenshots there
 */
import { createServer } from "node:http";
import { mkdtempSync, rmSync, existsSync, statSync, readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, normalize, extname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const webDir = join(dirname(fileURLToPath(import.meta.url)), "..");
const demoDir = join(webDir, "..");
const CHROME = process.env.CHROME_PATH || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const SHOTS = process.env.SHOTS_DIR || "";
const PACKS = ["health-coach", "banking-support", "retail-shopping"];
const pack = (id) => JSON.parse(readFileSync(join(demoDir, "usecases", `${id}.json`), "utf8"));

const TYPES = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8", ".svg": "image/svg+xml", ".png": "image/png",
};

// ── Results ─────────────────────────────────────────────────────────────────

const results = [];
function check(ok, what, detail = "") {
  results.push({ ok: !!ok, what: `${current}: ${what}`, detail });
  console.log(`${ok ? "  ok  " : "  FAIL"} ${what}${!ok && detail ? ` — ${detail}` : ""}`);
}
let current = "";

// ── A static server for demo/, with config.js and the stand-in loaders swapped in ─

let configBody = null;   // what /web/config.js answers; null = 404, as when there is no config.js
const requests = [];

function serve() {
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    requests.push(`${req.method} ${url.pathname}`);
    const send = (status, type, body) => {
      res.writeHead(status, { "Content-Type": type, "Cache-Control": "no-store" });
      res.end(body);
    };
    try {
      if (url.pathname === "/web/config.js") {
        return configBody == null ? send(404, "text/plain", "no config.js") : send(200, TYPES[".js"], configBody);
      }
      if (url.pathname === "/stub/released/embed/v1.js") return send(200, TYPES[".js"], await readFile(join(webDir, "test", "loader-1.1-stub.js")));
      if (url.pathname === "/stub/sdk/embed/v1.js") return send(200, TYPES[".js"], await readFile(join(webDir, "test", "fake-loader.js")));
      if (url.pathname.startsWith("/api/")) return send(404, TYPES[".json"], JSON.stringify({ error: "Not here.", code: "not_found" }));
      const file = normalize(join(demoDir, decodeURIComponent(url.pathname)));
      if (file !== demoDir && !file.startsWith(demoDir + sep)) return send(403, "text/plain", "outside the demo");
      if (existsSync(file) && statSync(file).isDirectory()) {
        if (!url.pathname.endsWith("/")) { res.writeHead(301, { Location: `${url.pathname}/${url.search}` }); return res.end(); }
        const index = join(file, "index.html");
        return existsSync(index) ? send(200, TYPES[".html"], await readFile(index)) : send(404, "text/plain", "no index");
      }
      if (!existsSync(file)) return send(404, "text/plain", "not found");
      send(200, TYPES[extname(file)] || "application/octet-stream", await readFile(file));
    } catch (e) {
      send(500, "text/plain", String(e));
    }
  });
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server)));
}

// ── Chrome and the DevTools protocol ────────────────────────────────────────

function launch(profile) {
  const proc = spawn(CHROME, [
    "--headless=new", "--remote-debugging-port=0", `--user-data-dir=${profile}`,
    "--no-first-run", "--no-default-browser-check", "--disable-background-networking", "--disable-component-update",
    "--disable-sync", "--disable-extensions", "--disable-default-apps", "--metrics-recording-only", "--mute-audio",
    "--disable-features=Translate,OptimizationHints,MediaRouter,AutofillServerCommunication",
    // With the request interception below: no host but this machine resolves.
    "--host-resolver-rules=MAP * ~NOTFOUND , EXCLUDE 127.0.0.1",
    "--window-size=1280,900",
    "about:blank",
  ], { stdio: ["ignore", "ignore", "pipe"] });
  return new Promise((resolve, reject) => {
    let buf = "";
    const timer = setTimeout(() => reject(new Error("Chrome did not start in 20 s")), 20000);
    proc.stderr.on("data", (d) => {
      buf += d;
      const m = /DevTools listening on (ws:\/\/\S+)/.exec(buf);
      if (m) { clearTimeout(timer); resolve({ proc, ws: m[1] }); }
    });
    proc.on("exit", (code) => { clearTimeout(timer); reject(new Error(`Chrome exited (${code}): ${buf.slice(-400)}`)); });
  });
}

class Cdp {
  static connect(url) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      ws.addEventListener("open", () => resolve(new Cdp(ws)));
      ws.addEventListener("error", () => reject(new Error("Could not connect to Chrome")));
    });
  }
  constructor(ws) {
    this.ws = ws;
    this.next = 1;
    this.pending = new Map();
    this.listeners = [];
    ws.addEventListener("message", (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        if (msg.error) reject(new Error(msg.error.message)); else resolve(msg.result);
        return;
      }
      for (const l of this.listeners) if (l.method === msg.method && l.sessionId === msg.sessionId) l.fn(msg.params);
    });
  }
  send(method, params = {}, sessionId) {
    const id = this.next++;
    this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
  on(method, sessionId, fn) {
    this.listeners.push({ method, sessionId, fn });
  }
}

/** Page-side helpers, installed before the page's own scripts on every load. */
function helpers() {
  const norm = (s) => String(s == null ? "" : s).replace(/\s+/g, " ").trim();
  window.__t = {
    $: (s) => document.querySelector(s),
    text: (s) => { const e = document.querySelector(s); return e ? norm(e.textContent) : null; },
    texts: (s) => Array.from(document.querySelectorAll(s)).map((e) => norm(e.textContent)),
    byText: (sel, t) => Array.from(document.querySelectorAll(sel)).find((e) => norm(e.textContent) === t) || null,
    click: (target) => {
      const e = typeof target === "string" ? document.querySelector(target) : target;
      if (!e) throw new Error(`nothing to click: ${target}`);
      e.click();
      return true;
    },
    h1: () => norm((document.querySelector("main h1") || {}).textContent),
    toast: () => { const e = document.getElementById("toast"); return e && !e.hidden ? norm(e.textContent) : ""; },
    notice: (key) => { const e = document.querySelector(`[data-notice="${key}"]`); return e ? norm(e.textContent) : null; },
    /** The stand-in widget's booking sheet, when one is open (in the panel of `agent`, when named). */
    sheet: (agent) => document.querySelector(`[data-fake=panel]${agent ? `[data-agent="${agent}"]` : ""} [data-fake=booking]`),
    /** Duplicate ids, unnamed buttons, unlabelled fields — on the page, not in a stand-in's panel. */
    audit: () => {
      const mine = (e) => !e.closest("[data-fake], [data-stub]");
      const ids = Array.from(document.querySelectorAll("[id]")).filter(mine).map((e) => e.id);
      const dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
      const unnamed = Array.from(document.querySelectorAll("button")).filter(mine)
        .filter((b) => !norm(b.textContent) && !b.getAttribute("aria-label")).length;
      const unlabelled = Array.from(document.querySelectorAll("input, select, textarea")).filter(mine)
        .filter((e) => e.type !== "hidden" && !e.closest("label") && !e.getAttribute("aria-label")
          && !(e.id && document.querySelector(`label[for="${CSS.escape(e.id)}"]`)))
        .map((e) => e.id || e.name || e.tagName);
      return { dupes, unnamed, unlabelled };
    },
    wide: () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  };
}

async function openPage(cdp, origin) {
  const { targetId } = await cdp.send("Target.createTarget", { url: "about:blank" });
  const { sessionId } = await cdp.send("Target.attachToTarget", { targetId, flatten: true });
  const send = (method, params) => cdp.send(method, params, sessionId);
  const page = { exceptions: [], blocked: [], consoleErrors: [], send };

  cdp.on("Fetch.requestPaused", sessionId, ({ requestId, request }) => {
    if (request.url.startsWith(`${origin}/`)) send("Fetch.continueRequest", { requestId }).catch(() => {});
    else {
      page.blocked.push(request.url);
      send("Fetch.failRequest", { requestId, errorReason: "BlockedByClient" }).catch(() => {});
    }
  });
  cdp.on("Runtime.exceptionThrown", sessionId, ({ exceptionDetails: d }) => {
    page.exceptions.push(`${d.text} ${(d.exception && d.exception.description) || ""}`.trim());
  });
  cdp.on("Runtime.consoleAPICalled", sessionId, ({ type, args }) => {
    if (type === "error") page.consoleErrors.push(args.map((a) => a.value ?? a.description ?? "").join(" "));
  });

  await send("Fetch.enable", { patterns: [{ urlPattern: "*", requestStage: "Request" }] });
  await send("Runtime.enable");
  await send("Page.enable");
  await send("Page.addScriptToEvaluateOnNewDocument", { source: `(${helpers.toString()})();` });
  await send("Storage.clearDataForOrigin", { origin, storageTypes: "all" });
  await send("Emulation.setDeviceMetricsOverride", { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });

  page.eval = async (expression) => {
    const r = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true, userGesture: true });
    if (r.exceptionDetails) {
      throw new Error(`${(r.exceptionDetails.exception && r.exceptionDetails.exception.description) || r.exceptionDetails.text} in: ${expression.slice(0, 160)}`);
    }
    return r.result.value;
  };
  page.waitFor = async (expression, what, timeout = 8000) => {
    const end = Date.now() + timeout;
    let last;
    for (;;) {
      try { last = await page.eval(expression); if (last) return last; } catch (e) { last = e.message; }
      if (Date.now() > end) throw new Error(`timed out waiting for ${what} (last: ${JSON.stringify(last)})`);
      await new Promise((r) => setTimeout(r, 50));
    }
  };
  page.goto = async (url) => {
    await send("Page.navigate", { url });
    await page.waitFor(`document.readyState !== "loading" && document.documentElement.dataset.ready === "1"`, `${url} to be ready`, 15000);
  };
  page.shot = async (name) => {
    if (!SHOTS) return;
    mkdirSync(SHOTS, { recursive: true });
    const { data } = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
    writeFileSync(join(SHOTS, `${name}.png`), Buffer.from(data, "base64"));
  };
  page.close = () => cdp.send("Target.closeTarget", { targetId });
  return page;
}

const js = (v) => JSON.stringify(v);
const cfg = (o) => `export default ${JSON.stringify(o)};\n`;

async function signIn(page, userId, firstName) {
  await page.eval(`__t.click(${js(`#user-${userId}`)})`);
  await page.waitFor(`!__t.$("#signin-continue").disabled`, "Continue to be enabled");
  await page.eval(`__t.click("#signin-continue")`);
  await page.waitFor(`__t.h1() === ${js(`Hello, ${firstName}`)}`, `Home for ${firstName}`);
}

async function go(page, route, heading) {
  await page.eval(`location.hash = ${js(`#/${route}`)}`);
  await page.waitFor(`__t.h1() === ${js(heading)}`, `the ${route} screen ("${heading}")`);
}

// ── Scenarios ───────────────────────────────────────────────────────────────

async function blocked(page, origin) {
  configBody = cfg({ agentId: "agent_headless", publishableKey: "pk_test_headless", baseUrl: "https://www.graine.ai" });
  await page.goto(`${origin}/web/`);
  await page.waitFor(`__t.notice("not_loaded")`, "the not-loaded notice");
  check(/did not load from https:\/\/www\.graine\.ai\/embed\/v1\.js/.test(await page.eval(`__t.notice("not_loaded")`)), "the notice names the loader URL");
  check(page.blocked.some((u) => u.startsWith("https://www.graine.ai/embed/v1.js")), "the loader request was stopped before it left the machine");
  check(page.blocked.every((u) => new URL(u).host === "www.graine.ai"), "nothing else tried to leave", page.blocked.join(", "));
  check(/Did not load/.test(await page.eval(`__t.text("#assistant-status")`)), "the header says the assistant did not load");
  await page.shot("blocked-sign-in");

  await signIn(page, "MRN-000981", "Riya");
  await page.eval(`__t.click("#talk")`);
  await page.waitFor(`/did not load/.test(__t.toast())`, "a toast saying why Talk does nothing");
  await go(page, "items", "Medicines");
  check((await page.eval(`__t.texts(".item-title")`)).join("|") === "Metformin 500 mg|Atorvastatin 10 mg", "the list works without the assistant");
  await page.eval(`__t.click(__t.byText('#row-metformin button', 'Mark as taken'))`);
  await page.waitFor(`__t.text('#row-metformin .chip') === "Taken today"`, "the status chip to change");
  await go(page, "memory", "Memory");
  check(/did not load/.test(await page.eval(`__t.text(".issue")`)), "Memory says why it is unavailable");
  await go(page, "calls", "Check-in calls");
  check(await page.eval(`__t.$("#book-open").disabled && !!__t.text(".issue")`), "booking is disabled, with the reason shown");
  await go(page, "profile", "Profile");
  await page.eval(`__t.click("#sign-out")`);
  await page.waitFor(`__t.h1() === "Sign in"`, "the sign-in screen after signing out");
  check(page.exceptions.length === 0, "no uncaught errors", page.exceptions.join(" | "));
}

async function unconfigured(page, origin) {
  configBody = null;
  const before = requests.length;
  await page.goto(`${origin}/web/`);
  await page.waitFor(`__t.notice("not_configured")`, "the not-configured notice");
  check(!requests.slice(before).some((r) => r.includes("/embed/v1.js")), "no loader was requested");
  check(page.blocked.length === 0, "nothing tried to leave the machine", page.blocked.join(", "));
  check(/Not configured/.test(await page.eval(`__t.text("#assistant-status")`)), "the header says not configured");
  check((await page.eval(`__t.texts(".person-name")`)).length === 3, "the sign-in screen lists the pack's three people");
  check((await page.eval(`__t.$("#pack-picker").options.length`)) === 3, "the use-case picker offers three packs");
  check(page.exceptions.length === 0, "no uncaught errors", page.exceptions.join(" | "));
}

async function released(page, origin) {
  configBody = cfg({ agentId: "agent_headless", publishableKey: "pk_test_headless", baseUrl: `${origin}/stub/released`,
    help: { agentId: "agent_help", publishableKey: "pk_test_help" } });
  const health = pack("health-coach");
  await page.goto(`${origin}/web/`);
  await page.waitFor(`/Ready/.test(__t.text("#assistant-status"))`, "the assistant to start");
  check((await page.eval(`__graineStub.actionNames().sort().join(",")`)) === health.actions.map((a) => a.name).sort().join(","),
    "every pack action is registered by name");
  check((await page.eval(`__graineStub.screens[0] && __graineStub.screens[0].screen`)) === "sign_in", "the first screen reported is sign_in");

  await signIn(page, "MRN-000981", "Riya");
  await page.waitFor(`__t.notice("unsupported")`, "the needs-a-newer-loader notice");
  let n = await page.eval(`__t.notice("unsupported")`);
  check(/version 1\.1\.0/.test(n) && /GraineAgent\.identify\(\)/.test(n), "the notice names identify() and the loader version", n);
  await go(page, "memory", "Memory");
  await page.waitFor(`/GraineAgent\\.memories\\(\\)/.test(__t.notice("unsupported"))`, "memories() in the notice");
  check(/does not have memories\(\)/.test(await page.eval(`__t.text(".issue")`)), "the Memory screen says what is missing");
  await go(page, "calls", "Check-in calls");
  await page.waitFor(`/GraineAgent\\.scheduledCalls\\(\\)/.test(__t.notice("unsupported"))`, "scheduledCalls() in the notice");
  n = await page.eval(`__t.notice("unsupported")`);
  check(/GraineAgent\.openBooking\(\) —/.test(n) && /GraineAgent\.bookingAvailable —/.test(n),
    "openBooking() and the bookingAvailable property are named", n);
  check(await page.eval(`__t.$("#book-open").disabled && /does not have openBooking\\(\\)/.test(__t.text(".issue"))`),
    "booking is disabled on a loader without the booking sheet, and says why");
  await page.shot("released-calls-notice");

  await page.eval(`__t.click("#memory-chip") || true`).catch(() => {});
  await go(page, "home", "Hello, Riya");
  await page.eval(`__t.click("#talk")`);
  await page.waitFor(`__graineStub.opened() === 1`, "Talk to open the assistant");
  const r = await page.eval(`__graineStub.runAction("open_screen", { screen: "medicines" })`);
  check(r && r.status === "ok" && r.message === "Opened Medicines.", "the agent's open_screen works on the released loader", js(r));
  await page.waitFor(`__t.h1() === "Medicines"`, "Medicines");
  const m = await page.eval(`__graineStub.runAction("mark_done", { item: "metformin" })`);
  check(m && m.message === "Metformin 500 mg: Taken today.", "mark_done answers in the pack's words", js(m));
  await page.waitFor(`__graineStub.screens.at(-1).fields[0].value === "Taken today"`, "the screen context to follow");
  const ctx = await page.eval(`__graineStub.screens.at(-1)`);
  check(ctx.screen === "medicines" && ctx.title === "Medicines" && ctx.fields.length === 2 && ctx.fields[0].name === "metformin"
    && ctx.fields[0].status === "filled", "the list's screen context has one filled field per row", js(ctx));
  await go(page, "help", "Billing & appointments help");
  await page.waitFor(`/GraineAgent\\.load\\(\\) with another agent/.test(__t.notice("unsupported"))`, "the switch named in the notice");
  check(/cannot switch to Billing & appointments help/.test(await page.eval(`__t.text(".issue")`)), "Help says the loader cannot switch");
  check(await page.eval(`__t.$("#help-talk").disabled && __t.$("#book-open").disabled`), "Help's talk and booking are off rather than reaching the main agent");
  check((await page.eval(`__graineStub.loads.length`)) === 1, "no second assistant was mounted");
  check(page.blocked.length === 0, "nothing tried to leave the machine", page.blocked.join(", "));
  check(page.exceptions.length === 0, "no uncaught errors", page.exceptions.join(" | "));
}

async function sdkTypes(page, origin) {
  configBody = cfg({ agentId: "agent_headless", publishableKey: "pk_test_headless", baseUrl: `${origin}/stub/sdk`,
    help: { agentId: "agent_help", publishableKey: "pk_test_help" } });
  await page.goto(`${origin}/web/`);
  await page.waitFor(`/Ready/.test(__t.text("#assistant-status"))`, "the assistant to start");
  check((await page.eval(`document.querySelectorAll("#graine-notices .notice").length`)) === 0, "no notices with a loader that has everything");
  await page.eval(`__graineFake.seedMemories("MRN-000981", ["Takes metformin at 8 pm, after an evening walk.", "Feels dizzy when skipping breakfast."]);
    __graineFake.seedMemories("MRN-000982", ["Prefers calls in the morning."]); true`);
  await page.shot("sdk-sign-in");

  // Sign in: identify with the traits, the name, the pack's identifier field, the same id under user_id, and the consent.
  await signIn(page, "MRN-000981", "Riya");
  const id = await page.eval(`__graineFake.log.filter((c) => c[0] === "identify").at(-1)`);
  check(js(id) === js(["identify", { plan: "Diabetes care", care_team: "Dr. Kavya Rao", age: "34", name: "Riya Sharma", patient_id: "MRN-000981", user_id: "MRN-000981" }, { memory: true }]),
    "identify gets the traits, name, patient_id, user_id and memory: true", js(id));
  check((await page.eval(`__t.text("#memory-chip")`)) === "Memory: on", "Home shows Memory: on");
  check((await page.eval(`__t.texts("[data-card] .card-title").join("|")`)) === "Medicines due today|Check-in calls|What Asha remembers", "Home draws the pack's cards");
  const home = await page.eval(`__graineFake.screens.at(-1)`);
  check(home.screen === "home" && home.title === "Today" && js(home.fields.map((f) => [f.name, f.value])) === js([["memory", "on"], ["next_call", "none"]]),
    "the home screen context: memory and next_call", js(home));
  await page.shot("sdk-home");

  // The agent's actions, answered in the pack's words.
  const run = (name, args) => page.eval(`__graineFake.runAction(${js(name)}, ${js(args)})`);
  let r = await run("open_screen", { screen: "medicines" });
  check(r.status === "ok" && r.message === "Opened Medicines.", "open_screen", js(r));
  await page.waitFor(`__t.h1() === "Medicines"`, "Medicines");
  r = await run("mark_done", { item: "metformin" });
  check(r.message === "Metformin 500 mg: Taken today.", "mark_done", js(r));
  await page.waitFor(`__t.text("#row-metformin .chip") === "Taken today"`, "the chip to change");
  check(!(await page.eval(`!!__t.byText("#row-metformin button", "Mark as taken")`)), "the button hides while the row has that status");
  check(await page.eval(`!!__t.byText("#row-atorvastatin button", "Mark as taken")`), "the other row keeps its button");
  r = await run("mark_done", { item: "metformin" });
  check(r.status === "ok" && r.message === "Metformin 500 mg is already Taken today.", "mark_done again says already", js(r));
  r = await run("mark_done", { item: "amlodipine" });
  check(r.status === "refused" && r.message === 'No medicine called "amlodipine". Medicines: Metformin 500 mg, Atorvastatin 10 mg.',
    "another patient's medicine is not there", js(r));
  r = await run("open_item", { item: "Atorvastatin" });
  check(r.message === "Showing Atorvastatin 10 mg.", "open_item", js(r));
  await page.waitFor(`__t.$("#row-atorvastatin").classList.contains("is-open") && /Next dose/.test(__t.text("#row-atorvastatin"))`, "the row to open");
  await page.shot("sdk-items");
  r = await run("open_screen", { screen: "settings" });
  check(r.status === "refused" && r.message === 'There is no screen called "settings". Screens: home, chat, medicines, calls, memory, profile.', "an unknown screen is refused", js(r));

  // Scheduled calls: the pack's button opens the widget's own booking sheet; statuses; cancel.
  r = await run("open_screen", { screen: "calls" });
  await page.waitFor(`__t.h1() === "Check-in calls"`, "Check-in calls");
  check(/rings the Graine Care app on the phone/.test(await page.eval(`__t.text("#rings-phone")`)), "the page says the call rings the phone app");
  check((await page.eval(`__t.text("#book-open")`)) === "Schedule a consultation call", "the booking button is the pack's bookLabel");
  await page.waitFor(`!__t.$("#book-open").disabled`, "the booking button on while booking is available");
  check(!(await page.eval(`!!__t.$("#book-unavailable")`)), "no unavailable line while booking is available");
  await page.eval(`__t.click("#book-open")`);
  await page.waitFor(`!!__t.sheet("agent_headless")`, "the widget's booking sheet");
  check(js(await page.eval(`__graineFake.log.filter((c) => c[0] === "openBooking").at(-1)`)) === js(["openBooking", { reason: "Consultation" }, "agent_headless"]),
    "the sheet opens for the main agent with the pack's reason");
  check((await page.eval(`__t.$("[data-fake=booking-reason]").value`)) === "Consultation", "\"What's it about?\" starts with the pack's reason");
  await page.shot("sdk-booking-sheet");
  await page.eval(`__t.click("[data-fake=booking-book]")`);
  await page.waitFor(`__t.texts(".list-row [data-status]").includes("Scheduled") && !__t.sheet()`, "the booked call in the list, the sheet closed");
  check(/^Call booked for /.test(await page.eval(`__t.toast()`)), "a toast confirms the booking");
  const listed = await page.eval(`__t.texts(".list-row").length`);
  await page.eval(`__t.click("#book-open")`);
  await page.waitFor(`!!__t.sheet()`, "the sheet again");
  await page.eval(`__t.click("[data-fake=booking-close]")`);
  await page.waitFor(`!__t.sheet()`, "the sheet closed");
  check((await page.eval(`__t.texts(".list-row").length`)) === listed, "closing the sheet books nothing");
  // While the loader says this visitor cannot book, the button stays, disabled, and says so.
  await page.eval(`__graineFake.setUsersCanBook(false); true`);
  await page.eval(`__t.click("#calls-refresh")`);
  await page.waitFor(`__t.$("#book-open").disabled && __t.text("#book-unavailable") === "Booking needs, in Graine UI: users may book (Calls & scheduling) and Who is the user? set to this pack's ID."`,
    "the button off, with one line saying why");
  await page.shot("sdk-booking-unavailable");
  await page.eval(`__graineFake.setUsersCanBook(true); true`);
  await page.eval(`__t.click("#calls-refresh")`);
  await page.waitFor(`!__t.$("#book-open").disabled && !__t.$("#book-unavailable")`, "the button on again");
  await page.eval(`__graineFake.seedCalls("MRN-000981", [
    { at: new Date(Date.now() - 864e5).toISOString(), status: "missed", source: "agent", reason: "Weekly check-in" },
    { at: new Date(Date.now() - 2 * 864e5).toISOString(), status: "completed", source: "dashboard" },
    { at: new Date(Date.now() - 3 * 864e5).toISOString(), status: "no_device", source: "sdk" }]); true`);
  await page.eval(`__t.click("#calls-refresh")`);
  await page.waitFor(`["Missed", "Completed", "No device"].every((s) => __t.texts(".list-row [data-status]").includes(s))`, "the earlier calls' chips");
  check((await page.eval(`__t.texts(".list-sub")`)).includes("Booked by Asha"), "who booked it, in words");
  await page.shot("sdk-calls");
  await page.eval(`__t.click(__t.byText(".list-row button", "Cancel"))`);
  await page.waitFor(`__t.$("#confirm").open`, "the in-page confirmation");
  await page.eval(`__t.click("#confirm-yes")`);
  await page.waitFor(`__t.texts(".list-row [data-status]").includes("Cancelled")`, "the cancelled chip");
  await page.waitFor(`__graineFake.screens.at(-1).fields[0].value === "0"`, "the calls screen context to count no upcoming calls");

  // Help: the second agent takes the page's assistant; the same person stays signed in.
  await go(page, "help", "Billing & appointments help");
  await page.waitFor(`__t.text("#assistant-status") === "Billing & appointments help: Ready"`, "the help agent on the page");
  check((await page.eval(`__graineFake.currentAgent()`)) === "agent_help", "the page's assistant is the help agent");
  check((await page.eval(`__graineFake.widgets()`)) === 1, "one assistant on the page, never two");
  check((await page.eval(`__graineFake.identity()`)) === "MRN-000981", "the same person is still signed in");
  await page.waitFor(`__graineFake.screens.at(-1).screen === "help"`, "the help screen context");
  check(js(await page.eval(`__graineFake.screens.at(-1)`)) === js({ screen: "help", title: "Billing & appointments help", fields: [] }), "the help screen context");
  await page.eval(`__t.click("#help-talk")`);
  await page.waitFor(`__graineFake.opened() >= 1 && !!document.querySelector('[data-fake=panel][data-agent="agent_help"]')`, "Talk opens the help agent");
  await page.waitFor(`!__t.$("#book-open").disabled`, "booking with the help agent");
  check((await page.eval(`__t.text("#book-open")`)) === "Book a call with billing", "Help's booking button is the help agent's bookLabel");
  check(/as “Graine Care · Billing”/.test(await page.eval(`__t.text("#rings-phone")`)), "the help agent's call name");
  await page.eval(`__t.click("#book-open")`);
  await page.waitFor(`!!__t.sheet("agent_help")`, "the help agent's booking sheet");
  check(js(await page.eval(`__graineFake.log.filter((c) => c[0] === "openBooking").at(-1)`)) === js(["openBooking", { reason: "Billing question" }, "agent_help"]),
    "Help's sheet opens for the help agent with its own reason");
  await page.eval(`__t.click("[data-fake=booking-book]")`);
  await page.waitFor(`!__t.sheet() && __t.texts(".list-row [data-status]").includes("Scheduled")`, "the help booking in Help's list");
  const helpBooked = await page.eval(`__graineFake.log.filter((c) => c[0] === "booked").at(-1)`);
  check(helpBooked[2] === "agent_help" && helpBooked[1].reason === "Billing question", "booked with the help agent, with its reason", js(helpBooked));
  check((await page.eval(`document.querySelectorAll(".list-agent").length`)) === 0, "Help's own list does not repeat the agent's name");
  await page.shot("sdk-help");

  // Back to Scheduled calls: the main agent again, and both agents' calls, each named.
  await go(page, "calls", "Check-in calls");
  await page.waitFor(`__t.text("#assistant-status") === "Asha: Ready" && __graineFake.currentAgent() === "agent_headless"`, "the main agent back on the page");
  check((await page.eval(`__graineFake.widgets()`)) === 1, "still one assistant on the page");
  await page.waitFor(`!!document.querySelector('.list-row[data-agent="help"]')`, "the help agent's call in the list");
  check((await page.eval(`__t.text('.list-row[data-agent="help"] .list-agent')`)) === "With Billing & appointments help", "the help agent's call is named");
  check((await page.eval(`__t.texts('.list-row[data-agent="main"] .list-agent')`)).every((t) => t === "With Asha"), "the main agent's calls are named");
  const route = await page.eval(`__graineFake.log.filter((c) => c[0] === "route" && c[1] === "/api/embed/calls/list").at(-1)`);
  check(route && route[2].publishableKey === "pk_test_help" && route[2].identity.value === "MRN-000981" && route[2].installId === "6f1d2c3b-4a59-4e7f-9a1b-0c2d3e4f5a6b",
    "the help agent's calls are read with its key, this browser's install id and the identifier", js(route));
  await page.eval(`__t.click(__t.$('.list-row[data-agent="help"] button'))`);
  await page.waitFor(`__t.$("#confirm").open`, "the cancel confirmation");
  await page.eval(`__t.click("#confirm-yes")`);
  await page.waitFor(`__t.text('.list-row[data-agent="help"] [data-status]') === "Cancelled"`, "the help agent's call cancelled");
  check(!!(await page.eval(`__graineFake.log.find((c) => c[0] === "route" && c[1] === "/api/embed/calls/cancel" && c[2].publishableKey === "pk_test_help")`)),
    "cancelled with the help agent's key");
  await page.shot("sdk-calls-two-agents");

  // Memory: the list, the switch, erase.
  await go(page, "memory", "Memory");
  await page.waitFor(`__t.texts(".memory-text").length === 2`, "Riya's memories");
  check((await page.eval(`__t.texts(".memory-text")[0]`)) === "Takes metformin at 8 pm, after an evening walk.", "newest first");
  const mctx = await page.eval(`__graineFake.screens.at(-1)`);
  check(js(mctx.fields.map((f) => [f.name, f.value])) === js([["memory", "on"], ["remembered", "2"]]) && !js(mctx).includes("metformin"),
    "the memory screen context counts, never quotes", js(mctx));
  await page.eval(`__t.click("#memory-switch")`);
  await page.waitFor(`__graineFake.log.some((c) => c[0] === "setMemory" && c[1] === false)`, "setMemory(false)");
  await page.eval(`__t.click("#erase-memory")`);
  await page.waitFor(`__t.$("#confirm").open`, "the erase confirmation");
  check(/Erase everything Asha remembers about you\?/.test(await page.eval(`__t.text("#confirm-title")`)), "the confirmation names the agent");
  await page.eval(`__t.click("#confirm-yes")`);
  await page.waitFor(`!!__t.$("#memory-empty") && /Nothing yet/.test(__t.text("#memory-empty"))`, "the empty memory list");
  await page.shot("sdk-memory");

  // Profile, sign out, another person: memories never cross.
  await go(page, "profile", "Profile");
  check(/6f1d2c3b-4a59-4e7f-9a1b-0c2d3e4f5a6b/.test(await page.eval(`__t.text(".facts")+" "+document.body.textContent`)), "Profile shows the install id");
  await page.shot("sdk-profile");
  await page.eval(`__t.click("#sign-out")`);
  await page.waitFor(`__t.h1() === "Sign in"`, "sign-in after signing out");
  check(js(await page.eval(`__graineFake.log.filter((c) => c[0] === "identify").at(-1).slice(0, 2)`)) === js(["identify", null]), "sign-out is identify(null)");
  await signIn(page, "MRN-000982", "Arjun");
  await go(page, "memory", "Memory");
  await page.waitFor(`__t.texts(".memory-text").join("|") === "Prefers calls in the morning."`, "Arjun's own memories only");
  await go(page, "profile", "Profile");
  await page.eval(`__t.click("#sign-out")`);
  await page.waitFor(`__t.h1() === "Sign in"`, "sign-in");
  await page.eval(`__t.click("#user-MRN-000981")`);
  await page.waitFor(`__t.$("#signin-memory") && __t.$("#signin-memory").checked === false`, "Riya's earlier choice (memory off) remembered");
  await page.eval(`__t.click("#signin-continue")`);
  await page.waitFor(`__t.h1() === "Hello, Riya"`, "Home");
  check(js(await page.eval(`__graineFake.log.filter((c) => c[0] === "identify").at(-1)[2]`)) === js({ memory: false }), "identify carries memory: false");
  check((await page.eval(`__t.text("#memory-chip")`)) === "Memory: off", "Home shows Memory: off");

  // Phone width, every screen; dark mode.
  for (const width of [375, 320]) {
    await page.send("Emulation.setDeviceMetricsOverride", { width, height: 800, deviceScaleFactor: 2, mobile: true });
    for (const [route, heading] of [["home", "Hello, Riya"], ["chat", "Asha"], ["items", "Medicines"], ["calls", "Check-in calls"], ["memory", "Memory"], ["profile", "Profile"], ["help", "Billing & appointments help"]]) {
      await go(page, route, heading);
      const over = await page.eval(`__t.wide()`);
      check(over <= 0, `${width}px: ${route} has no sideways scroll`, `${over}px too wide`);
      const audit = await page.eval(`__t.audit()`);
      if (width === 375) check(!audit.dupes.length && !audit.unnamed && !audit.unlabelled.length, `${route}: ids unique, buttons named, fields labelled`, js(audit));
      if (width === 375 && (route === "home" || route === "calls")) await page.shot(`sdk-phone-${route}`);
    }
  }
  await page.send("Emulation.setDeviceMetricsOverride", { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });
  await page.send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: "dark" }] });
  await go(page, "home", "Hello, Riya");
  check((await page.eval(`getComputedStyle(document.body).backgroundColor`)) === "rgb(14, 16, 20)", "dark mode follows the device");
  await page.shot("sdk-home-dark");
  await go(page, "calls", "Check-in calls");
  await page.shot("sdk-calls-dark");
  await page.send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: "light" }] });
  check((await page.eval(`getComputedStyle(document.body).backgroundColor`)) === "rgb(245, 246, 248)", "light mode");
  await page.shot("sdk-calls-light");
  await go(page, "home", "Hello, Riya");
  await page.shot("sdk-home-light");

  // Switching use case: signed out first, then a new page with the other pack.
  await go(page, "profile", "Profile");
  await page.eval(`__t.click("#sign-out")`);
  await page.waitFor(`__t.h1() === "Sign in"`, "sign-in");
  await page.eval(`const s = __t.$("#pack-picker"); s.value = "banking-support"; s.dispatchEvent(new Event("change", { bubbles: true })); true`);
  await page.waitFor(`__t.text("#app-title") === "Graine Bank" && document.documentElement.dataset.ready === "1"`, "the banking pack", 15000);
  check((await page.eval(`__t.texts(".person-name").join("|")`)) === "Priya Nair|Rahul Verma|Sana Qureshi", "the banking pack's people");
  check(page.blocked.length === 0, "nothing tried to leave the machine", page.blocked.join(", "));
  check(page.exceptions.length === 0, "no uncaught errors", page.exceptions.join(" | "));
}

async function everyPack(page, origin) {
  configBody = cfg({ agentId: "agent_headless", publishableKey: "pk_test_headless", baseUrl: `${origin}/stub/sdk` });
  for (const id of PACKS) {
    const p = pack(id);
    const user = p.users[0];
    const first = user.name.split(/\s+/)[0];
    await page.goto(`${origin}/web/?pack=${id}`);
    check((await page.eval(`__t.text("#app-title")`)) === p.appTitle, `${id}: the app bar says ${p.appTitle}`);
    await page.waitFor(`/Ready/.test(__t.text("#assistant-status"))`, "the assistant to start");
    await signIn(page, user.id, first);
    for (const [route, heading] of [["chat", p.agentName], ["items", p.items.title], ["calls", p.call.title], ["memory", "Memory"], ["profile", "Profile"], ["home", `Hello, ${first}`]]) {
      await go(page, route, heading);
    }
    await go(page, "items", p.items.title);
    const rows = p.items.rows.filter((r) => !r.userIds || r.userIds.includes(user.id)).length;
    check((await page.eval(`document.querySelectorAll(".item").length`)) === rows, `${id}: ${first}'s ${rows} rows`);
    const button = await page.eval(`(() => { const b = document.querySelector(".item-actions [data-action]"); return b ? { row: b.closest(".item").dataset.row, label: b.textContent.trim(), action: b.dataset.action } : null; })()`);
    if (button) {
      const status = p.actions.find((a) => a.name === button.action).status;
      await page.eval(`__t.click(".item-actions [data-action]")`);
      await page.waitFor(`__t.text(${js(`#row-${button.row} .chip`)}) === ${js(status)}`, `${id}: "${button.label}" sets ${status}`);
      check(true, `${id}: "${button.label}" sets the row to ${status}`);
    }
    await go(page, "calls", p.call.title);
    check((await page.eval(`__t.text("#book-open")`)) === p.call.bookLabel, `${id}: the booking button says "${p.call.bookLabel}"`);
    await page.waitFor(`!__t.$("#book-open").disabled`, `${id}: booking available`);
    await page.eval(`__t.click("#book-open")`);
    await page.waitFor(`!!__t.sheet()`, `${id}: the booking sheet`);
    check((await page.eval(`__graineFake.log.filter((c) => c[0] === "openBooking").at(-1)[1].reason`)) === p.call.reasons[0],
      `${id}: the sheet starts with the first reason, "${p.call.reasons[0]}"`);
    await page.eval(`__t.click("[data-fake=booking-book]")`);
    await page.waitFor(`__t.texts(".list-row [data-status]").includes("Scheduled")`, `${id}: the booking in the list`);
    await go(page, "help", p.agents.help.name);
    check(/Add /.test(await page.eval(`__t.text("#help-setup .card-title")`)) && /help: \{ agentId/.test(await page.eval(`__t.text("#help-setup pre")`)),
      `${id}: without a help agent, Help says how to add one`);
    check((await page.eval(`__graineFake.loads.length`)) === 1 && (await page.eval(`__graineFake.currentAgent()`)) === "agent_headless",
      `${id}: the page stays on the main agent`);
    await go(page, "profile", "Profile");
    await page.eval(`__t.click("#sign-out")`);
    await page.waitFor(`__t.h1() === "Sign in"`, "sign-in");
  }
  check(page.blocked.length === 0, "nothing tried to leave the machine", page.blocked.join(", "));
  check(page.exceptions.length === 0, "no uncaught errors", page.exceptions.join(" | "));
}

// ── Run ─────────────────────────────────────────────────────────────────────

async function main() {
  if (!existsSync(CHROME)) {
    console.log(`Chrome not found at ${CHROME}; set CHROME_PATH. Skipped.`);
    process.exit(2);
  }
  const server = await serve();
  const origin = `http://127.0.0.1:${server.address().port}`;
  const profile = mkdtempSync(join(process.env.HEADLESS_TMP || tmpdir(), "graine-demo-headless-"));
  let chrome;
  try {
    chrome = await launch(profile);
    const cdp = await Cdp.connect(chrome.ws);
    for (const [name, scenario] of [["blocked", blocked], ["unconfigured", unconfigured], ["released", released], ["sdk-types", sdkTypes], ["every pack", everyPack]]) {
      current = name;
      console.log(`\n${name}`);
      const page = await openPage(cdp, origin);
      try {
        await scenario(page, origin);
      } catch (e) {
        check(false, "the scenario ran to the end", e.message);
        await page.shot(`${name.replace(/\s+/g, "-")}-failed`).catch(() => {});
      }
      await page.close().catch(() => {});
    }
  } finally {
    if (chrome) chrome.proc.kill("SIGTERM");
    server.close();
    await new Promise((r) => setTimeout(r, 300));
    try { rmSync(profile, { recursive: true, force: true }); } catch (e) { /* Chrome may still hold it */ }
  }
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  for (const f of failed) console.log(`FAIL ${f.what}${f.detail ? ` — ${f.detail}` : ""}`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

#!/usr/bin/env node
/**
 * THE OFFLINE DEMO — a stand-in for Graine on this machine, so the demo apps draw every screen with no
 * workspace and no publishable key: for screenshots, for a demo with no network, for trying the apps
 * before the agent exists.
 *
 *   node scripts/offline-server.mjs                 # http://127.0.0.1:8790, the health-coach pack
 *   PORT=8791 PACK=banking-support node scripts/offline-server.mjs
 *
 * Then start an app against it (iOS: see ios/README.md, "Offline demo"). It answers what the SDKs ask
 * Graine (WIRE.md): the agent's configuration, booked calls, memories, devices, the chat — played by a
 * script in the pack's words, with the pack's components from agent-setup/ — and a voice bar that
 * connects and speaks one line. Nothing is real: no agent, no model, no audio, no ringing. The
 * typefaces come from the public font CDN the dashboard uses; without internet the apps draw in
 * the system font.
 *
 * Node 18+, no dependencies.
 */
import { createServer } from "node:http";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const PORT = Number(process.env.PORT || 8790);
const PACK = process.env.PACK || "health-coach";
const ORIGIN = `http://127.0.0.1:${PORT}`;

const read = (path) => JSON.parse(readFileSync(join(root, path), "utf8"));
const pack = read(`usecases/${PACK}.json`);
const components = read(`agent-setup/${PACK}/components.json`);
const actions = read(`agent-setup/${PACK}/actions.json`);
// The design the dashboard resolves for this pack's look — Signature in the pack's accent, its starters, its
// agent's name — as the session would carry it: the dashboard's resolveDesign(designFromAppearance(…)), kept here.
const design = read(`scripts/offline/${PACK}.design.json`);
const logo = readFileSync(join(root, "ios/GraineDemo/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png"));

// The stand-in keys the apps use in offline mode, one per agent the pack has.
const AGENTS = {
  pk_offline_demo: { id: `offline-${PACK}-main`, name: pack.agentName, callName: pack.call.callName, starters: pack.starters },
  pk_offline_help: pack.agents?.help
    ? { id: `offline-${PACK}-help`, name: pack.agents.help.name, callName: pack.agents.help.callName, starters: pack.agents.help.starters }
    : null,
};

// ── State, for the life of the server ────────────────────────────────────────

const now = () => Date.now();
const iso = (ms) => new Date(ms).toISOString();
const atHour = (daysFromToday, hour, minute = 0) => {
  const d = new Date();
  d.setDate(d.getDate() + daysFromToday);
  d.setHours(hour, minute, 0, 0);
  return d.getTime();
};
// A few calls in every state the apps draw, so the list is never empty.
const calls = [
  { id: "call_offline_1", at: iso(atHour(1, 18)), status: "scheduled", source: "agent", reason: pack.call.reasons[0] },
  { id: "call_offline_2", at: iso(atHour(-1, 10)), status: "answered", source: "sdk", reason: pack.call.reasons[1] ?? null },
  { id: "call_offline_3", at: iso(atHour(-2, 19, 30)), status: "missed", source: "agent", reason: null },
  { id: "call_offline_4", at: iso(atHour(-4, 9)), status: "no_device", source: "dashboard", reason: pack.call.reasons[2] ?? null },
];
const helpCalls = [];
// What the agent remembers about whoever is signed in — the pack's memory examples, as the agent keeps them.
const REMEMBERED = {
  "health-coach": ["Takes metformin at 8 pm, after an evening walk", "Feels dizzy when breakfast is skipped", "Prefers check-in calls after 6 pm"],
  "banking-support": ["Travels to Singapore every March for work", "Wants calls only after 6 pm on weekdays", "Uses the card mostly for travel bookings"],
  "retail-shopping": ["Wears size 7 in running shoes", "Wants parcels left with the building security desk", "Prefers delivery updates by call"],
};
let memories = (REMEMBERED[PACK] || []).map((text, i) => {
  const at = iso(atHour(-(i + 1), 18 - i * 2, 10 + i * 7));
  return { id: `mem_${i + 1}`, text, createdAt: at, updatedAt: at };
});

// ── HTTP ─────────────────────────────────────────────────────────────────────

function session(agent) {
  return {
    agentId: agent.id,
    webcallAgentId: agent.id,
    variables: {},
    appearance: {
      title: agent.name, mode: "both", start: "voice", position: "bottom-right",
      accent: pack.accent, branding: false, logo: "offline/logo.png", audioTransport: "ws",
      // The design as the dashboard stores it; `design` below is the same, resolved into tokens.
      design: design.config,
    },
    design,
    components: components.map(({ slug, toolName, name, kind, data, fallbackText }) => ({ slug, toolName, name, kind, data, fallbackText })),
    appActions: actions,
    friction: {},
    identity: { field: pack.identifierField, devicesPerUser: 2 },
    memory: { enabled: true },
    calls: {
      hosted: true, platforms: ["ios", "android"], ringTimeoutSeconds: 45, callName: agent.callName || agent.name,
      scheduling: { usersCanBook: true, maxDaysAhead: 14, minLeadMinutes: 5, maxPendingPerUser: 3 },
    },
    wsHost: `ws://127.0.0.1:${PORT}`,
    pageVersion: "offline",
  };
}

// The faces the offline design uses, from the same public files the dashboard's /api/embed/font serves.
const FONT_BASE = {
  "DM Sans": ["https://cdn.jsdelivr.net/npm/@expo-google-fonts/dm-sans@0.4.2", "DMSans"],
  Newsreader: ["https://cdn.jsdelivr.net/npm/@expo-google-fonts/newsreader@0.4.1", "Newsreader"],
};
const WEIGHT_NAMES = { 200: "ExtraLight", 300: "Light", 400: "Regular", 500: "Medium", 600: "SemiBold", 700: "Bold", 800: "ExtraBold" };
const fontCache = new Map();

async function font(family, weight) {
  const base = FONT_BASE[family];
  const name = WEIGHT_NAMES[weight];
  if (!base || !name) return null;
  const url = `${base[0]}/${weight}${name}/${base[1]}_${weight}${name}.ttf`;
  if (!fontCache.has(url)) {
    const res = await fetch(url).catch(() => null);
    if (!res || !res.ok) return null;
    fontCache.set(url, Buffer.from(await res.arrayBuffer()));
  }
  return fontCache.get(url);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = "";
    req.on("data", (c) => { raw += c; });
    req.on("end", () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { resolve({}); } });
  });
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, ORIGIN);
  const send = (status, body, type = "application/json; charset=utf-8") => {
    res.writeHead(status, { "Content-Type": type, "Cache-Control": "no-store", "Access-Control-Allow-Origin": "*" });
    res.end(typeof body === "string" || Buffer.isBuffer(body) ? body : JSON.stringify(body));
  };
  if (req.method === "OPTIONS") {
    res.writeHead(204, { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, POST, OPTIONS", "Access-Control-Allow-Headers": "Content-Type" });
    return res.end();
  }
  console.log(req.method, url.pathname);

  if (req.method === "GET") {
    if (url.pathname === "/api/embed/logo") return send(200, logo, "image/png");
    if (url.pathname === "/api/embed/font") {
      const bytes = await font(url.searchParams.get("f"), Number(url.searchParams.get("w")));
      return bytes ? send(200, bytes, "font/ttf") : send(404, "Unknown font.", "text/plain");
    }
    if (url.pathname.startsWith("/embed/")) return send(200, ENGINE_PAGE, "text/html; charset=utf-8");
    return send(404, { error: "Not found.", code: "not_found" });
  }

  const body = await readBody(req);
  const agent = AGENTS[body.publishableKey || body.key];
  if (!agent) return send(401, { error: "Invalid key.", code: "invalid_key" });
  const signedIn = !!body.identity?.value;

  switch (url.pathname) {
    case "/api/embed/session":
      return send(200, session(agent));
    case "/api/embed/ticket":
      // No ticket here: the apps open the conversation socket directly (wsHost).
      return send(503, { error: "Tickets are not offered here.", code: "unavailable" });
    case "/api/embed/devices":
      return send(200, { ok: true, trusted: true });
    case "/api/embed/identity/signout":
      return send(200, { ok: true });
    case "/api/embed/calls/list":
      // The main agent's calls; the help desk has none until one is booked with it.
      return send(200, { calls: (agent === AGENTS.pk_offline_demo ? calls : helpCalls).slice().sort((a, b) => (a.status === "scheduled" ? -1 : 0) - (b.status === "scheduled" ? -1 : 0) || Date.parse(b.at) - Date.parse(a.at)) });
    case "/api/embed/calls/schedule": {
      const call = { id: `call_${randomUUID().slice(0, 8)}`, at: new Date(body.at).toISOString(), status: "scheduled", source: "sdk", reason: body.reason ?? null };
      (agent === AGENTS.pk_offline_demo ? calls : helpCalls).unshift(call);
      return send(201, { call });
    }
    case "/api/embed/calls/cancel": {
      const call = [...calls, ...helpCalls].find((c) => c.id === body.callId);
      if (!call) return send(404, { error: "That call was not found.", code: "not_found" });
      call.status = "cancelled";
      return send(200, { ok: true, status: "cancelled" });
    }
    case "/api/embed/calls/outcome":
      return send(200, { ok: true });
    case "/api/embed/memory/list":
      if (!signedIn) return send(400, { error: "Sign in first.", code: "identity_required" });
      return send(200, { memories });
    case "/api/embed/memory/forget": {
      if (!signedIn) return send(400, { error: "Sign in first.", code: "identity_required" });
      const deleted = memories.length;
      memories = [];
      return send(200, { deleted });
    }
    default:
      return send(404, { error: "Not found.", code: "not_found" });
  }
});

// ── The conversation socket (RFC 6455, text frames only) ────────────────────

function wsSend(socket, text) {
  const payload = Buffer.from(text);
  const len = payload.length;
  const head = len < 126 ? Buffer.from([0x81, len]) : len < 65536 ? Buffer.from([0x81, 126, len >> 8, len & 255]) : Buffer.concat([Buffer.from([0x81, 127, 0, 0, 0, 0]), Buffer.from([len >>> 24, (len >> 16) & 255, (len >> 8) & 255, len & 255])]);
  if (!socket.destroyed) socket.write(Buffer.concat([head, payload]));
}

function wsFrames(socket, onText) {
  let buf = Buffer.alloc(0);
  let parts = [];
  socket.on("data", (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    for (;;) {
      if (buf.length < 2) return;
      const fin = buf[0] & 0x80, op = buf[0] & 0x0f, masked = buf[1] & 0x80;
      let len = buf[1] & 0x7f, off = 2;
      if (len === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
      else if (len === 127) { if (buf.length < 10) return; len = Number(buf.readBigUInt64BE(2)); off = 10; }
      const need = off + (masked ? 4 : 0) + len;
      if (buf.length < need) return;
      let data = buf.subarray(off + (masked ? 4 : 0), need);
      if (masked) { const mask = buf.subarray(off, off + 4); data = Buffer.from(data.map((b, i) => b ^ mask[i % 4])); }
      buf = buf.subarray(need);
      if (op === 0x8) { if (!socket.destroyed) socket.end(Buffer.from([0x88, 0])); return; }
      if (op === 0x9) { if (!socket.destroyed) socket.write(Buffer.concat([Buffer.from([0x8a, data.length]), data])); continue; }
      if (op === 0x1 || op === 0x0) {
        parts.push(data);
        if (fin) { const text = Buffer.concat(parts).toString("utf8"); parts = []; onText(text); }
      }
    }
  });
}

const later = (ms, fn) => setTimeout(fn, ms);

// One agent turn, the way the runtime frames it: start marker, the words in phrases, end marker.
function sayTurn(socket, words, then) {
  wsSend(socket, JSON.stringify({ type: "text", data: "<beginning_of_stream>" }));
  // Phrases keep their spaces, as the model's tokens do.
  const phrases = words.match(/[^,.!?]+[,.!?]?\s*/g) || [words];
  let t = 250;
  for (const p of phrases) { later(t, () => wsSend(socket, JSON.stringify({ type: "text", data: p }))); t += 120; }
  later(t + 60, () => { wsSend(socket, JSON.stringify({ type: "text", data: "<end_of_stream>" })); console.log("said:", words); if (then) then(); });
}

const component = (toolName) => {
  const c = components.find((x) => x.toolName === toolName);
  if (!c) return null;
  let data = c.data;
  // Times to call: the agent offers the next day it has, three of them, rather than the whole week.
  if (c.kind === "calendar_slots" && Array.isArray(data.days)) {
    const day = data.days.find((d) => (d.slots || []).length >= 3) || data.days[0];
    data = { ...data, days: [{ ...day, slots: (day.slots || []).slice(0, 3) }] };
  }
  return { widget_id: `w_${randomUUID().slice(0, 8)}`, kind: c.kind, name: c.name, data, style: c.style, fallbackText: c.fallbackText };
};

// The chat, played from the pack: what the customer asks decides the reply and the component that comes with it.
function chatReply(text, firstName) {
  const t = text.toLowerCase();
  const byKind = (kind) => components.find((c) => c.kind === kind);
  if (/book|call|schedule|callback|call me/.test(t) && byKind("calendar_slots")) {
    return { words: "Of course. Pick a time that suits you, and I'll ring you then.", widget: byKind("calendar_slots").toolName };
  }
  if (/due|today|doses|medicine|taken|order|card/.test(t) && byKind("product_list")) {
    return { words: "Here is today's list. Tick off what's done and I'll keep track.", widget: byKind("product_list").toolName };
  }
  if (/remember/.test(t)) {
    return { words: memories.length ? `Here's what I remember: ${memories.map((m) => m.text.toLowerCase()).join("; ")}.` : "Nothing yet — tell me something and I'll remember it next time." };
  }
  if (/feel|symptom|better|worse|same/.test(t) && byKind("quick_actions")) {
    return { words: "Thanks for telling me. How are you compared with yesterday?", widget: byKind("quick_actions").toolName };
  }
  return { words: `Thanks${firstName ? `, ${firstName}` : ""}. I can help with that — what would you like to do first?` };
}

server.on("upgrade", (req, socket) => {
  const url = new URL(req.url, ORIGIN);
  const key = req.headers["sec-websocket-key"];
  if (!key || !url.pathname.startsWith("/chat/v1/")) return socket.destroy();
  const accept = createHash("sha1").update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").digest("base64");
  socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
  socket.on("error", () => {});
  const text = url.searchParams.get("text_chat") === "1";
  console.log("socket", text ? "chat" : "call", url.pathname);
  let vars = {};
  try { vars = JSON.parse(Buffer.from(url.searchParams.get("context_token") || "", "base64").toString("utf8") || "{}"); } catch {}
  const firstName = String(vars.name || "").split(" ")[0];
  let greeted = false;
  wsFrames(socket, (raw) => {
    let frame; try { frame = JSON.parse(raw); } catch { return; }
    if (frame.type === "init" && !greeted) {
      greeted = true;
      const hello = text
        ? `Hello ${firstName || "there"}, I'm ${pack.agentName}, your ${pack.agentRole}. How are you feeling today?`
        : `Hi ${firstName || "there"}, it's ${pack.agentName}. I'm calling about the time you booked.`;
      later(500, () => sayTurn(socket, hello));
    } else if (frame.type === "text" && text) {
      const reply = chatReply(String(frame.data || ""), firstName);
      later(700, () => sayTurn(socket, reply.words, () => {
        const w = reply.widget && component(reply.widget);
        if (w) later(200, () => { wsSend(socket, JSON.stringify({ type: "widget", widget: w })); console.log("showed:", w.name); });
      }));
    }
  });
});

// The voice bar's engine page, played: it connects when the app starts the call and says one line.
const ENGINE_PAGE = `<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body>
<script>
  const post = (m) => { try { window.ReactNativeWebView.postMessage(JSON.stringify(m)); } catch (e) {} };
  window.addEventListener("message", (e) => {
    const m = e.data || {};
    if (m.type === "graine:start-call") {
      post({ type: "graine:call", connecting: true, connected: false });
      setTimeout(() => {
        post({ type: "graine:call", connecting: false, connected: true });
        setTimeout(() => post({ type: "graine:caption", role: "agent", text: ${JSON.stringify(`Hi, I'm ${pack.agentName}. What can I help you with today?`)}, live: false }), 900);
      }, 1200);
    }
    if (m.type === "graine:end-call") post({ type: "graine:ended" });
  });
  post({ type: "graine:ready" });
</script></body></html>`;

if (!existsSync(join(root, `scripts/offline/${PACK}.design.json`))) {
  console.error(`No design for ${PACK} in scripts/offline/.`);
  process.exit(1);
}
server.listen(PORT, "127.0.0.1", () => {
  console.log(`Offline demo (${pack.appTitle}) on ${ORIGIN}`);
});

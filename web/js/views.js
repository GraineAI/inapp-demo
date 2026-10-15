/**
 * The screens. Each takes the page's state (`ctx`, built by app.js) and returns
 * a fresh element; app.js swaps it in. Every word that names a domain comes from
 * the pack — these functions know screens, not patients or cards.
 */
import { h } from "./dom.js";
import * as L from "./logic.js";

// ── Pieces ──────────────────────────────────────────────────────────────────

const SVG_NS = "http://www.w3.org/2000/svg";
const ICONS = {
  voice: ["M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z", "M19 11a7 7 0 0 1-14 0", "M12 18v3"],
  chat: ["M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"],
  phone: ["M5 4h3l2 5-2.5 1.5a11 11 0 0 0 6 6L15 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"],
  refresh: ["M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7"],
  chevron: ["M9 6l6 6-6 6"],
  info: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 16v-4", "M12 8h.01"],
};

export function icon(name, size = 18) {
  const s = document.createElementNS(SVG_NS, "svg");
  s.setAttribute("viewBox", "0 0 24 24");
  s.setAttribute("width", String(size));
  s.setAttribute("height", String(size));
  s.setAttribute("fill", "none");
  s.setAttribute("stroke", "currentColor");
  s.setAttribute("stroke-width", "2");
  s.setAttribute("stroke-linecap", "round");
  s.setAttribute("stroke-linejoin", "round");
  s.setAttribute("aria-hidden", "true");
  s.setAttribute("focusable", "false");
  for (const d of ICONS[name] || []) {
    const p = document.createElementNS(SVG_NS, "path");
    p.setAttribute("d", d);
    s.append(p);
  }
  return s;
}

const chip = (text, tone = "neutral", extra = {}) => h("span", { class: `chip chip-${tone}`, ...extra }, text);

function button(label, { variant = "secondary", onClick, disabled, iconName, id, type = "button", ...rest } = {}) {
  return h("button", { type, class: `btn btn-${variant}`, onClick, disabled, id, ...rest },
    iconName ? icon(iconName) : null, h("span", null, label));
}

function card(title, ...children) {
  return h("section", { class: "card" }, title ? h("h2", { class: "card-title" }, title) : null, ...children);
}

function pageHead(kicker, title, lead) {
  return h("header", { class: "page-head" },
    kicker ? h("p", { class: "kicker" }, kicker) : null,
    h("h1", { tabindex: "-1" }, title),
    lead ? h("p", { class: "lead" }, lead) : null);
}

function standingNotice(pack) {
  return pack.notice ? h("p", { class: "banner" }, icon("info", 16), h("span", null, pack.notice)) : null;
}

function switchRow({ id, label, checked, onChange, help, disabled }) {
  return h("div", { class: "switch-row" },
    h("div", { class: "switch-text" },
      h("label", { for: id, class: "switch-label" }, label),
      help ? h("p", { class: "help", id: `${id}-help` }, help) : null),
    h("input", {
      id, type: "checkbox", role: "switch", class: "switch",
      checked: !!checked, disabled, "aria-describedby": help ? `${id}-help` : null,
      onChange: (e) => onChange(e.target.checked),
    }));
}

function issue(text) {
  return text ? h("p", { class: "issue", role: "alert" }, text) : null;
}

/**
 * Why a screen that needs the assistant cannot use it right now, in words —
 * or null when it can. The notice at the top of the page has the details.
 */
export function assistantGap(ctx, method) {
  const a = ctx.assistant;
  switch (a.state) {
    case "not_configured": return "The assistant is not configured for this page, so this is unavailable. See web/README.md.";
    case "unavailable": return "The Graine loader did not load, so this is unavailable. See the notice at the top of the page.";
    case "idle": case "loading": return null;   // still on its way: the screen shows its loading state
    default:
      return a.supports(method) ? null
        : `The Graine loader on this page does not have ${method}() yet, so this is unavailable. See the notice at the top of the page.`;
  }
}

/** The display name of the agent a call is with ("main" or "help"). */
function callAgentName(ctx, call) {
  return call.agent === "help" && ctx.agents.help ? ctx.agents.help.name : ctx.agents.main.name;
}

function callRow(ctx, call, { showAgent = true } = {}) {
  const { now } = ctx;
  const agentName = callAgentName(ctx, call);
  const by = L.bookedBy(call.source, agentName);
  const cancelling = ctx.busy.cancel === call.id;
  return h("li", { class: "list-row", "data-call": call.id, "data-agent": call.agent || "main" },
    h("div", { class: "list-main" },
      h("p", { class: "list-title" }, L.formatWhen(call.at, now)),
      showAgent ? h("p", { class: "list-agent" }, `With ${agentName}`) : null,
      call.reason ? h("p", { class: "list-sub" }, call.reason) : null,
      by ? h("p", { class: "list-sub" }, by) : null),
    h("div", { class: "list-side" },
      chip(L.statusLabel(call.status), L.statusTone(call.status), { "data-status": call.status }),
      call.canCancel
        ? button(cancelling ? "Cancelling…" : "Cancel", {
          variant: "link", disabled: cancelling, onClick: () => ctx.on.cancelCall(call),
          "aria-label": `Cancel the call with ${agentName}, ${L.formatWhen(call.at, now)}`,
        })
        : null));
}

/**
 * The booking card: what rings where, and the agent's booking button, which opens the widget's own
 * booking sheet (the day, the time, the time zone and an optional note, in the agent's design). The
 * button is enabled while the loader says this visitor can book (`bookable`); otherwise it stays on the
 * page, disabled, with one line saying why. The same card books with the main agent on Scheduled calls
 * and with the help agent in Help.
 *
 *   spec: { agentName, callName, bookLabel, gap }
 */
function bookingCard(ctx, spec) {
  const { pack, user, bookable } = ctx;
  const off = !!spec.gap || !bookable;
  return card(null,
    h("p", { class: "note", id: "rings-phone" }, icon("info", 16), h("span", null,
      `${spec.agentName} calls ${L.firstName(user.name)} on their phone, not in this browser: the call rings the ` +
      `${pack.appTitle} app on the phone where ${L.firstName(user.name)} is signed in, as “${spec.callName}”. ` +
      "A browser cannot receive calls. With no phone signed in, the call ends as “No device”.")),
    h("div", { class: "actions-row" },
      button(spec.bookLabel || "Book a call", {
        variant: "primary", iconName: "phone", id: "book-open", disabled: off,
        "aria-describedby": off && !spec.gap ? "book-unavailable" : null, onClick: ctx.on.book,
      })),
    spec.gap ? issue(spec.gap) : bookable ? null : h("p", { class: "help", id: "book-unavailable" }, L.BOOKING_UNAVAILABLE));
}

function callList(ctx, title, items, empty, options) {
  return h("section", { class: "card" },
    h("h2", { class: "card-title" }, title),
    items.length ? h("ul", { class: "list" }, items.map((c) => callRow(ctx, c, options))) : h("p", { class: "muted" }, empty));
}

// ── Sign in ─────────────────────────────────────────────────────────────────

export function signInView(ctx) {
  const { pack, packs, signIn } = ctx;
  const chosen = pack.users.find((u) => u.id === signIn.userId) || null;
  const noun = pack.userNoun;

  const picker = h("select", {
    id: "pack-picker", class: "select",
    onChange: (e) => ctx.on.switchPack(e.target.value),
  }, packs.map((p) => {
    const option = h("option", { value: p.id }, `${p.appTitle} — ${p.agentName}, ${p.agentRole}`);
    option.selected = p.id === pack.id;
    return option;
  }));

  const people = h("fieldset", { class: "people" },
    h("legend", { class: "card-title" }, `Choose a ${noun}`),
    pack.users.map((u) => {
      const id = `user-${u.id}`;
      const traits = Object.values(u.traits || {}).filter(Boolean).join(" · ");
      return h("label", { class: `person${chosen && chosen.id === u.id ? " is-chosen" : ""}`, for: id },
        h("input", {
          type: "radio", name: "user", id, value: u.id, checked: !!(chosen && chosen.id === u.id),
          onChange: () => ctx.on.pickUser(u.id),
        }),
        h("span", { class: "person-text" },
          h("span", { class: "person-name" }, u.name),
          h("span", { class: "person-meta" }, `${pack.identifierLabel} ${u.id}`),
          traits ? h("span", { class: "person-meta" }, traits) : null));
    }));

  return h("div", { class: "view view-narrow" },
    pageHead("Graine Demo", "Sign in", "Pick a use case and a person to sign in as. Each use case is its own assistant."),
    card(null,
      h("label", { for: "pack-picker", class: "field-label" }, "Use case"),
      picker,
      h("p", { class: "help" }, pack.tagline)),
    h("section", { class: "card" }, people),
    card(null,
      switchRow({
        id: "signin-memory",
        label: `Let ${pack.agentName} remember our conversations`,
        checked: signIn.memory,
        onChange: ctx.on.setSignInMemory,
        help: `${pack.agentName} uses what you said before to pick up where you left off. You can switch this off, or erase what ${pack.agentName} remembers, at any time.`,
      })),
    h("div", { class: "actions-row" },
      button(chosen ? `Continue as ${L.firstName(chosen.name)}` : "Continue", {
        variant: "primary", id: "signin-continue", disabled: !chosen, onClick: () => ctx.on.signIn(),
      })),
    h("p", { class: "fine" }, "Demo sign-in, no password. All people and data in this demo are fictional."));
}

// ── Home ────────────────────────────────────────────────────────────────────

function homeCard(ctx, c) {
  const { pack, user, statuses, calls, now } = ctx;
  const open = button(`Open ${L.screenTitle(c.target, pack)}`, { variant: "link", onClick: () => ctx.on.go(c.target) });
  let live = null;
  if (c.target === "items") {
    const rows = L.rowsFor(pack, user.id).slice(0, 3);
    live = rows.length
      ? h("ul", { class: "mini-list" }, rows.map((r) => {
        const s = L.rowStatus(r, statuses);
        return h("li", null, h("span", null, r.title), s ? chip(s, L.statusToneForRow(r, statuses)) : null);
      }))
      : h("p", { class: "muted" }, pack.items.emptyText);
  } else if (c.target === "calls") {
    const next = L.nextCall(calls.list);
    live = next
      ? h("p", { class: "next-call" }, h("strong", null, L.formatWhen(next.at, now)), " ",
        chip(L.statusLabel(next.status), L.statusTone(next.status)),
        next.agent === "help" && ctx.agents.help ? h("span", { class: "list-sub" }, `With ${ctx.agents.help.name}`) : null)
      : h("p", { class: "muted" }, calls.loading && !calls.loaded ? "Loading…" : "No call booked.");
  } else if (c.target === "memory") {
    live = h("p", null, chip(ctx.memoryOn ? "Memory: on" : "Memory: off", ctx.memoryOn ? "positive" : "neutral"));
  }
  return h("section", { class: "card card-link", "data-card": c.id },
    h("h2", { class: "card-title" }, c.title),
    h("p", { class: "muted" }, c.body),
    live,
    open);
}

export function homeView(ctx) {
  const { pack, user } = ctx;
  const traits = Object.values(user.traits || {}).filter(Boolean);
  const hasCallsCard = pack.home.cards.some((c) => c.target === "calls");
  const next = L.nextCall(ctx.calls.list);
  return h("div", { class: "view" },
    pageHead(pack.home.title, `Hello, ${L.firstName(user.name)}`),
    h("div", { class: "meta-row" },
      traits.map((t) => chip(t)),
      h("a", { href: "#/memory", class: `chip chip-link chip-${ctx.memoryOn ? "positive" : "neutral"}`, id: "memory-chip" },
        ctx.memoryOn ? "Memory: on" : "Memory: off")),
    standingNotice(pack),
    h("section", { class: "card card-agent" },
      h("h2", { class: "card-title" }, `${pack.agentName}, your ${pack.agentRole}`),
      h("p", { class: "muted" }, "Voice and chat open in the panel at the bottom right of the page."),
      h("div", { class: "actions-row" },
        button(`Talk to ${pack.agentName}`, { variant: "primary", iconName: "voice", id: "talk", onClick: ctx.on.talk }),
        button("Chat", { iconName: "chat", onClick: () => ctx.on.go("chat") }))),
    hasCallsCard ? null : card("Next call",
      next ? h("p", null, L.formatWhen(next.at, ctx.now)) : h("p", { class: "muted" }, "No call booked.")),
    h("div", { class: "grid" }, pack.home.cards.map((c) => homeCard(ctx, c))));
}

// ── Chat ────────────────────────────────────────────────────────────────────

export function chatView(ctx) {
  const { pack } = ctx;
  return h("div", { class: "view view-narrow" },
    pageHead("Chat", pack.agentName, `The chat opens in the panel at the bottom right of the page; voice is in the same panel.`),
    standingNotice(pack),
    card(null,
      h("div", { class: "actions-row" },
        button("Open the chat", { variant: "primary", iconName: "chat", id: "open-chat", onClick: ctx.on.talk }))),
    card("Suggestions",
      h("p", { class: "help" }, "Choose one to copy it, then paste it into the chat."),
      h("div", { class: "chips" }, pack.starters.map((s) =>
        h("button", { type: "button", class: "chip chip-button", onClick: () => ctx.on.starter(s) }, s)))));
}

// ── Items ───────────────────────────────────────────────────────────────────

function itemRow(ctx, row) {
  const { pack, statuses } = ctx;
  const status = L.rowStatus(row, statuses);
  const open = ctx.openRowId === row.id;
  const bodyId = `row-${row.id}-details`;
  const opener = pack.actions.find((a) => a.effect === "open_item");
  const buttons = L.statusButtons(pack, row, statuses);
  return h("li", { class: `item${open ? " is-open" : ""}`, id: `row-${row.id}`, "data-row": row.id },
    h("div", { class: "item-head" },
      h("div", { class: "item-text" },
        h("p", { class: "item-title" }, row.title),
        row.subtitle ? h("p", { class: "item-sub" }, row.subtitle) : null),
      status ? chip(status, L.statusToneForRow(row, statuses), { "data-status": status }) : null),
    open && row.fields && row.fields.length
      ? h("dl", { class: "item-fields", id: bodyId }, row.fields.map((f) => [h("dt", null, f.label), h("dd", null, f.value)]))
      : null,
    h("div", { class: "item-actions" },
      button(open ? "Hide details" : (opener ? opener.label : "Details"), {
        variant: "link", "aria-expanded": open ? "true" : "false", "aria-controls": open ? bodyId : null,
        onClick: () => ctx.on.toggleRow(row.id),
      }),
      buttons.map((a) => button(a.label, { onClick: () => ctx.on.setRowStatus(row.id, a.name), "data-action": a.name }))));
}

export function itemsView(ctx) {
  const { pack, user } = ctx;
  const rows = L.rowsFor(pack, user.id);
  return h("div", { class: "view view-narrow" },
    pageHead(null, pack.items.title),
    rows.length
      ? h("ul", { class: "items" }, rows.map((r) => itemRow(ctx, r)))
      : card(null, h("p", { class: "muted" }, pack.items.emptyText)));
}

// ── Scheduled calls ─────────────────────────────────────────────────────────

export function callsView(ctx) {
  const { pack, calls } = ctx;
  const gap = assistantGap(ctx, "scheduledCalls");
  const bookGap = assistantGap(ctx, "openBooking");
  const { upcoming, past } = L.orderCalls(calls.list);
  const help = ctx.agents.help;

  return h("div", { class: "view view-narrow" },
    pageHead(null, pack.call.title,
      help && ctx.helpConfigured ? `Calls with ${ctx.agents.main.name} and with ${help.name}, each named.` : null),
    bookingCard(ctx, {
      agentName: ctx.agents.main.name,
      callName: pack.call.callName,
      bookLabel: pack.call.bookLabel,
      gap: bookGap,
    }),
    h("div", { class: "list-tools" },
      h("p", { class: "muted", "aria-live": "polite" }, calls.loading ? "Refreshing…" : ""),
      button("Refresh", { iconName: "refresh", onClick: ctx.on.refreshCalls, disabled: calls.loading || !!gap, id: "calls-refresh" })),
    issue(gap || calls.error),
    help && calls.helpError ? issue(`Calls with ${help.name}: ${calls.helpError}`) : null,
    gap ? null : callList(ctx, "Upcoming", upcoming, calls.loading && !calls.loaded ? "Loading…" : "No upcoming calls."),
    !gap && past.length ? callList(ctx, "Earlier", past, "") : null,
    h("details", { class: "card legend" },
      h("summary", null, "What the statuses mean"),
      h("dl", { class: "legend-list" }, L.CALL_STATUSES.map((s) => [
        h("dt", null, chip(L.statusLabel(s), L.statusTone(s))),
        h("dd", null, L.STATUS_HELP[s]),
      ]))));
}

// ── Help: the second agent ──────────────────────────────────────────────────

/**
 * The help agent's screen: while it is open, the page's assistant (voice and chat) is the help
 * agent, and its bookings go to it; every other section uses the main agent. Without a help agent
 * in config.js it says how to add one.
 */
export function helpView(ctx) {
  const { pack, calls, assistant } = ctx;
  const help = ctx.agents.help;
  const main = ctx.agents.main;
  if (!help) {
    return h("div", { class: "view view-narrow" },
      pageHead(null, "Help"),
      card(null, h("p", { class: "muted" }, "This use case has no help agent.")));
  }
  const lead = `The ${help.role}: chat, voice and calls with its own assistant. The other sections use ${main.name}.`;
  if (!ctx.helpConfigured) {
    return h("div", { class: "view view-narrow" },
      pageHead("Help", help.name, lead),
      h("section", { class: "card", id: "help-setup" },
        h("h2", { class: "card-title" }, `Add ${help.name}`),
        h("p", null, `This page has no help agent yet, so the assistant here is still ${main.name}. ` +
          `Create the help agent from agent-setup/${pack.id}/ (its prompt and settings), add this site to its ` +
          "Allowed domains, then add its id and key to web/config.js:"),
        h("pre", { class: "code" }, h("code", null, 'help: { agentId: "…", publishableKey: "pk_live_…" },')),
        h("p", { class: "help" }, "Reload the page after saving config.js.")));
  }
  const switching = assistant.agent !== "help";
  const switchGap = !assistant.canSwitch
    ? `The Graine loader on this page cannot switch to ${help.name} yet, so the assistant here is still ${main.name}. See the notice at the top of the page.`
    : null;
  const bookGap = switchGap || assistantGap(ctx, "openBooking")
    || (switching ? `Switching the assistant to ${help.name}…` : null);
  const mine = L.orderCalls(calls.list.filter((c) => c.agent === "help"));

  return h("div", { class: "view view-narrow" },
    pageHead("Help", help.name, lead),
    issue(switchGap),
    h("section", { class: "card card-agent", id: "help-agent" },
      h("h2", { class: "card-title" }, help.name),
      h("p", { class: "muted" }, switchGap ? `Voice and chat here still go to ${main.name}.`
        : `Voice and chat in the panel at the bottom right now go to ${help.name}.`),
      h("div", { class: "actions-row" },
        button(`Talk to ${help.name}`, { variant: "primary", iconName: "voice", id: "help-talk", onClick: ctx.on.talk, disabled: !!switchGap }))),
    card("Suggestions",
      h("p", { class: "help" }, "Choose one to copy it, then paste it into the chat."),
      h("div", { class: "chips" }, (help.starters || []).map((s) =>
        h("button", { type: "button", class: "chip chip-button", disabled: !!switchGap, onClick: () => ctx.on.starter(s) }, s)))),
    bookingCard(ctx, {
      agentName: help.name,
      callName: help.callName,
      bookLabel: help.bookLabel,
      gap: bookGap,
    }),
    calls.helpError ? issue(`Calls with ${help.name}: ${calls.helpError}`) : null,
    callList(ctx, `Your calls with ${help.name}`, [...mine.upcoming, ...mine.past],
      calls.loading && !calls.loaded ? "Loading…" : "No calls booked.", { showAgent: false }));
}

// ── Memory ──────────────────────────────────────────────────────────────────

function memoryList(ctx) {
  const { pack, memories, now } = ctx;
  const gap = assistantGap(ctx, "memories");
  if (gap) return issue(gap);
  if (memories.error) return issue(memories.error);
  if (!memories.list.length) {
    const example = pack.memoryExamples[0];
    return h("p", { class: "muted", id: "memory-empty" }, memories.loading && !memories.loaded
      ? "Loading…"
      : `Nothing yet. Tell ${pack.agentName} something in a conversation${example ? ` — for example, “${example.tell}”` : ""} ` +
        `— and ${pack.agentName} will remember it next time.`);
  }
  return h("ul", { class: "list", id: "memory-list" }, memories.list.map((m) => {
    const when = m.updatedAt || m.createdAt;
    return h("li", { class: "list-row" },
      h("div", { class: "list-main" },
        h("p", { class: "memory-text" }, m.text),
        when ? h("p", { class: "list-sub" }, L.formatWhen(when, now)) : null));
  }));
}

function memorySwitch(ctx, id) {
  const { pack } = ctx;
  return switchRow({
    id,
    label: `Let ${pack.agentName} remember our conversations`,
    checked: ctx.memoryOn,
    onChange: ctx.on.setMemory,
    disabled: ctx.busy.memory,
    help: `Applies from your next conversation. ${pack.agentName} keeps what is useful for helping you, and never shares it with anyone else signed in here.`,
  });
}

function eraseButton(ctx) {
  const { pack } = ctx;
  return button(ctx.busy.erase ? "Erasing…" : `Erase everything ${pack.agentName} remembers`, {
    variant: "danger", id: "erase-memory", disabled: ctx.busy.erase || !!assistantGap(ctx, "forgetMemory"),
    onClick: ctx.on.erase,
  });
}

export function memoryView(ctx) {
  const { pack, memories } = ctx;
  const gap = assistantGap(ctx, "memories");
  return h("div", { class: "view view-narrow" },
    pageHead(null, "Memory"),
    card(null, memorySwitch(ctx, "memory-switch")),
    h("section", { class: "card" },
      h("div", { class: "card-head" },
        h("h2", { class: "card-title" }, `What ${pack.agentName} remembers about you`),
        button("Refresh", { iconName: "refresh", onClick: ctx.on.refreshMemories, disabled: memories.loading || !!gap, id: "memory-refresh" })),
      memoryList(ctx)),
    card("Try it",
      h("ol", { class: "steps" }, pack.memoryExamples.map((ex) => h("li", null,
        h("p", null, "Tell ", pack.agentName, ": ", h("q", null, ex.tell)),
        h("p", { class: "muted" }, "In a new conversation, ask: ", h("q", null, ex.ask)),
        h("p", { class: "muted" }, `${pack.agentName} should answer: `, h("q", null, ex.expect))))),
      h("p", { class: "help" }, `Signed in as someone else, ${pack.agentName} does not know any of it.`)),
    h("section", { class: "card card-danger" },
      h("h2", { class: "card-title" }, "Erase"),
      h("p", { class: "muted" }, "Erasing cannot be undone. Your scheduled calls are not affected."),
      eraseButton(ctx)));
}

// ── Profile ─────────────────────────────────────────────────────────────────

const row = (label, value, mono) => [h("dt", null, label), h("dd", { class: mono ? "mono" : null }, value)];

const STATE_WORDS = {
  idle: "Starting", not_configured: "Not configured", loading: "Loading", unavailable: "Did not load",
  starting: "Starting", ready: "Ready", not_started: "Did not start",
};

export function assistantWords(state) {
  return STATE_WORDS[state] || state;
}

export function profileView(ctx) {
  const { pack, user, assistant, theme } = ctx;
  const themeSelect = h("select", { id: "theme-select", class: "select", onChange: (e) => ctx.on.setTheme(e.target.value) },
    [["system", "Match this device"], ["light", "Light"], ["dark", "Dark"]].map(([v, label]) => {
      const o = h("option", { value: v }, label);
      o.selected = v === theme;
      return o;
    }));
  return h("div", { class: "view view-narrow" },
    pageHead(null, "Profile"),
    card(pack.userNoun.charAt(0).toUpperCase() + pack.userNoun.slice(1),
      h("dl", { class: "facts" },
        row("Name", user.name),
        row(pack.identifierLabel, user.id, true),
        Object.entries(user.traits || {}).map(([k, v]) => row(L.humanize(k), v)))),
    h("section", { class: "card" },
      h("h2", { class: "card-title" }, "Memory"),
      memorySwitch(ctx, "profile-memory-switch"),
      h("div", { class: "actions-row" },
        button(`What ${pack.agentName} remembers`, { onClick: () => ctx.on.go("memory") }),
        eraseButton(ctx))),
    card("This browser",
      h("dl", { class: "facts" },
        row("Assistant", assistantWords(assistant.state)),
        row("Graine loader", assistant.version ? `Version ${assistant.version}` : "Not loaded"),
        row("Install ID", assistant.installId || "Not shown by this loader", !!assistant.installId),
        row("Agent ID", assistant.agentId || "Not configured", !!assistant.agentId),
        ctx.agents.help ? row(`${ctx.agents.help.name} (help agent)`, assistant.helpAgentId || "Not configured", !!assistant.helpAgentId) : null,
        row("Server", assistant.baseUrl, true)),
      h("p", { class: "help" }, "The install ID tells this browser apart from your other devices. It is not linked to your phone number.")),
    card("Appearance",
      h("label", { for: "theme-select", class: "field-label" }, "Theme"),
      themeSelect),
    h("div", { class: "actions-row" },
      button("Sign out", { variant: "danger", id: "sign-out", onClick: ctx.on.signOut })));
}

// ── Nothing to show ─────────────────────────────────────────────────────────

export function noPacksView(failures) {
  return h("div", { class: "view view-narrow" },
    pageHead("Graine Demo", "No use cases found"),
    card(null,
      h("p", null, "This page reads its use cases from ", h("code", null, "usecases/"), " beside the ", h("code", null, "web/"),
        " folder. Serve the whole demo folder and open /web/ — see web/README.md."),
      failures.length ? h("ul", { class: "plain-list" }, failures.map((f) => h("li", null, f.problem))) : null));
}

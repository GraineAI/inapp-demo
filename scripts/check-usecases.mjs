#!/usr/bin/env node
/**
 * Checks every use-case pack in usecases/ against usecases/usecase.schema.json, plus the rules a
 * schema cannot say (an action a row names must exist, the open_screen list must name every screen,
 * a row's user must exist). With --agent-setup it also checks that each agent-setup/<pack>/actions.json
 * is exactly what the pack declares (see build-agent-setup.mjs), that its components.json is in the
 * dashboard's component shape, and that prompt.md and settings.md name every action, component,
 * identifier and call name the apps rely on.
 *
 * No dependencies: the validator below implements the JSON Schema keywords the schema uses, and
 * fails loudly on any keyword it does not know, so the schema cannot quietly outgrow it.
 *
 *   node scripts/check-usecases.mjs [--agent-setup]
 */
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { actionsJsonFor } from "./usecase-tools.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const dir = join(root, "usecases");
const schema = JSON.parse(readFileSync(join(dir, "usecase.schema.json"), "utf8"));

const KNOWN = new Set([
  "$schema", "$id", "title", "description", "$defs", "$ref", "type", "required", "properties", "additionalProperties",
  "pattern", "enum", "const", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "items", "not",
  "maxProperties", "propertyNames", "allOf", "if", "then", "contains", "minimum", "maximum",
]);

function typeOf(v) {
  if (v === null) return "null";
  if (Array.isArray(v)) return "array";
  if (Number.isInteger(v)) return "integer";
  return typeof v;
}

function resolve(ref) {
  if (!ref.startsWith("#/")) throw new Error(`Only local $ref is supported: ${ref}`);
  return ref.slice(2).split("/").reduce((node, key) => node[key], schema);
}

/** Errors for `value` against `s`, as "path: message" strings. */
function validate(value, s, path = "$") {
  const errors = [];
  for (const key of Object.keys(s)) if (!KNOWN.has(key)) throw new Error(`Schema keyword "${key}" at ${path} is not supported by this checker.`);
  if (s.$ref) return validate(value, resolve(s.$ref), path);
  const t = typeOf(value);
  if (s.type) {
    const ok = s.type === t || (s.type === "number" && (t === "integer" || t === "number")) || (s.type === "integer" && t === "integer");
    if (!ok) return [`${path}: expected ${s.type}, got ${t}`];
  }
  if ("const" in s && JSON.stringify(value) !== JSON.stringify(s.const)) errors.push(`${path}: must be ${JSON.stringify(s.const)}`);
  if (s.enum && !s.enum.some((e) => JSON.stringify(e) === JSON.stringify(value))) errors.push(`${path}: must be one of ${s.enum.join(", ")}`);
  if (s.not && validate(value, s.not, path).length === 0) errors.push(`${path}: value is not allowed here`);
  if (t === "integer" || t === "number") {
    if (s.minimum !== undefined && value < s.minimum) errors.push(`${path}: less than ${s.minimum}`);
    if (s.maximum !== undefined && value > s.maximum) errors.push(`${path}: more than ${s.maximum}`);
  }
  if (t === "string") {
    if (s.minLength !== undefined && [...value].length < s.minLength) errors.push(`${path}: shorter than ${s.minLength}`);
    if (s.maxLength !== undefined && [...value].length > s.maxLength) errors.push(`${path}: longer than ${s.maxLength} characters`);
    if (s.pattern && !new RegExp(s.pattern, "u").test(value)) errors.push(`${path}: does not match ${s.pattern}`);
  }
  if (t === "array") {
    if (s.minItems !== undefined && value.length < s.minItems) errors.push(`${path}: fewer than ${s.minItems} items`);
    if (s.maxItems !== undefined && value.length > s.maxItems) errors.push(`${path}: more than ${s.maxItems} items`);
    if (s.uniqueItems && new Set(value.map((v) => JSON.stringify(v))).size !== value.length) errors.push(`${path}: items repeat`);
    if (s.items) value.forEach((v, i) => errors.push(...validate(v, s.items, `${path}[${i}]`)));
    if (s.contains && !value.some((v, i) => validate(v, s.contains, `${path}[${i}]`).length === 0)) {
      errors.push(`${path}: no item matches ${JSON.stringify(s.contains)}`);
    }
  }
  if (t === "object") {
    for (const key of s.required ?? []) if (!(key in value)) errors.push(`${path}: missing "${key}"`);
    const props = s.properties ?? {};
    for (const [key, v] of Object.entries(value)) {
      if (props[key]) errors.push(...validate(v, props[key], `${path}.${key}`));
      else if (s.additionalProperties === false) errors.push(`${path}: unexpected "${key}"`);
      else if (s.additionalProperties && typeof s.additionalProperties === "object") errors.push(...validate(v, s.additionalProperties, `${path}.${key}`));
      if (s.propertyNames) errors.push(...validate(key, s.propertyNames, `${path}.${key} (name)`));
    }
    if (s.maxProperties !== undefined && Object.keys(value).length > s.maxProperties) errors.push(`${path}: more than ${s.maxProperties} keys`);
  }
  for (const sub of s.allOf ?? []) {
    if (sub.if && validate(value, sub.if, path).length === 0) errors.push(...validate(value, sub.then ?? {}, path));
    else if (!sub.if) errors.push(...validate(value, sub, path));
  }
  return errors;
}

/** What the schema cannot say. */
function crossCheck(pack, file) {
  const errors = [];
  if (`${pack.id}.json` !== file) errors.push(`id "${pack.id}" does not match the file name ${file}`);
  const main = pack.agents.main;
  if (main.name !== pack.agentName) errors.push(`agents.main.name "${main.name}" must equal agentName "${pack.agentName}"`);
  if (main.role !== pack.agentRole) errors.push(`agents.main.role must equal agentRole`);
  if (JSON.stringify(main.starters) !== JSON.stringify(pack.starters)) errors.push("agents.main.starters must equal starters");
  if (pack.agents.help.name === main.name) errors.push("the help agent needs a name of its own");
  if (pack.agents.help.callName === pack.call.callName) errors.push("the help agent's callName must differ from the main agent's");
  const userIds = pack.users.map((u) => u.id);
  if (new Set(userIds).size !== userIds.length) errors.push("two users share an id");
  for (const u of pack.users) {
    for (const reserved of [pack.identifierField, "name"]) {
      if (reserved in u.traits) errors.push(`user ${u.id}: traits must not repeat "${reserved}"`);
    }
  }
  const names = pack.actions.map((a) => a.name);
  if (new Set(names).size !== names.length) errors.push("two actions share a name");
  const byName = new Map(pack.actions.map((a) => [a.name, a]));
  for (const effect of ["open_screen", "open_item"]) {
    if (!pack.actions.some((a) => a.effect === effect)) errors.push(`no action with effect ${effect}`);
  }
  const screens = ["home", "chat", pack.items.screenName, "calls", "memory", "profile"];
  for (const a of pack.actions) {
    for (const arg of a.args) {
      if (arg.enum && arg.type !== "string") errors.push(`action ${a.name}: enum on a ${arg.type} argument`);
    }
    if (a.effect === "open_screen") {
      const screen = a.args.find((x) => x.name === "screen");
      const listed = [...(screen?.enum ?? [])].sort().join(",");
      if (listed !== [...screens].sort().join(",")) errors.push(`action ${a.name}: screen enum must be exactly ${screens.join(", ")}`);
    }
  }
  const rowIds = pack.items.rows.map((r) => r.id);
  if (new Set(rowIds).size !== rowIds.length) errors.push("two rows share an id");
  for (const r of pack.items.rows) {
    for (const name of r.actions) {
      const a = byName.get(name);
      if (!a) errors.push(`row ${r.id}: action "${name}" is not declared`);
      else if (a.effect !== "set_status") errors.push(`row ${r.id}: "${name}" is ${a.effect}; rows list set_status actions only`);
    }
    for (const id of r.userIds ?? []) if (!userIds.includes(id)) errors.push(`row ${r.id}: unknown user ${id}`);
  }
  for (const u of pack.users) {
    if (!pack.items.rows.some((r) => !r.userIds || r.userIds.includes(u.id))) errors.push(`user ${u.id} has no rows`);
  }
  return errors;
}


/** The agent-setup kit: components in the dashboard's shape, and a prompt and settings that name what the app relies on. */
const KINDS = {
  quick_actions: (d) => Array.isArray(d.actions) && d.actions.every((a) => a.id && a.label),
  product_list: (d) => Array.isArray(d.items) && d.items.length > 0 && d.items.every((i) => i.id && i.title),
  calendar_slots: (d) => typeof d.title === "string" && Array.isArray(d.days) && d.days.every((x) => x.id && x.label && Array.isArray(x.slots)),
  lead_form: (d) => typeof d.title === "string" && typeof d.submitLabel === "string" && Array.isArray(d.fields)
    && d.fields.every((f) => f.name && f.label && ["text", "tel", "email", "number"].includes(f.type)),
  info_card: (d) => typeof d.title === "string" && typeof d.summary === "string" && Array.isArray(d.facts),
  plan_comparison: (d) => typeof d.title === "string" && Array.isArray(d.plans),
  blank: (d) => Array.isArray(d.blocks),
};
const STYLE = {
  preset: ["gallery", "cards", "list", "spotlight", "compact"], imageSize: ["small", "medium", "large"],
  density: ["comfortable", "compact"], button: ["filled", "outline", "row"], surface: ["auto", "screen", "sheet", "card"],
  platform: ["auto", "ios", "material", "oneui"], select: ["one", "many"],
};
const TOOL = /^[a-z][a-z0-9_]{0,63}$/;
const RESERVED = ["switch_language", "transfer_call", "end_call"];

function checkKit(pack) {
  const errors = [];
  const dir = join(root, "agent-setup", pack.id);
  const compPath = join(dir, "components.json");
  const screens = ["home", "chat", pack.items.screenName, "calls", "memory", "profile"];
  let comps = [];
  if (!existsSync(compPath)) errors.push(`agent-setup/${pack.id}/components.json is missing`);
  else comps = JSON.parse(readFileSync(compPath, "utf8"));
  if (!Array.isArray(comps) || comps.length === 0) errors.push("components.json must be a non-empty array");
  const tools = new Set();
  for (const c of Array.isArray(comps) ? comps : []) {
    const at = `component ${c.name ?? "?"}`;
    if (typeof c.name !== "string" || !c.name) errors.push(`${at}: no name`);
    if (!KINDS[c.kind]) errors.push(`${at}: unknown kind ${c.kind}`);
    else if (!c.data || !KINDS[c.kind](c.data)) errors.push(`${at}: data is not a ${c.kind}`);
    for (const key of ["toolName", "slug"]) {
      if (!TOOL.test(c[key] ?? "") || RESERVED.includes(c[key])) errors.push(`${at}: ${key} "${c[key]}" is not a usable tool name`);
    }
    if (tools.has(c.toolName) || pack.actions.some((a) => a.name === c.toolName)) errors.push(`${at}: tool name ${c.toolName} is taken`);
    tools.add(c.toolName);
    if (typeof c.fallbackText !== "string" || !c.fallbackText.trim()) errors.push(`${at}: no fallbackText`);
    for (const [k, v] of Object.entries(c.style ?? {})) {
      const ok = STYLE[k] ? STYLE[k].includes(v)
        : k === "facts" ? typeof v === "boolean"
        : k === "columns" ? v === 1 || v === 2
        : k === "maxSelect" ? Number.isInteger(v) && v >= 2 && v <= 8
        : k === "radius" ? Number.isInteger(v) && v >= 0 && v <= 24
        : k === "accent" ? /^#[0-9a-fA-F]{6}$/.test(v) : false;
      if (!ok) errors.push(`${at}: style.${k} = ${JSON.stringify(v)} is not allowed`);
    }
    const sel = c.onSelect ?? {};
    for (const k of Object.keys(sel)) if (!["path", "action", "args", "say"].includes(k)) errors.push(`${at}: onSelect.${k} is not allowed`);
    if (sel.action && !pack.actions.some((a) => a.name === sel.action)) errors.push(`${at}: onSelect.action ${sel.action} is not one of the pack's actions`);
    if (sel.path !== undefined) {
      const first = String(sel.path).split("?")[0].split("/").filter(Boolean)[0] ?? "";
      if (!screens.includes(first)) errors.push(`${at}: onSelect.path "${sel.path}" opens no screen of this pack`);
    }
  }
  const promptPath = join(dir, "prompt.md");
  if (!existsSync(promptPath)) errors.push(`agent-setup/${pack.id}/prompt.md is missing`);
  else {
    const prompt = readFileSync(promptPath, "utf8");
    for (const name of [...pack.actions.map((a) => a.name), ...tools, "schedule_callback"]) {
      if (!prompt.includes(`\`${name}\``)) errors.push(`prompt.md never names \`${name}\``);
    }
    if (!prompt.includes(pack.agentName)) errors.push(`prompt.md never names ${pack.agentName}`);
  }
  const help = pack.agents.help;
  const helpPrompt = join(dir, "help-prompt.md");
  if (!existsSync(helpPrompt)) errors.push(`agent-setup/${pack.id}/help-prompt.md is missing`);
  else {
    const text = readFileSync(helpPrompt, "utf8");
    for (const must of [help.name, "`schedule_callback`", "`open_screen`"]) if (!text.includes(must)) errors.push(`help-prompt.md never names ${must}`);
  }
  const helpSettings = join(dir, "help-settings.md");
  if (!existsSync(helpSettings)) errors.push(`agent-setup/${pack.id}/help-settings.md is missing`);
  else {
    const text = readFileSync(helpSettings, "utf8");
    for (const must of [help.name, help.callName, pack.identifierField]) if (!text.includes(must)) errors.push(`help-settings.md never says "${must}"`);
  }
  const settingsPath = join(dir, "settings.md");
  if (!existsSync(settingsPath)) errors.push(`agent-setup/${pack.id}/settings.md is missing`);
  else {
    const settings = readFileSync(settingsPath, "utf8");
    for (const must of [pack.identifierField, pack.call.callName, pack.agentName]) {
      if (!settings.includes(must)) errors.push(`settings.md never says "${must}"`);
    }
  }
  return errors;
}

const files = readdirSync(dir).filter((f) => f.endsWith(".json") && f !== "usecase.schema.json").sort();
let failed = 0;
for (const file of files) {
  const pack = JSON.parse(readFileSync(join(dir, file), "utf8"));
  const errors = validate(pack, schema);
  if (errors.length === 0) errors.push(...crossCheck(pack, file));
  if (process.argv.includes("--agent-setup")) {
    const actionsPath = join(root, "agent-setup", pack.id, "actions.json");
    if (!existsSync(actionsPath)) errors.push(`agent-setup/${pack.id}/actions.json is missing (node scripts/build-agent-setup.mjs)`);
    else if (readFileSync(actionsPath, "utf8") !== actionsJsonFor(pack)) {
      errors.push(`agent-setup/${pack.id}/actions.json differs from the pack (node scripts/build-agent-setup.mjs)`);
    }
    if (errors.length === 0) errors.push(...checkKit(pack));
  }
  if (errors.length) {
    failed++;
    console.error(`✗ ${file}`);
    for (const e of errors) console.error(`  ${e}`);
  } else {
    console.log(`✓ ${file}`);
  }
}
if (!files.includes("health-coach.json")) {
  failed++;
  console.error("✗ health-coach.json (the default pack) is missing");
}
process.exit(failed ? 1 : 0);

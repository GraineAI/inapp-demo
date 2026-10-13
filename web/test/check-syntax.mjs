#!/usr/bin/env node
// `node --check` on every script in web/ — no dependencies. Exits 1 when any file does not parse.
import { readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const files = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    if (name === "node_modules" || name.startsWith(".")) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path);
    else if (/\.m?js$/.test(name)) files.push(path);
  }
})(root);

let failed = 0;
for (const file of files.sort()) {
  const r = spawnSync(process.execPath, ["--check", file], { encoding: "utf8" });
  if (r.status === 0) {
    console.log(`ok   ${relative(root, file)}`);
  } else {
    failed += 1;
    console.error(`FAIL ${relative(root, file)}\n${r.stderr}`);
  }
}
console.log(`${files.length - failed}/${files.length} files parse`);
process.exit(failed ? 1 : 0);

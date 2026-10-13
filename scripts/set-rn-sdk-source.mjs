#!/usr/bin/env node
/**
 * Points react-native/package.json at the published SDKs, or at local tarballs.
 *
 *   node scripts/set-rn-sdk-source.mjs published
 *   node scripts/set-rn-sdk-source.mjs local <inapp.tgz> <calls.tgz>
 *
 * Used by use-local-sdks.sh; run `npm install` in react-native/ afterwards.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { RN_SDK, RN_CALLS } from "./sdk-coordinates.mjs";

const app = join(dirname(fileURLToPath(import.meta.url)), "..", "react-native");
const file = join(app, "package.json");
const pkg = JSON.parse(readFileSync(file, "utf8"));
const [mode, sdkTgz, callsTgz] = process.argv.slice(2);

if (mode === "published") {
  pkg.dependencies[RN_SDK.name] = RN_SDK.version;
  pkg.dependencies[RN_CALLS.name] = RN_CALLS.version;
} else if (mode === "local" && sdkTgz && callsTgz) {
  pkg.dependencies[RN_SDK.name] = `file:${relative(app, sdkTgz)}`;
  pkg.dependencies[RN_CALLS.name] = `file:${relative(app, callsTgz)}`;
} else {
  console.error("usage: set-rn-sdk-source.mjs published | local <inapp.tgz> <calls.tgz>");
  process.exit(2);
}
writeFileSync(file, `${JSON.stringify(pkg, null, 2)}\n`);
console.log(`react-native/package.json → ${pkg.dependencies[RN_SDK.name]}, ${pkg.dependencies[RN_CALLS.name]}`);

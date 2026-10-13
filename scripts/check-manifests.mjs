#!/usr/bin/env node
/**
 * The committed manifests must name the PUBLISHED SDKs. Run before committing: a manifest left
 * pointing at local tarballs or a path override builds on one machine only.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { RN_SDK, RN_CALLS, FLUTTER_SDK } from "./sdk-coordinates.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const problems = [];

const rnFile = join(root, "react-native", "package.json");
if (existsSync(rnFile)) {
  const deps = JSON.parse(readFileSync(rnFile, "utf8")).dependencies ?? {};
  for (const { name, version } of [RN_SDK, RN_CALLS]) {
    if (deps[name] !== version) problems.push(`react-native/package.json: ${name} is "${deps[name]}", expected "${version}" (scripts/use-local-sdks.sh published)`);
  }
}

const pubspec = join(root, "flutter", "pubspec.yaml");
if (existsSync(pubspec)) {
  const text = readFileSync(pubspec, "utf8");
  const line = text.split("\n").find((l) => l.trim().startsWith(`${FLUTTER_SDK.name}:`));
  if (!line || line.split(":")[1].trim() !== FLUTTER_SDK.constraint) {
    problems.push(`flutter/pubspec.yaml: ${FLUTTER_SDK.name} must be ${FLUTTER_SDK.constraint}`);
  }
  if (/^dependency_overrides:/m.test(text)) problems.push("flutter/pubspec.yaml: no dependency_overrides in the committed file (use pubspec_overrides.yaml)");
}

if (problems.length) {
  for (const p of problems) console.error(`✗ ${p}`);
  process.exit(1);
}
console.log("✓ manifests name the published SDKs");

#!/usr/bin/env bash
# Build the demo apps against SDK sources on this machine, before those SDK versions are published —
# or switch back to the published packages.
#
#   scripts/use-local-sdks.sh [local|published] [rn|flutter|all]      (default: local all)
#
# local     React Native: builds @graineai/inapp-react-native and @graineai/inapp-react-native-calls from
#           the SDK sources into react-native/.local-sdk/*.tgz (the sources are copied first; nothing is
#           written into the SDK checkout) and points react-native/package.json at those tarballs.
#           Flutter: writes flutter/pubspec_overrides.yaml with a path override to the Flutter SDK.
# published Puts the published coordinates back (react-native/package.json) and removes the override.
#           Run it before committing; scripts/check-manifests.mjs fails while a manifest is local.
#
# Where the SDK sources are (defaults: beside this repository):
#   GRAINE_SDK_RN_DIR       folder holding inapp/ and inapp-calls/   (default ../sdk-ts/sdk)
#   GRAINE_SDK_FLUTTER_DIR  the graine_inapp package                 (default ../sdk-flutter/sdk/flutter)
#
# Then run `npm install` in react-native/ and `flutter pub get` in flutter/.
# Note: the local React Native calls package carries whatever native sources the SDK checkout has; a
# native build with it needs the SDK's own vendoring step. JavaScript checks (tsc, jest, expo config,
# prebuild) do not.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-local}"
TARGET="${2:-all}"
RN_SRC="${GRAINE_SDK_RN_DIR:-$ROOT/../sdk-ts/sdk}"
FLUTTER_SRC="${GRAINE_SDK_FLUTTER_DIR:-$ROOT/../sdk-flutter/sdk/flutter}"

rn_local() {
  local work="$ROOT/react-native/.local-sdk"
  [ -d "$RN_SRC/inapp" ] && [ -d "$RN_SRC/inapp-calls" ] || { echo "No SDK sources at $RN_SRC (set GRAINE_SDK_RN_DIR)"; exit 1; }
  [ -x "$(command -v rsync)" ] || { echo "rsync is required"; exit 1; }
  [ -f "$RN_SRC/inapp/node_modules/typescript/bin/tsc" ] || { echo "Install the SDK's dev dependencies first (npm install in $RN_SRC/inapp)"; exit 1; }
  rm -rf "$work"
  mkdir -p "$work/src"
  rsync -a --exclude node_modules --exclude dist --exclude test "$RN_SRC/inapp/" "$work/src/inapp/"
  rsync -a --exclude node_modules --exclude dist --exclude test "$RN_SRC/inapp-calls/" "$work/src/inapp-calls/"
  # The SDK's own compiler and type packages, read where they are.
  ln -s "$RN_SRC/inapp/node_modules" "$work/src/inapp/node_modules"
  (cd "$work/src/inapp" && node node_modules/typescript/bin/tsc -p tsconfig.build.json)
  (cd "$work/src/inapp-calls" && node ../inapp/node_modules/typescript/bin/tsc -p tsconfig.build.json)
  (cd "$work/src/inapp" && npm pack --silent --ignore-scripts --pack-destination "$work" >/dev/null)
  (cd "$work/src/inapp-calls" && npm pack --silent --ignore-scripts --pack-destination "$work" >/dev/null)
  rm -rf "$work/src"
  node "$ROOT/scripts/set-rn-sdk-source.mjs" local "$(ls "$work"/graineai-inapp-react-native-[0-9]*.tgz)" "$(ls "$work"/graineai-inapp-react-native-calls-*.tgz)"
  echo "React Native: now run 'npm install' in react-native/"
}

rn_published() {
  node "$ROOT/scripts/set-rn-sdk-source.mjs" published
  rm -rf "$ROOT/react-native/.local-sdk"
  echo "React Native: now run 'npm install' in react-native/ (needs the published versions)"
}

flutter_local() {
  [ -f "$FLUTTER_SRC/pubspec.yaml" ] || { echo "No Flutter SDK at $FLUTTER_SRC (set GRAINE_SDK_FLUTTER_DIR)"; exit 1; }
  cat > "$ROOT/flutter/pubspec_overrides.yaml" <<EOF
# Written by scripts/use-local-sdks.sh — never committed. Delete it (or run the script with
# "published") to build against graine_inapp from pub.dev.
dependency_overrides:
  graine_inapp:
    path: $(cd "$FLUTTER_SRC" && pwd)
EOF
  echo "Flutter: graine_inapp → $FLUTTER_SRC; now run 'flutter pub get' in flutter/"
}

flutter_published() {
  rm -f "$ROOT/flutter/pubspec_overrides.yaml"
  echo "Flutter: graine_inapp from pub.dev; now run 'flutter pub get' in flutter/"
}

case "$MODE:$TARGET" in
  local:rn) rn_local ;;
  local:flutter) flutter_local ;;
  local:all) rn_local; flutter_local ;;
  published:rn) rn_published ;;
  published:flutter) flutter_published ;;
  published:all) rn_published; flutter_published ;;
  *) echo "usage: $0 [local|published] [rn|flutter|all]"; exit 2 ;;
esac

#!/usr/bin/env bash
# Flutter bundles assets from inside its own package, so the shared packs are copied into
# flutter/assets/usecases/. Run after editing a pack; flutter/test/usecases_test.dart fails while they differ.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/flutter/assets/usecases"
rm -f "$ROOT/flutter/assets/usecases/"*.json
for f in "$ROOT"/usecases/*.json; do
  [ "$(basename "$f")" = "usecase.schema.json" ] && continue
  cp "$f" "$ROOT/flutter/assets/usecases/"
done
ls "$ROOT/flutter/assets/usecases"

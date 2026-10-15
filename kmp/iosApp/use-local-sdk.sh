#!/usr/bin/env bash
# Generate iosAppLocal.xcodeproj: the demo built against an iOS SDK checkout on this machine, for an
# iOS SDK version that is not tagged yet (a tagged one comes from Swift Package Manager: iosApp.xcodeproj). The
# Kotlin Multiplatform SDK follows graine.kmpSdkDir in ../local.properties either way.
#
#   ./use-local-sdk.sh                      # iOS SDK sources at ../../../graine-sdk/ios: a checkout of inapp-ios beside
#                                           # this repository, next to graine-sdk/kmp and graine-sdk/android (README.md)
#   GRAINE_IOS_SDK_PATH=/path/to/inapp-ios ./use-local-sdk.sh
#
# Then open iosAppLocal.xcodeproj. It is git-ignored; iosApp.xcodeproj (the published packages) is untouched.
set -euo pipefail
cd "$(dirname "$0")"
command -v xcodegen >/dev/null || { echo "XcodeGen is required: brew install xcodegen"; exit 1; }
SDK="${GRAINE_IOS_SDK_PATH:-../../../graine-sdk/ios}"
[ -f "$SDK/Package.swift" ] || { echo "No GraineInApp package at $SDK (set GRAINE_IOS_SDK_PATH)"; exit 1; }
grep -q '^graine.kmpSdkDir=' ../local.properties 2>/dev/null || echo "Note: set graine.kmpSdkDir in ../local.properties to build the Kotlin side from sources too."
GRAINE_IOS_SDK_PATH="$(cd "$SDK" && pwd)" xcodegen generate --spec project.local.yml --quiet
echo "Open iosAppLocal.xcodeproj (GraineInApp from $SDK)."

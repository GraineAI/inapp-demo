#!/usr/bin/env bash
# Generate iosAppLocal.xcodeproj: the demo built against SDK sources on this machine, for SDK versions
# that are not published yet — GraineInApp from an iOS SDK checkout, and the Kotlin Multiplatform SDK
# from graine.kmpSdkDir in ../local.properties (set it there).
#
#   ./use-local-sdk.sh                      # iOS SDK sources at ../../../sdk-kmp/sdk/ios
#   GRAINE_IOS_SDK_PATH=/path/to/inapp-ios ./use-local-sdk.sh
#
# Then open iosAppLocal.xcodeproj. It is git-ignored; iosApp.xcodeproj (the published packages) is untouched.
set -euo pipefail
cd "$(dirname "$0")"
command -v xcodegen >/dev/null || { echo "XcodeGen is required: brew install xcodegen"; exit 1; }
SDK="${GRAINE_IOS_SDK_PATH:-../../../sdk-kmp/sdk/ios}"
[ -f "$SDK/Package.swift" ] || { echo "No GraineInApp package at $SDK (set GRAINE_IOS_SDK_PATH)"; exit 1; }
grep -q '^graine.kmpSdkDir=' ../local.properties 2>/dev/null || echo "Note: set graine.kmpSdkDir in ../local.properties to build the Kotlin side from sources too."
GRAINE_IOS_SDK_PATH="$(cd "$SDK" && pwd)" xcodegen generate --spec project.local.yml --quiet
echo "Open iosAppLocal.xcodeproj (GraineInApp from $SDK)."

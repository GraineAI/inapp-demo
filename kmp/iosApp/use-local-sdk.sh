#!/usr/bin/env bash
# Generate iosAppLocal.xcodeproj: the demo built against the iOS SDK's sources on this machine, for a version that
# is not tagged yet (a tagged one comes from Swift Package Manager: iosApp.xcodeproj). Both packages come from the
# same source tree — GraineInApp and its WebRTC module GraineInAppRTC, side by side as the SDK keeps them (sdk/ios and
# sdk/ios-rtc, the module taking GraineInApp from ../ios). The Kotlin Multiplatform SDK follows graine.kmpSdkDir in
# ../local.properties either way.
#
#   ./use-local-sdk.sh                      # sources at ../../../graine-sdk/ios and ../../../graine-sdk/ios-rtc
#   GRAINE_IOS_SDK_PATH=/path/to/sdk/ios ./use-local-sdk.sh                  # the WebRTC module beside it, in ios-rtc
#   GRAINE_IOS_SDK_PATH=… GRAINE_IOS_RTC_SDK_PATH=… ./use-local-sdk.sh
#
# Then open iosAppLocal.xcodeproj. It is git-ignored; iosApp.xcodeproj (the published packages) is untouched.
set -euo pipefail
cd "$(dirname "$0")"
command -v xcodegen >/dev/null || { echo "XcodeGen is required: brew install xcodegen"; exit 1; }
SDK="${GRAINE_IOS_SDK_PATH:-../../../graine-sdk/ios}"
[ -f "$SDK/Package.swift" ] || { echo "No GraineInApp package at $SDK (set GRAINE_IOS_SDK_PATH)"; exit 1; }
SDK="$(cd "$SDK" && pwd)"
RTC="${GRAINE_IOS_RTC_SDK_PATH:-$SDK/../ios-rtc}"
[ -f "$RTC/Package.swift" ] || { echo "No GraineInAppRTC package at $RTC (set GRAINE_IOS_RTC_SDK_PATH)"; exit 1; }
RTC="$(cd "$RTC" && pwd)"
grep -q '^graine.kmpSdkDir=' ../local.properties 2>/dev/null || [ -n "${GRAINE_KMP_SDK_DIR:-}" ] ||
  echo "Note: set graine.kmpSdkDir in ../local.properties (or GRAINE_KMP_SDK_DIR) to build the Kotlin side from sources too."
GRAINE_IOS_SDK_PATH="$SDK" GRAINE_IOS_RTC_SDK_PATH="$RTC" xcodegen generate --spec project.local.yml --quiet
echo "Open iosAppLocal.xcodeproj (GraineInApp from $SDK, GraineInAppRTC from $RTC)."

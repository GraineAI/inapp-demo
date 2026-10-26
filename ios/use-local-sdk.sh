#!/usr/bin/env bash
# Generate GraineDemoLocal.xcodeproj: the demo built against the iOS SDK's sources on this machine, for an SDK version
# that is not tagged yet — GraineInApp and its WebRTC module GraineInAppRTC, both from sources of the same version:
# checkouts of https://github.com/GraineAI/inapp-ios and https://github.com/GraineAI/inapp-ios-rtc side by side, or
# the SDK's source tree (ios and ios-rtc side by side).
#
#   ./use-local-sdk.sh                      # ../../inapp-ios and ../../inapp-ios-rtc: checkouts beside this repository
#   GRAINE_IOS_SDK_PATH=/path/to/inapp-ios ./use-local-sdk.sh       # the WebRTC module beside it (inapp-ios-rtc or ios-rtc)
#   GRAINE_IOS_SDK_PATH=… GRAINE_IOS_RTC_SDK_PATH=… ./use-local-sdk.sh
#
# Then open GraineDemoLocal.xcodeproj. It is git-ignored; GraineDemo.xcodeproj (the published packages) is untouched.
set -euo pipefail
cd "$(dirname "$0")"
command -v xcodegen >/dev/null || { echo "XcodeGen is required: brew install xcodegen"; exit 1; }
SDK="${GRAINE_IOS_SDK_PATH:-../../inapp-ios}"
[ -f "$SDK/Package.swift" ] || { echo "No GraineInApp package at $SDK (set GRAINE_IOS_SDK_PATH)"; exit 1; }
SDK="$(cd "$SDK" && pwd)"
RTC="${GRAINE_IOS_RTC_SDK_PATH:-}"
if [ -z "$RTC" ]; then
  for candidate in "$SDK/../inapp-ios-rtc" "$SDK/../ios-rtc"; do
    if [ -f "$candidate/Package.swift" ]; then RTC="$candidate"; break; fi
  done
fi
[ -n "$RTC" ] && [ -f "$RTC/Package.swift" ] || {
  echo "No GraineInAppRTC package beside $SDK (clone https://github.com/GraineAI/inapp-ios-rtc there, or set GRAINE_IOS_RTC_SDK_PATH)"
  exit 1
}
RTC="$(cd "$RTC" && pwd)"
GRAINE_IOS_SDK_PATH="$SDK" GRAINE_IOS_RTC_SDK_PATH="$RTC" xcodegen generate --spec project.local.yml --quiet
echo "Open GraineDemoLocal.xcodeproj (GraineInApp from $SDK, GraineInAppRTC from $RTC)."

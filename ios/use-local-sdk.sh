#!/usr/bin/env bash
# Generate GraineDemoLocal.xcodeproj: the demo built against GraineInApp sources on this machine
# (an inapp-ios checkout), for an SDK version that is not published yet.
#
#   ./use-local-sdk.sh                      # sources at ../../sdk-ios/sdk/ios
#   GRAINE_IOS_SDK_PATH=/path/to/inapp-ios ./use-local-sdk.sh
#
# Then open GraineDemoLocal.xcodeproj. It is git-ignored; GraineDemo.xcodeproj (the published package) is untouched.
set -euo pipefail
cd "$(dirname "$0")"
command -v xcodegen >/dev/null || { echo "XcodeGen is required: brew install xcodegen"; exit 1; }
SDK="${GRAINE_IOS_SDK_PATH:-../../sdk-ios/sdk/ios}"
[ -f "$SDK/Package.swift" ] || { echo "No GraineInApp package at $SDK (set GRAINE_IOS_SDK_PATH)"; exit 1; }
GRAINE_IOS_SDK_PATH="$(cd "$SDK" && pwd)" xcodegen generate --spec project.local.yml --quiet
echo "Open GraineDemoLocal.xcodeproj (GraineInApp from $SDK)."

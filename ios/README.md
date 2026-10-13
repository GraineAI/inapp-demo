# Graine Demo — iOS

A SwiftUI app (iOS 16+) that shows every in-app feature of the Graine SDK: the assistant in a voice
bar, the chat window with components, phone-style calls on CallKit with VoIP push, identity and memory,
calls the user books, and screen context with actions. Every domain word comes from a use-case pack in
`../usecases/` (care coach by default, or banking or shopping — switch on the sign-in screen). All people
and records are fictional.

## Set up

1. Copy `Config.example.xcconfig` to `Config.xcconfig` (git-ignored) and fill in:
   - `GRAINE_PUBLISHABLE_KEY` — the agent's publishable key (Agent → Embed & Widgets). Add `app://` to
     the agent's **Allowed domains**.
   - `DEVELOPMENT_TEAM` and `PRODUCT_BUNDLE_IDENTIFIER` — your team and bundle id, for running on a device.
   - `GRAINE_BASE_URL` — leave as is (`https://www.graine.ai`) unless you were given another server.
   - `GRAINE_HELP_PUBLISHABLE_KEY` — optional: a second agent (the pack's help desk) for the **Help** tab.
     Without it, the Help tab says how to add one.
2. Recreate the agent from `../agent-setup/<pack>/` so its identity field, actions and call settings
   match the pack you demo.
3. Open `GraineDemo.xcodeproj` and run the **GraineDemo** scheme.

The project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`, then `xcodegen generate`); the generated project is committed, so XcodeGen is
needed only to change it.

**Before the SDK version is published**, build against SDK sources on your machine:

```sh
./use-local-sdk.sh                                  # sources at ../../sdk-ios/sdk/ios
GRAINE_IOS_SDK_PATH=/path/to/inapp-ios ./use-local-sdk.sh
```

and open the generated `GraineDemoLocal.xcodeproj` (git-ignored). `GraineDemo.xcodeproj` keeps the
published package (`https://github.com/GraineAI/inapp-ios`, from 0.7.0).

## What is where

| | |
|---|---|
| `GraineDemo/Service/GraineService.swift` | The one file that calls the SDK (identify, memory, screen context, actions, scheduling, voice, chat, calls) |
| `GraineDemo/Domain/` | The pack model, the Items list, call and booking presentation — no SDK rules re-implemented |
| `GraineDemo/App/AppModel.swift` | The app's state and the agent's actions, run by effect in the pack's words |
| `GraineDemo/Screens/` | Sign in · Home · Chat · Items · Scheduled calls · Memory · Profile · Call activity |
| `GraineDemoTests/` | Unit tests against a fake SDK client (`xcodebuild test`, any iOS 16+ simulator) |

## Two agents in one app

Home, Chat and the Items screen talk to the main agent; the **Help** tab talks to the help desk — its own
chat, voice bar and phone-style call (`startCall(…, agent: "help")`). The user signs in once and both agents
know who they are; **Calls** lists the calls booked with either agent, each with the agent's name, and the
booking sheet asks which agent should call. `GraineService.swift` configures the help desk with
`GraineInApp.configure(_:name: "help")`.

## Calls on a real device

Phone-style calls need a device (the simulator has no CallKit or VoIP push).

1. Signing & Capabilities already declares **Push Notifications** and **Background Modes** (audio, voice
   over IP); select your team.
2. In the dashboard, under **Workspace settings → In-app calling**, add the iOS app: your bundle id, team
   id, key id and the APNs `.p8` key. Graine then registers this device by itself when the app starts.
3. Sign in as one of the pack's users, open **Scheduled calls** and book a call a few minutes ahead (the
   quick times help). Lock the phone: it rings on the lock screen with the pack's call name. Answer, talk,
   hang up; **Call activity** shows what the SDK reported, and the call's status chip changes.
4. **Call** on Home places a call the other way: the user calls the assistant.

## Offline demo

Every screen with no workspace and no key — for screenshots, or a demo without a network. From the
repository root, start the stand-in for Graine (it plays the agent from the use-case pack):

```sh
node scripts/offline-server.mjs        # http://127.0.0.1:8790; PACK=banking-support for another pack
```

Run the app once in a simulator, then launch it against the stand-in:

```sh
xcrun simctl launch booted com.example.grainedemo -GraineOffline http://127.0.0.1:8790
```

It answers what the SDK asks — the agent's design and settings, booked calls, memories — and plays the chat
and the voice bar; nothing is real (no model, no audio). More launch arguments sign in and open a screen, for
screenshots — `-GraineDemoUser MRN-000981 -GraineDemoOpen chat -GraineDemoSay "Book a check-in call"`; the
full list is at the top of `OfflineDemo` in `GraineDemo/Domain/AppConfig.swift`. A simulator has no CallKit:
calls cannot ring or connect there, and a ring shows as missed. The pictures in
[`../docs/screenshots/`](../docs/screenshots/) were taken this way.

## Memory, in two minutes

Sign in with memory on, tell the assistant one of the pack's `memoryExamples` (`tell`) in the chat, start a
new chat and ask the `ask` question: it answers from memory, and the **Memory** tab lists it. Sign in as
another user: nothing is shared. Switch back: it is there. **Erase everything** removes it.

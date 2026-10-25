# Graine Demo — iOS

A SwiftUI app (iOS 16+) that shows every in-app feature of the Graine SDK: the assistant in a voice
bar, the chat window with components, phone-style calls on CallKit with VoIP push, identity and memory,
past sessions, calls the user books, and screen context with actions. Every domain word comes from a use-case pack in
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
2. Recreate the agent from `../agent-setup/<pack>/` so its identity field, memory, actions and call
   settings match the pack you demo.
3. Open `GraineDemo.xcodeproj` and run the **GraineDemo** scheme.

The project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`, then `xcodegen generate`); the generated project is committed, so XcodeGen is
needed only to change it.

**The SDK comes from Swift Package Manager**: `GraineDemo.xcodeproj` takes `GraineInApp` from
`https://github.com/GraineAI/inapp-ios`, from 0.14.3 (a public repository), and Xcode fetches the package on
first open. (The SDK is on CocoaPods too, as `GraineInApp`; this demo uses Swift Package Manager.)

**To build against SDK sources on your machine instead** (for example a fix that is not released yet), use the
local-SDK switch:

```sh
./use-local-sdk.sh                                  # sources at ../../inapp-ios: a checkout beside this repository
GRAINE_IOS_SDK_PATH=/path/to/inapp-ios ./use-local-sdk.sh
```

and open the generated `GraineDemoLocal.xcodeproj` (git-ignored; it needs XcodeGen). `GraineDemo.xcodeproj`
keeps the published package.

## What is where

| | |
|---|---|
| `GraineDemo/Service/GraineService.swift` | The one file that calls the SDK (identify, memory, screen context, actions, scheduling, voice, chat, calls, past sessions) |
| `GraineDemo/Service/PastSessionsSdk.swift` | Past sessions read from the SDK (`chat.pastSessions`, `sessionTranscript(id:)`) into the app's own state |
| `GraineDemo/Domain/` | The pack model, the Items list, call presentation, how past sessions read — no SDK rules re-implemented |
| `GraineDemo/App/AppModel.swift` | The app's state and the agent's actions, run by effect in the pack's words |
| `GraineDemo/Screens/` | Sign in · Home · Chat · Items · Scheduled calls · Memory · Profile · Call activity · Past sessions |
| `GraineDemoTests/` | Unit tests against a fake SDK client (`xcodebuild test`, any iOS 16+ simulator) |

## Two agents in one app

Home, Chat and the Items screen talk to the main agent; the **Help** tab talks to the help desk — its own
chat, voice bar and phone-style call (`startCall(…, agent: "help")`). The user signs in once and both agents
know who they are; **Calls** lists the calls booked with either agent, each with the agent's name. Each
agent is booked with its own button: the pack's booking button on **Calls** books the main agent, the help
desk's on **Help** books the help desk. `GraineService.swift` configures the help desk with
`GraineInApp.configure(_:name: "help")`.

## Booking a call

Booking is the SDK's own booking sheet — the day, the time, the phone's time zone and an optional note,
drawn in the agent's design — so the app has no booking screen of its own. The pack's booking button
(`call.bookLabel`, or the help desk's) calls `presentBooking(reason:)` with the pack's first reason, which
starts the sheet's "What's it about?". The button is enabled while the agent's `bookingAvailable` is true;
otherwise it stays on screen, disabled, with "Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID." After a booking, the
**Calls** list is read again; there each call has its status and, while it can be, **Cancel**.

## Past sessions

**Past sessions**, at the top of the chat (Chat, and the help desk's chat on **Help**), lists the signed-in user's
earlier sessions with that agent, newest first: the date and time, chat, voice or call (and who started it), how
long, how it ended, how many messages, the first words, and the session id with **Copy**. The SDK has no screen for
it: the app draws it from `chat.pastSessions` (`PastSessionsView.swift`), calls `refreshPastSessions()` when it opens
and on pull to refresh, and `loadMorePastSessions()` at the end of the list, and says when the list is empty,
loading or refused ("Sign in to see past sessions", "Not available on this device"). A tap opens
`chat.sessionTranscript(id:)`, read only, each line with its time. The session open now is never listed: a chat
closes 15 seconds after its screen does (the demo sets `GraineConfig(chatCloseDelaySeconds: 15)`, the SDK's default)
and is listed once it has closed.

## Calls on a real device

Phone-style calls need a device (the simulator has no CallKit or VoIP push).

1. Signing & Capabilities already declares **Push Notifications** and **Background Modes** (audio, voice
   over IP); select your team.
2. In the dashboard, under **Workspace settings → In-app calling**, add the iOS app: your bundle id, team
   id, key id and the APNs `.p8` key. Graine then registers this device by itself when the app starts.
3. Sign in as one of the pack's users, open **Calls**, tap the pack's booking button and, in the booking
   sheet, book a call a few minutes ahead. Lock the phone: it rings on the lock screen with the pack's call
   name. Answer, talk, hang up; **Call activity** shows what the SDK reported, and the call's status chip
   changes.
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

It answers what the SDK asks — the agent's design and settings, booked calls, memories, past sessions — and plays the chat
and the voice bar; nothing is real (no model, no audio). More launch arguments sign in and open a screen, for
screenshots — `-GraineDemoUser MRN-000981 -GraineDemoOpen chat -GraineDemoSay "Book a check-in call"`, or
`-GraineDemoOpen book` for the booking sheet, `past_sessions` or `transcript` for Past sessions; the full list is at the top of `OfflineDemo` in
`GraineDemo/Domain/AppConfig.swift`. A simulator has no CallKit:
calls cannot ring or connect there, and a ring shows as missed. The pictures in
[`../docs/screenshots/`](../docs/screenshots/) were taken this way.

## Memory, in two minutes

The app identifies the user with the pack's field (`patient_id`, `customer_id` or `shopper_id`) before any chat
or call opens. The same id also goes under `user_id`, so the user is recognised whether the agent's
**Who is the user?** is the pack's field or **User ID** (the dashboard's usual choice); with any other field,
every conversation is a stranger. At the start of each chat, Talk or call, what the agent remembers about the
user goes into what the model reads, after its prompt. What is said is saved every few messages and when the
conversation ends, ready about a minute later — never contact details, ID or card numbers, codes or medical
details. More: the root README's [Memory QA script](../README.md#memory-qa-script).

Sign in with memory on, tell the assistant the pack's two `memoryExamples` (`tell`) in the chat, close the app
and wait about a minute. Open it again and sign in as the same user: the chat continues where it was. Tap **New
conversation** in the chat's header and ask "What do you remember about me?": it answers with both, and the
**Memory** tab lists them (so does the dashboard's Agent → Memory → **People**). Sign in as another user: nothing
is shared. Switch back: it is there. **Erase everything**, or erasing the user on the dashboard's **People** tab,
removes it; memory off on the sign-in screen or the **Memory** tab opts the user out.

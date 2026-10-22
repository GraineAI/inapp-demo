# Use-case packs

Graine is general purpose. The demo apps contain no domain code: every word that makes an app a care
coach, a banking assistant or a shopping assistant comes from a **use-case pack** in this folder. Every
platform (React Native, Flutter, iOS, Android, website) bundles these files as they are and draws the same
screens from them.

| File | Agent | Users | List screen | Calls |
|---|---|---|---|---|
| `health-coach.json` (**default**) | Asha, care coach | patients (`patient_id`) | Medicines | Check-in calls |
| `banking-support.json` | Kabir, account assistant | customers (`customer_id`) | Cards | Callbacks |
| `retail-shopping.json` | Tara, shopping assistant | shoppers (`shopper_id`) | Orders | Delivery calls |

`usecase.schema.json` is the schema (JSON Schema 2020-12). Check every pack with:

```bash
node scripts/check-usecases.mjs                 # schema + cross-checks
node scripts/check-usecases.mjs --agent-setup   # …and agent-setup/<pack>/actions.json matches the pack
node scripts/build-agent-setup.mjs              # rewrite agent-setup/<pack>/actions.json from the packs
```

All people, cards and orders are fictional.

## The screens (identical on every platform)

| Screen | Reported as `screen` | Title | Shows |
|---|---|---|---|
| Sign in | `sign_in` | Sign in | Pack picker (remembered on the device), the pack's `users`, the memory consent switch |
| Home | `home` | `home.title` | Memory chip (on / off), next scheduled call, `home.cards`, Talk (voice), Chat, Call (native call) |
| Chat | `chat` | `agentName` | The SDK's chat window, the pack's `starters` as chips |
| Items | `items.screenName` | `items.title` | The signed-in user's rows, their status chips and action buttons |
| Scheduled calls | `calls` | `call.title` | Calls with their status chips and Cancel; `call.bookLabel` opens the SDK's own booking sheet (the day, the time, the device's time zone, an optional note), its "What's it about?" starting with the first of `call.reasons` — enabled while the SDK's `bookingAvailable` is true, else disabled with "Booking isn't available for this agent." |
| Memory | `memory` | Memory | "What `agentName` remembers about you" (newest first), consent switch, erase everything |
| Profile | `profile` | Profile | Name, `identifierLabel`, traits, memory switch, sign out |
| Help | `help` | `agents.help.name` | The **help agent's** chat, voice and call (its own publishable key), and `agents.help.bookLabel`, which opens the SDK's booking sheet for the help agent, starting with the first of `agents.help.reasons`; says how to add the key when there is none |

Code names are generic: `ItemsScreen.tsx`, `items_screen.dart`, `ItemsView.swift`, `ItemsScreen.kt` — never
"Medicines".

**Screen context** sent on each screen (same keys everywhere):

| Screen | `fields` |
|---|---|
| `home` | `memory` = `on` / `off`, `next_call` = ISO-8601 time or `none` |
| items | one per row: `name` = row `id`, `label` = row `title`, `value` = row status (else subtitle), `status` = `filled` |
| `calls` | `upcoming` = number of upcoming calls |
| `memory` | `memory` = `on` / `off`, `remembered` = number of memories (never their text) |
| others | none |

## Two agents

Every pack has two agents (`agents.main`, `agents.help`), each its own agent and publishable key in the
dashboard. Home and Chat talk to the main agent; the Help screen talks to the help agent. One sign-in
identifies the user for both (identify once; sign-out signs out of both); the memory choice is shared; each
agent keeps its own conversation, actions and bookings, and the Scheduled calls screen lists both agents'
calls with the agent's name on each. One conversation at a time: opening one agent's chat or voice ends the
other's. `agentName` / `agentRole` / `starters` are the main agent's and stay for older readers; the checker
keeps them equal to `agents.main`.

## Signing in

```
identify({ [identifierField]: user.id, name: user.name, ...user.traits }, { memory: consent })
```

`identifierField` must equal the agent's **Memory → Settings → Who is the user? → Custom field** (also under
Embed & Widgets → Users & memory), or nobody is recognised and nothing is remembered. Sign out is
`identify(null)`. Switching pack signs the current user out first.

## Actions

The app registers **every** entry of `actions` under its `name` and runs it by its `effect`. The same list is
what the agent is given (`agent-setup/<pack>/actions.json`, generated from the pack). Three effects, with fixed
argument names:

| `effect` | Argument | The app | Answers |
|---|---|---|---|
| `open_screen` | `screen` (or `path`, which a component's tap sends) | Opens that screen. Accepts the generic keys `home`, `chat`, `items`, `calls`, `memory`, `profile` and `items.screenName`, case-insensitively | ok `Opened <title>.` · refused `There is no screen called "<x>". Screens: <list>.` |
| `open_item` | `item` | Opens the Items screen with that row expanded | ok `Showing <row title>.` |
| `set_status` | `item` | Sets that row's status chip to the action's `status` | ok `<row title>: <status>.` · ok `<row title> is already <status>.` · refused `<label> is not available for <row title>.` when the row does not list the action |

Finding the row named by `item` (only the signed-in user's rows; case-insensitive, trimmed):
1. a row whose `id` equals it; else 2. a row whose `title` equals it; else 3. the ONE row whose `title` or any
field `value` contains it (so `"7730"` and `"GD-55120"` work). Two or more → refused `Which one? <titles>.`;
none → refused `No <itemNoun> called "<x>". <items.title>: <titles>.`

## Booking a call

Booking is the SDK's own booking sheet — the day, the time, the device's time zone and an optional note, drawn
in the agent's design; the app has no booking screen. Each agent's booking button (`call.bookLabel`,
`agents.help.bookLabel`) is enabled while that agent's `bookingAvailable` is true and opens its sheet with the
first of its `reasons` (iOS `presentBooking(reason:)`, Android `presentBooking(activity, reason)`, Flutter
`GraineBooking.show(context, app:, reason:)`, React Native `openBooking({ reason })`, the website
`GraineAgent.openBooking({ reason })`); otherwise the button stays, disabled, with "Booking isn't available for
this agent." The rules and the refusals are the SDK's: the sheet offers only times the agent allows and says
any refusal (`outside_window`, `too_many_pending`…) in one line. After a booking the app reads its list again
and draws the statuses (`scheduled`, `ringing`, `answered`, `completed`, `declined`, `missed`, `expired`,
`noDevice`, `failed`, `cancelled`) as chips; Cancel is shown when the SDK says `canCancel`.

A row's `actions` are the `set_status` actions it offers as buttons and accepts from the agent; a button is
hidden while the row already has that status. Statuses live for the app session (they reset on restart) and
are per user.

## Fields

| Field | Meaning |
|---|---|
| `schemaVersion` | `1` |
| `id` | File name without `.json`; remembered on the device as the chosen pack |
| `appTitle`, `agentName`, `agentRole`, `tagline` | App bar title; the main agent's display name (match the dashboard) and what it is; the sign-in line |
| `agents` | `main` (`name`, `role`, `starters` — equal to the top-level fields) and `help` (`name`, `role`, `callName`, `placeReason`, `bookLabel`, `reasons`, `starters`), each its own agent in the dashboard |
| `notice` | Optional standing notice on Home and Chat |
| `accent` | Optional colour for the app's own chrome (the agent's look comes from the dashboard) |
| `identifierField`, `identifierLabel` | The identifying trait (match the dashboard) and its label on Profile |
| `userNoun` | `patient` / `customer` / `shopper` — "Choose a patient" |
| `users[]` | `id` (the identifier's value), `name`, `traits` (extra prompt variables, strings) |
| `home` | `title`, `cards[]` (`id`, `title`, `body`, `target`: a generic screen key) |
| `items` | `title`, `screenName`, `itemNoun`, `emptyText`, `rows[]` (`id`, `title`, `subtitle`, `status?`, `fields[]` of `name`/`label`/`value`, `actions[]`, `userIds?`) |
| `actions[]` | `name`, `effect`, `label` (button text), `description` (for the model), `status` (set_status), `args[]` (`name`, `type`, `required`, `description`, `enum?`) |
| `call` | `callName` (name on the incoming call — match the dashboard), `title` (the screen), `bookLabel` (the button that opens the SDK's booking sheet), `reasons[]` (2–4, at most 120 characters each; the first starts the sheet's "What's it about?", which the user may change), `quickTimes[]` (one-tap times, `{label, inMinutes}` or `{label, day: "today"/"tomorrow", time: "HH:MM"}`, for an app that draws its own booking screen; the SDK's sheet does not use them), `placeReason` (reason on a call the user places) |
| `memoryExamples[]` | `tell` / `ask` / `expect` — the memory QA script in the root README and the Memory screen's hint. Use facts memory keeps (a goal, a preference, a time to call): contact details, ID and card numbers, codes and medical details are removed before saving, so an example with one never passes |
| `starters[]` | Up to four chat chips, 40 characters each |

## Adding a pack

1. Copy `health-coach.json`, change `id` and the file name together, and rewrite the content. Keep the three
   effects and their argument names; name actions for the moment they are used (`freeze_card`, not `update`).
2. `node scripts/check-usecases.mjs`, then `node scripts/build-agent-setup.mjs`.
3. Write `agent-setup/<id>/` (prompt, components, settings) — copy the closest pack's folder.
4. Register the file in each app's pack list (React Native `src/usecases.ts`, Flutter `assets/usecases/` +
   `lib/usecases.dart`, and the iOS, Android and website equivalents).

# The Journey — three agents, set up in the dashboard

The Kotlin Multiplatform demo's **Journey** (sign-in screen → *The full journey*) is a whole in-app journey:
onboarding, a first consultation call (now or booked, with "We couldn't connect" and a chat version), a 7-day
plan the coach builds in the chat through the app's actions, check-ins, Today, Progress and privacy. It talks
to three agents. This folder holds everything they need; the words the app shows are in
`../../../usecases/health-coach.json` (`journey`), and the names below must match it.

| Agent | Kind | What it does | Its publishable key goes in |
|---|---|---|---|
| **Asha** — the coach | Prompt agent | The chat the app opens on; calls from the chat's call button; builds and changes the plan | `graine.publishableKey` (Android, `kmp/local.properties`) · `GRAINE_PUBLISHABLE_KEY` (iOS, `kmp/iosApp/Config.xcconfig`) |
| **Asha · Consultation** — the first call | Flow agent | The first call and its chat version; ends by saving what it learned | `graine.consultationPublishableKey` · `GRAINE_CONSULTATION_PUBLISHABLE_KEY` — the app configures it under the name `consultation` |
| **Help & support** | Prompt agent | The Help & support chat in the sidebar | `graine.helpPublishableKey` · `GRAINE_HELP_PUBLISHABLE_KEY` |

The coach uses the app's main key and support the help key, so on a build set up for the Journey the
feature tour talks to the same two agents.

## 1. Every one of the three — 3 minutes each

| Where | Setting | Value |
|---|---|---|
| Memory → Settings | Remembers users across chats and calls | **On** |
| Memory → Settings | Who is the user? | **User ID** — the app identifies the user as `user_id` |
| Memory → Settings | Devices per user | 2 (testing on several phones or emulators: raise it, or reset the person's devices in People) |
| Memory → Settings | Shared with | *Agents that share memory*: Asha and Asha · Consultation, so the coach remembers the consultation's conversation too (optional; the plan reads the consultation through the app either way) |
| Embed & Widgets → Install & domains | Allowed domains | **`app://`** |
| Embed & Widgets | What visitors get | **Call and chat** |
| Embed & Widgets | Publishable key | into the app (the table above) |
| Agent → Custom Variables | Defaults | `name` = `there`, `health_notes` = `none`, `language_style` = `English`, `who_called` = `customer`, `call_reason` = (empty) |
| Agent → Languages | Languages | **English (`en`)**, the default, and **Hindi (`hi`)**. The app sends the user's choice as `agent_language` (`en` or `hi`): each chat and call starts in it, and the user can switch by speaking. Hinglish uses the Hindi voice: the app sends `hi` with `language_style` = "Hinglish: Hindi and English mixed, in Latin script", which the prompts follow |

The app sends the profile as variables on every agent, at sign-up and at every launch: `gender`, `age`
(from the date of birth), `weight_kg`, `height_cm`, `goal`, `diet`, `health_notes`, `agent_language` and
`language_style` — nine of the twelve a conversation carries.

## 2. Asha, the coach — 3 more minutes

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Asha** (a prompt agent) |
| Agent → Prompt | Prompt | everything below the line in `coach-prompt.md` |
| Agent → Prompt | Welcome message | Hi {name}, I'm Asha, your health coach. Ask me anything about your plan. |
| Agent → its language model's settings | Max tokens (`max_tokens`) | about **4,000**, so a whole week fits in one `save_plan` (a few hundred cuts the week short) |
| Embed & Widgets → App actions | Import from JSON | **`coach-actions.json`**, then **Save** (an import that is not saved does nothing): `get_consultation`, `save_plan`, `get_plan`, `swap_item`, `log_check_in` |
| Calls & scheduling | Name on the incoming call | **Asha · Graine Care** |
| Calls & scheduling | Who may book a call | optional: *The agent, during a conversation* on, for follow-up calls it books |

Import replaces the agent's actions. On a build where the same agent also runs the feature tour's list
screen, add the tour's three actions (`../actions.json`) by hand after the import, and **Save**.

## 3. Asha · Consultation, the first call — 5 more minutes

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Kind and name | a **flow** agent, **Asha · Consultation** |
| Flow builder → Import / Export | Import JSON… | **`consultation-flow.json`** — its eight steps (routine, eating habits, likes and dislikes, allergies, challenges, activity and sleep, *Save and close*, *Goodbye*), its briefing and, ticked, its welcome message — then **Save** |
| Embed & Widgets → App actions | Import from JSON | **`consultation-actions.json`**, then **Save**: `save_consultation` |
| Embed & Widgets → Components | New component | the one in **`components.json`**: template **Quick actions**, the seven options as written, **Tool name** `show_challenges`, Look & feel → **Choose: Several** (the "Select all that apply" question in the chat), selected for this agent |
| Calls & scheduling | Who may book a call → Users, from your app | **On** (the app's "Choose a time" books with it) |
| Calls & scheduling | Upcoming calls per user · Days ahead · Minutes of notice | 3 · 14 · 5 |
| Calls & scheduling | Name on the incoming call | **Asha · Graine Care** |
| Calls & scheduling → How it rings | Seconds to ring · Ring again after · Rings per user per day | 45 · **10 minutes** · **3** — Graine rings once more 10 minutes after a missed ring, never after a decline |
| Calls & scheduling | Quiet hours | **22:00 – 07:00** |
| Agent → Calling Window | Hours | on, 09:00–21:30 every day: the picker offers 10:30, 13:00, 17:30 and 20:00 inside it |

The flow, step by step, and why it is built this way: `consultation-flow.md`. Every step can also run in
the chat: "Continue in chat" opens this agent's chat, which asks the same questions and saves the same way.
"Select all that apply" is that component: the app draws a choices or quick-actions component set to
**Several** as tickable chips with one **Done**, and answers with every pick at once (`chat.answerWidget`).

## 4. Help & support — 2 more minutes

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Help & support** (a prompt agent) |
| Agent → Prompt | Prompt | everything below the line in `support-prompt.md` |
| Agent → Prompt | Welcome message | Hi {name}, this is Help & support. What can I help with? |
| Embed & Widgets → What visitors get | | Call and chat (the Journey uses the chat) |

## 5. Ringing — once per workspace

**Workspace settings → In-app calling**: the iOS bundle ID you build with its APNs key, and the Android
package with its Firebase service-account JSON; wait for **Verified**. Without it "Start now" and the call
button still work (the user places those calls); a booked consultation cannot ring.

## 6. Put the keys in the app, and run it

`kmp/local.properties` (Android) and `kmp/iosApp/Config.xcconfig` (iOS), as in the table at the top; then
build and run (`../../../RUN-THE-DEMO.md`, *Journey*). On the sign-in screen: **The full journey → Start**.

## Check your set-up

- **"Start now" says the first-call agent is not set up** → `graine.consultationPublishableKey` /
  `GRAINE_CONSULTATION_PUBLISHABLE_KEY` is empty, or the app was not rebuilt.
- **The plan never arrives** → the coach's actions were imported but not saved, or its prompt lacks the
  `consultation_finished` part; on the progress screen, **Ask again**, or (debug builds) **Load sample plan**.
- **"Choose a time" has no times** → *Users, from your app* is off on the consultation agent, the user is
  not identified as **User ID**, or its calling window misses every preferred time (the picker then offers
  a few of the window's own).
- **The agent forgets the user** → **Who is the user?** is not **User ID** on that agent, or the user is
  past Devices per user.

# Settings — Asha, care coach (Graine Care)

Every dashboard setting this agent uses, where it lives, and its value. Values in **bold** must match the
apps exactly (they read them from `usecases/health-coach.json`).

## The agent

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Asha** |
| Agent → Prompt | Prompt | everything below the line in `prompt.md` |
| Agent → Prompt | Welcome message | Hello {name}, I'm Asha, your care coach. How are you feeling today? |
| Agent → Custom Variables | Defaults | `name` = `there`, `plan` = `your care`, `care_team` = `your care team`, `who_called` = `customer`, `call_reason` = (empty) |
| Agent → Voice Settings | Voice and language | any voice you like; English |
| Agent → Calling Window | Hours | on, 08:00–21:00 every day (a time a user books must fall inside it) |

## Embed & Widgets (Agent → Embed & Widgets)

| Setting | Value |
|---|---|
| Install & domains → Allowed domains | **`app://`** (the apps), `localhost` (the website demo on your machine), and your website's domain |
| Publishable key | copy it into each app's configuration (see the root README) |
| What visitors get | **Call and chat** |
| Call audio | High quality (optional; WebRTC with automatic fallback) |
| App actions → Import from JSON | **`actions.json`**, then **Save** (saving is what gives the agent the actions) |
| Components | the three below, from `components.json`, selected for this agent |
| Appearance → Chat window → Suggestions | optional: “What is due today?”, “I have a new symptom”, “Book a check-in call”, “What do you remember about me?” |

| Component | Template (kind) | Tool name |
|---|---|---|
| Daily check-in | quick_actions | `show_daily_check_in` |
| Doses taken today | product_list | `show_doses_taken` |
| Check-in call times | calendar_slots | `show_check_in_times` |

## Memory (Agent → Memory → Settings, the same page as Embed & Widgets → Users & memory)

| Setting | Value |
|---|---|
| Remembers users across chats and calls | **On** |
| Who is the user? | **Custom field → `patient_id`** — the field the apps identify the patient with (they send the same id under `user_id` too, so **User ID** also works); with any other field nobody is recognised |
| Devices per user | 2 (the same person on the website and on a phone, or on two phones) |
| What the agent remembers → Shared with | This agent only |
| What the agent remembers → Keep memories for | 365 days |
| What the agent remembers → What to remember — and what never to | the text below, as it is (at most 1,000 characters; it replaces the default rules) |

```text
Remember the patient's health goals (for example walking 30 minutes a day), the routines they chose
around meals, sleep and exercise, what they find hard, their preferred language, and the days and
times they like check-in calls and reminders.
Never remember symptoms, medicines, doses, test results, diagnoses or record numbers, anything about
other people's health, or anything the patient asks you to forget.
```

Health conditions, medicines and diagnoses are removed before anything is saved, whatever these rules say, so
Asha asks about symptoms and doses in every conversation. What Asha does with what she remembers is set by the
**Memory** section of `prompt.md` (part of the prompt you pasted): the preferred language, a goal confirmed
instead of asked again, a follow-up on what was agreed.

Agent → Memory → **People** shows everyone the agent has talked to while they were identified and what it
remembers about each — useful during QA. Erase a person there and the agent forgets them at once.

## Calls & scheduling (Agent → Calls & scheduling)

| Setting | Value |
|---|---|
| Who may book a call → The agent, during a conversation | **On** |
| Who may book a call → Users, from your app | **On** |
| Upcoming calls per user · Days ahead · Minutes of notice | 3 · 14 · 5 |
| How it rings → Name on the incoming call | **Asha · Graine Care** |
| Seconds to ring · Minutes before ringing again · Rings per user per day | 45 · 10 · 3 |
| Quiet hours | 21:00 – 08:00 |

## In-app calling (Workspace settings → In-app calling)

Needed only for hosted ringing (Graine rings the calls that were booked). One entry per app you build.

| Platform | Fields |
|---|---|
| iOS | Bundle ID of the app you build (React Native default `ai.graine.demo.rn`, Flutter `ai.graine.demo.flutter`), Team ID, Key ID, the APNs `.p8` key, environment **Sandbox** for development builds (or Auto) |
| Android | Package name (React Native default `ai.graine.demo.rn`, Flutter `ai.graine.demo.flutter`) and your Firebase project's service-account JSON |

The status turns to verified when the credentials work; a broken key shows its reason there.

# Settings — Tara, store assistant (Graine Store)

Every dashboard setting this agent uses, where it lives, and its value. Values in **bold** must match the
apps exactly (they read them from `usecases/retail-shopping.json`). Graine Store is a fictional daily-essentials
store: fresh milk and groceries delivered every morning before 7 am, on subscriptions paid from a prepaid wallet.

## The agent

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Tara** |
| Agent → Prompt | Prompt | everything below the line in `prompt.md` |
| Agent → Prompt | Welcome message | Hi {name}, I'm Tara from Graine Store. Want to hear what's coming tomorrow morning? |
| Agent → Custom Variables | Defaults | `name` = `there`, `plan` = `daily plan`, `city` = `your city`, `delivery_slot` = `the morning`, `wallet_balance` = `your balance`, `who_called` = `customer`, `call_reason` = (empty) |
| Agent → Voice Settings | Voice and language | a warm Indian English voice that also speaks Hindi; English |
| Agent → Calling Window | Hours | on, 08:00–21:00 every day (a time a user books must fall inside it) |
| Agent → Knowledge base | Optional | `knowledge.md` beside this file: the store's products, prices and rules |

## Embed & Widgets (Agent → Embed & Widgets)

| Setting | Value |
|---|---|
| Install & domains → Allowed domains | **`app://`** (the apps), `localhost` (the website demo on your machine), and your website's domain |
| Publishable key | copy it into each app's configuration (see the root README) |
| What visitors get | **Call and chat** |
| Call audio | High quality (optional; WebRTC with automatic fallback) |
| App actions → Import from JSON | **`actions.json`**, then **Save** (saving is what gives the agent the actions) |
| Components | the four below, from `components.json`, selected for this agent |
| Appearance → Chat window → Suggestions | optional: “What's coming tomorrow?”, “Skip tomorrow's milk”, “Add something to tomorrow”, “What do you remember about me?” |
| Appearance → Avatar | optional: **Show a live face on calls** on, a licensed or fictional portrait, Quality **Standard** (see `../SETUP.md`, 6b) |

| Component | Template (kind) | Tool name |
|---|---|---|
| Add to tomorrow | product_list | `show_add_ons` |
| Pause for | quick_actions | `show_pause_options` |
| What went wrong | quick_actions | `show_issue_reasons` |
| Call times | calendar_slots | `show_call_times` |

## Memory (Agent → Memory → Settings, the same page as Embed & Widgets → Users & memory)

| Setting | Value |
|---|---|
| Remembers users across chats and calls | **On** |
| Who is the user? | **Custom field → `shopper_id`** — the field the apps identify the customer with (they send the same id under `user_id` too, so **User ID** also works); with any other field nobody is recognised |
| Devices per user | 2 (the same person on the website and on a phone, or on two phones) |
| What the agent remembers → Shared with | This agent only |
| What the agent remembers → Keep memories for | 365 days |
| What the agent remembers → What to remember — and what never to | the text below, as it is (at most 1,000 characters; it replaces the default rules) |

```text
Remember where deliveries should be left (for example the bag on the door handle, or the security desk),
whether to ring the bell, the products the household likes or avoids, weekly patterns (toned milk on
weekends, extra curd on Sundays), trips they mention and when they plan to resume, and refunds or calls
they are still waiting on.
Never remember payment details, wallet amounts, full addresses, gate codes or phone numbers.
```

Payment details, addresses and phone numbers are removed before anything is saved, whatever these rules say.
What Tara does with what she remembers is set by the **Memory** section of `prompt.md` (part of the prompt you
pasted): a preference confirmed instead of asked again, a follow-up on a refund or a pause still open.

Agent → Memory → **People** shows everyone the agent has talked to while they were identified and what it
remembers about each — useful during QA. Erase a person there and the agent forgets them at once.

## Calls & scheduling (Agent → Calls & scheduling)

| Setting | Value |
|---|---|
| Who may book a call → The agent, during a conversation | **On** |
| Who may book a call → Users, from your app | **On** |
| Upcoming calls per user · Days ahead · Minutes of notice | 3 · 14 · 5 |
| How it rings → Name on the incoming call | **Tara · Graine Store** |
| Seconds to ring · Minutes before ringing again · Rings per user per day | 45 · 10 · 3 |
| Quiet hours | 21:00 – 08:00 |

## In-app calling (Workspace settings → In-app calling)

Needed only for hosted ringing (Graine rings the calls that were booked). One entry per app you build.

| Platform | Fields |
|---|---|
| iOS | Bundle ID of the app you build (React Native default `ai.graine.demo.rn`, Flutter `ai.graine.demo.flutter`), Team ID, Key ID, the APNs `.p8` key, environment **Sandbox** for development builds (or Auto) |
| Android | Package name (React Native default `ai.graine.demo.rn`, Flutter `ai.graine.demo.flutter`) and your Firebase project's service-account JSON |

The status turns to verified when the credentials work; a broken key shows its reason there.

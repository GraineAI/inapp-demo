# Settings — Tara, shopping assistant (Graine Store)

Every dashboard setting this agent uses, where it lives, and its value. Values in **bold** must match the
apps exactly (they read them from `usecases/retail-shopping.json`).

## The agent

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Tara** |
| Agent → Prompt | Prompt | everything below the line in `prompt.md` |
| Agent → Prompt | Welcome message | Hi {name}, I'm Tara, your shopping assistant. Want me to check on an order? |
| Agent → Custom Variables | Defaults | `name` = `there`, `membership` = `valued`, `city` = `your city`, `who_called` = `customer`, `call_reason` = (empty) |
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
| Appearance → Chat window → Suggestions | optional: “Where is my order?”, “Return an item”, “Book a delivery call”, “What do you remember about me?” |

| Component | Template (kind) | Tool name |
|---|---|---|
| Recommended for you | product_list | `show_recommendations` |
| Return reason | quick_actions | `show_return_reasons` |
| Delivery call times | calendar_slots | `show_delivery_call_times` |

## Memory (Agent → Memory → Settings, the same page as Embed & Widgets → Users & memory)

| Setting | Value |
|---|---|
| Remembers users across chats and calls | **On** |
| Who is the user? | **Custom field → `shopper_id`** — the field the apps identify the shopper with (they send the same id under `user_id` too, so **User ID** also works); with any other field nobody is recognised |
| Devices per user | 2 (the same person on the website and on a phone, or on two phones) |
| What the agent remembers → Shared with | This agent only |
| What the agent remembers → Keep memories for | 365 days |
| What the agent remembers → What to remember — and what never to | the text below, as it is (at most 1,000 characters; it replaces the default rules) |

```text
Remember the shopper's sizes, the styles, colours and brands they like or dislike, where parcels
should be left (for example with the security desk) and their best delivery times, gift occasions
they mention, and returns or deliveries they are still waiting on.
Never remember payment details, full addresses, phone numbers or order amounts.
```

Payment details, addresses and phone numbers are removed before anything is saved, whatever these rules say.
What Tara does with what she remembers is set by the **Memory** section of `prompt.md` (part of the prompt you
pasted): a size confirmed instead of asked again, a follow-up on a return or a delivery still open.

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

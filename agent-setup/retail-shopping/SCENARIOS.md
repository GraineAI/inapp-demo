# Graine Store — the daily-essentials demo, scenario by scenario

A 15-minute demo of Graine's in-app agents inside a daily-essentials app: milk and groceries delivered every
morning, on subscriptions paid from a prepaid wallet. Two agents: **Tara**, the store assistant (Home and Chat),
and the **Freshness desk** (the Help screen). Everything is fictional: the store, the three households, the
products and the prices.

Run it in the Kotlin Multiplatform app (`kmp/`), on a real phone for the calls and the ring. Choose **Graine Store**
on the sign-in screen.

## Before the demo (once)

- Both agents set up as in `settings.md` and `help-settings.md`, their publishable keys in the app's configuration,
  **Show a live face on calls** on for Tara, and the app's push credentials verified in the workspace (for the
  ring in scenario 7).
- Sign in once as each household and sign out again, so the first sign-in of the demo is quick.
- Changes in the app last for the app session: sign out and back in to start from the pack's starting state.

| Household | Customer ID | What makes them useful |
|---|---|---|
| Ananya Gupta, Delhi | `SHP-20031` | Daily milk and curd, brown bread already in tomorrow's basket, paneer to add, wallet ₹240 |
| Vikram Singh, Jaipur | `SHP-20032` | Yesterday's delivery never came, eggs paused, wallet ₹85 (low) |
| Kavya Reddy, Bengaluru | `SHP-20033` | Away on a trip: A2 milk paused; yogurt and bananas still running; auto top-up on |

## 1. "What's coming tomorrow?" — the agent knows the screen (Ananya, chat)

Sign in as **Ananya**. On Home, open the chat and tap **What's coming tomorrow?**

Tara opens **Deliveries** and says what arrives tomorrow between 6 and 7 am: cow milk and the brown bread already
in the basket. Curd is every other day, so it isn't due tomorrow.

Point out: Tara read the app's own screen and opened it for the customer; no integration with the store's
backend was needed for the demo.

## 2. "Skip tomorrow's milk and add paneer instead" — the agent acts on the app (Ananya, chat)

Say: *"Skip tomorrow's milk and add paneer instead."*

Tara confirms, skips the milk (its chip turns to **Skipped tomorrow**), says the paneer is ninety rupees, adds it
(**In tomorrow's basket**) and reminds that changes close at 11 pm.

Then tap **Remove** on the paneer yourself and ask *"Is the paneer still coming?"*: Tara knows it was removed. The
customer's taps and the agent's actions change the same state.

## 3. "We're off to Jaipur for a week" — voice, in the app (Ananya, Talk)

Tap **Talk** and say: *"We're going away for a week. Pause the milk and the curd."*

Tara may show **Pause for** (Just tomorrow, 3 days, A week, Until I resume); answer by voice or tap. She pauses
both and says they restart the day after you resume. Say *"Actually, resume the curd"*: she resumes it.

Point out: the same agent in a voice bar over the app, acting while the customer talks. Speak in Hindi or a mix of
Hindi and English (*"kal ka doodh mat bhejna"*): Tara follows.

## 4. "Will my milk come tomorrow?" — personal context (Vikram, chat)

Sign out, sign in as **Vikram**. Ask: *"Will my milk come tomorrow?"*

Tara says yes, two packets of toned milk between 5:30 and 6:30 am, and that the wallet's ₹85 covers only
tomorrow's delivery: top up in the app before 11 pm. She doesn't take payment details, and offers a call if Vikram
wants help.

Point out: each customer's plan, slot and balance reach the agent when they sign in; nobody types them.

## 5. "My delivery didn't come yesterday" — the second agent (Vikram, Help → chat)

Open **Help** (the Freshness desk) and say: *"My delivery didn't come yesterday."*

The freshness desk opens **Yesterday's delivery**, records it (**Problem reported**), tells Vikram the ₹56 goes back
to his wallet within 24 hours, and asks whether anything changed at the doorstep (a new gate at the society).

Point out: a second agent with its own job and voice, on the same sign-in; it sees the same app.

## 6. "The curd was sour" — a phone-style call inside the app (Ananya, Help → Call)

Sign in as **Ananya**, open **Help** and tap **Call**. Say: *"The curd that came today was sour."*

On the call screen the freshness desk asks what went wrong, records it on **Today's delivery** and confirms the
refund to the wallet.

Point out: a full call screen (mute, speaker, end) inside the app, with the call's connection report; no phone
number involved.

## 7. "Call me at 7 to plan the week" — the agent rings the app (Ananya, booked call)

In the chat with Tara say: *"Call me in ten minutes to plan next week's deliveries"* (or open **Calls from Tara →
Book a call with Tara** and choose **In 10 minutes**). The booking appears under **Calls from Tara**.

Lock the phone. At the time, the phone rings like a phone call: **Tara · Graine Store**, on the lock screen. Answer:
Tara opens with *"Hi Ananya, it's Tara from Graine Store, calling about planning your week's deliveries."*

Point out: the agent rings a closed app, at a time the customer chose, inside the store's quiet hours and calling
window.

## 8. Face to face — the avatar (Kavya, Talk with the face on)

Sign in as **Kavya**. On Home turn on **Show the agent's face**, then tap **Talk**. Tara's face fills the screen,
with the call drawn over it.

Say: *"I'm back from my trip. Restart my milk, and what's good for breakfast tomorrow?"* Tara resumes the A2 milk
and suggests add-ons (paneer, bread, coconut water, bananas) with their prices.

Point out: the same agent, the same actions, now with a live face drawn from her own speech.

## 9. Memory — the store remembers the doorstep (Ananya, two chats)

As **Ananya**, say: *"Please leave the milk in the bag on my door handle, and don't ring the bell."* Close the chat,
wait about a minute, open the chat, choose **New conversation** and ask: *"Where should the milk be left?"*

Tara answers: in the bag on the door handle, without ringing the bell. Open **What Tara remembers** on Home: the
preference is there, with a switch to turn memory off and a button to erase it.

Point out: memory only with the customer's consent (the switch at sign-in), never payment details, wallet amounts or
addresses.

## 10. Store knowledge — questions any customer asks (anyone, chat)

Ask: *"Do you deliver on Sundays?"*, *"How much is A2 milk?"*, *"Until when can I change tomorrow's order?"*

Tara answers from the store's knowledge base: every morning including Sundays, ninety rupees a litre, until 11 pm.

## If something doesn't happen

- **The agent says it can't do something the app shows**: the app actions were imported but not saved
  (Embed & Widgets → App actions → Save).
- **No cards (add-ons, pause options)**: the components are not selected for the agent.
- **Nothing is remembered**: Memory is off, *Who is the user?* is not `shopper_id`, or the person turned memory off
  at sign-in; ask after **New conversation**, about a minute after the first chat.
- **The booked call never rings**: the app's push credentials are not verified in the workspace, or the phone is a
  simulator.
- **No face**: Avatar is off for Tara, or the call fell back from WebRTC.

package ai.graine.demo.journey

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.UseCasePack
import ai.graine.inapp.kmp.GraineBookingState
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallOutcome
import ai.graine.inapp.kmp.GraineCallState
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineField
import ai.graine.inapp.kmp.GraineFieldStatus
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/** The screens of a journey that is under way, from its sidebar. The chat is the main screen. */
enum class JourneyRoute { CHAT, TODAY, WEEK, PROGRESS, ACCOUNT, SUPPORT }

/** A plan item, by its day and time. */
data class ItemRef(val day: Weekday, val time: String)

/**
 * THE JOURNEY — onboarding, the first call (now or booked, "We couldn't connect", its chat version), the plan the
 * coach builds through the app's actions, check-ins, Today, Progress, Account and privacy — as one state machine over
 * [JourneySdk] and the [JourneyRepository]. Every word comes from the pack's journey ([journey]). Main thread.
 *
 *     ONBOARDING → FIRST_CALL ─ Start now ──────────────→ IN_CALL ─ ENDED, what it learned saved ─→ BUILDING_PLAN → READY
 *                     └─ Choose a time → BOOKED ─ rings ─┘   └─ missed, declined, failed, ended early ─→ COULD_NOT_CONNECT
 *     COULD_NOT_CONNECT: Call me now → IN_CALL · Choose another time → BOOKED · Continue in chat → FIRST_CALL_CHAT
 *     FIRST_CALL_CHAT ─ what it learned saved ─→ BUILDING_PLAN
 *
 * The journey's actions are registered at once, at every launch, so a call that rings a closed app finds them; the
 * user is identified at every launch while signed in.
 */
class JourneyModel(
    val pack: UseCasePack,
    private val sdk: JourneySdk,
    store: TextStore,
    private val reminders: ReminderScheduler,
    private val share: JourneyShare,
    /** The journey's own scope; [close] cancels it. */
    private val scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    /** A debug build: "Load sample plan" and "Simulate reminder" are offered. */
    private val debugBuild: Boolean = false,
    private val newUserId: () -> String = { newId() },
    /** The journey came on screen, or left it: the app's own call screen follows it. */
    private val onOpenChanged: (Boolean) -> Unit = {},
) {
    val journey: JourneyPack = pack.journey ?: throw IllegalArgumentException("The pack ${pack.id} has no journey")

    private val repo = JourneyRepository(
        store, pack.id, JourneySettings(quietStart = journey.checkIns.quietStart, quietEnd = journey.checkIns.quietEnd), scope, io,
    )

    // ── what the screens draw ───────────────────────────────────────────────

    /** The journey is on screen: its user is signed in. */
    var open by mutableStateOf(false)
        private set
    var account by mutableStateOf(repo.account)
        private set
    var profile by mutableStateOf(repo.profile)
        private set
    var stage by mutableStateOf(repo.progress.stage)
        private set
    var onboardingStep by mutableStateOf(repo.progress.onboardingStep)
        private set
    /** Why the first call did not happen, on "We couldn't connect". */
    var noConnect by mutableStateOf(repo.progress.noConnect)
        private set
    var booked by mutableStateOf(repo.progress.booked)
        private set
    var intake by mutableStateOf(repo.intake)
        private set
    var plan by mutableStateOf(repo.plan)
        private set
    var activity by mutableStateOf(repo.activity)
        private set
    var settings by mutableStateOf(repo.settings)
        private set
    /** The first-call picker, while it is open. */
    var picker by mutableStateOf<PickerState?>(null)
        private set
    var route by mutableStateOf(JourneyRoute.CHAT)
        private set
    /** The plan item open in detail. */
    var openItem by mutableStateOf<ItemRef?>(null)
        private set
    var shareOpen by mutableStateOf(false)
        private set
    /** The plan is taking longer than usual to arrive. */
    var planSlow by mutableStateOf(false)
        private set
    /** The plan just arrived: the chat's card says it is ready until My week is opened. */
    var planFresh by mutableStateOf(false)
        private set
    /** An SDK request is on its way: its button waits. */
    var busy by mutableStateOf(false)
        private set
    var microphoneAllowed by mutableStateOf<Boolean?>(null)
        private set
    var notificationsAllowed by mutableStateOf<Boolean?>(null)
        private set
    var exporting by mutableStateOf(false)
        private set
    /** What the coach's chat composer starts with ("Ask about this"); the chat takes it once. */
    var chatDraft by mutableStateOf<String?>(null)
    /** One line for the user: what just happened, or why it did not. */
    var notice by mutableStateOf<String?>(null)
    /** Whether the first-call agent can be booked now, and why not (the SDK's bookingState). */
    var firstCallBooking by mutableStateOf(sdk.bookingState(JourneyAgent.FIRST_CALL).value)
        private set
    /** The call as the SDK reports it, for the screens that follow it. */
    var call by mutableStateOf(sdk.callState.value)
        private set

    /** "Load sample plan" and "Simulate reminder": a debug build, or switched on in Account. */
    val testingTools: Boolean get() = debugBuild || settings.testingTools

    /** A debug build: the testing tools are always on. */
    val isDebugBuild: Boolean get() = debugBuild

    private val pendingEvents = ArrayDeque<Pair<String, String?>>()
    private var holdingCoachChat = false
    private var coachStatus: GraineChatStatus = sdk.chatStatus(JourneyAgent.COACH).value
    private var endedGrace: Job? = null
    private var slowTimer: Job? = null
    private var lastStage: JourneyStage? = null
    /** The plan was asked for in a conversation that is still live. */
    private var planAsked = false
    /** That conversation ended before the plan came: ask again in the next. */
    private var askAgainWhenOnline = false

    /** The day My week shows. */
    var weekDay by mutableStateOf(Weekday.of(today()))
        private set

    init {
        registerActions()
        scope.launch { sdk.callState.collect { onCall(it) } }
        scope.launch { sdk.callOutcomes.collect { onOutcome(it) } }
        scope.launch { sdk.chatStatus(JourneyAgent.COACH).collect { onCoachStatus(it) } }
        scope.launch {
            sdk.bookingState(JourneyAgent.FIRST_CALL).collect {
                val changed = it != firstCallBooking
                firstCallBooking = it
                if (changed && picker != null) refreshPicker()
            }
        }
        if (account?.signedIn == true) resume()
        // After resume: a tap that started the app is opened for the user signed in again, never before.
        ReminderTaps.listen { openCheckIn(it) }
    }

    /** The pack changed: this journey lets go of its actions, its taps and its coroutines. */
    fun close() {
        sdk.unregisterActions()
        ReminderTaps.stop()
        if (holdingCoachChat) sdk.chatShown(JourneyAgent.COACH, false)
        holdingCoachChat = false
        scope.cancel()
    }

    private fun today(): LocalDate = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date
    private fun nowTime(): String = JourneyTime.of(Instant.fromEpochMilliseconds(now()), zone)

    // ── signing up, in and out ──────────────────────────────────────────────

    /** "Start": a new user on this phone, from the first onboarding screen. */
    fun start() {
        val acc = JourneyAccount(newUserId(), now(), signedIn = true)
        repo.eraseAll()
        reload()
        repo.saveAccount(acc)
        account = acc
        setProgress { JourneyProgress() }
        showJourney(true)
        describe()
    }

    /**
     * "Continue": the user on this phone, signed in again — and at every launch while they are signed in. They are
     * identified again (with their profile's variables), the booked call's fate is read, and reminders come back.
     */
    fun resume() {
        val acc = account ?: return
        if (!acc.signedIn) repo.saveAccount(acc.copy(signedIn = true).also { account = it })
        if (profileComplete()) tellAgentsWhoItIs()
        showJourney(true)
        reconcile()
        rescheduleReminders()
        describe()
    }

    /**
     * Log out: a first call still booked is called off (it would ring nobody), the SDK forgets the user everywhere
     * (identify(null)), and this phone's journey — profile, plan, check-ins, reminders — is erased, so the next person
     * to sign up here starts from nothing. In an app with a server of its own, the plan comes back from there at the
     * next sign-in; this demo keeps it only on the phone.
     */
    fun logOut() {
        if (busy) return
        val b = booked
        if (b == null) {
            leaveThisPhone()
            return
        }
        busy = true
        scope.launch {
            withTimeoutOrNull(CANCEL_WAIT_MS) { attempt { sdk.cancelScheduledCall(b.id, JourneyAgent.FIRST_CALL) } }
            busy = false
            leaveThisPhone()
        }
    }

    /** The user is gone from this phone: no variables, no identity, no reminders, nothing of theirs on screen or on disk. */
    private fun leaveThisPhone() {
        sdk.setVariables(journey.variableNames.associateWith { null })
        sdk.identify(null)
        reminders.replaceAll(emptyList())
        releaseCoachChat()
        pendingEvents.clear()
        endedGrace?.cancel()
        slowTimer?.cancel()
        repo.eraseAll()
        reload()
        picker = null
        openItem = null
        shareOpen = false
        chatDraft = null
        route = JourneyRoute.CHAT
        lastStage = null
        showJourney(false)
    }

    /**
     * Delete account: what each agent remembers about the user — and the conversation it kept — is forgotten, then the
     * SDK lets the user go (identify(null)) and everything on this phone is erased. [done] hears whether it worked.
     */
    fun deleteAccount(done: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            // A first call still booked would ring someone who is gone.
            booked?.let { b -> attempt { sdk.cancelScheduledCall(b.id, JourneyAgent.FIRST_CALL) } }
            var failure: Throwable? = null
            for (agent in JourneyAgent.entries) {
                if (!sdk.isConfigured(agent)) continue
                val result = attempt { sdk.forgetMemory(agent) }
                val e = result.exceptionOrNull()
                // Nobody identified with that agent means there is nothing it remembers.
                if (e != null && (e as? GraineException)?.code != GraineErrorCode.IdentityRequired && agent == JourneyAgent.COACH) failure = e
            }
            busy = false
            if (failure != null) {
                notice = "Couldn't delete your account: ${CallText.message(failure)}"
                done(false)
                return@launch
            }
            leaveThisPhone()
            done(true)
        }
    }

    /** "Start over", from the sign-in screen: this phone's journey erased, nothing sent. */
    fun eraseThisPhone() {
        reminders.replaceAll(emptyList())
        repo.eraseAll()
        reload()
    }

    private fun showJourney(next: Boolean) {
        if (open == next) return
        open = next
        onOpenChanged(next)
    }

    private fun reload() {
        account = repo.account
        profile = repo.profile
        stage = repo.progress.stage
        onboardingStep = repo.progress.onboardingStep
        noConnect = repo.progress.noConnect
        booked = repo.progress.booked
        intake = repo.intake
        plan = repo.plan
        activity = repo.activity
        settings = repo.settings
        planSlow = false
        planFresh = false
    }

    // ── 1. onboarding ───────────────────────────────────────────────────────

    fun onboardingNext() {
        val next = when (onboardingStep) {
            OnboardingStep.WELCOME -> OnboardingStep.PROFILE
            OnboardingStep.PROFILE -> if (profileComplete()) OnboardingStep.LANGUAGE else return
            OnboardingStep.LANGUAGE -> if (profile.languageId != null) OnboardingStep.MICROPHONE else return
            OnboardingStep.MICROPHONE -> OnboardingStep.NOTIFICATIONS
            OnboardingStep.NOTIFICATIONS -> { finishOnboarding(); return }
        }
        setProgress { it.copy(onboardingStep = next) }
        describe()
    }

    fun onboardingBack() {
        val prev = when (onboardingStep) {
            OnboardingStep.WELCOME -> return
            OnboardingStep.PROFILE -> OnboardingStep.WELCOME
            OnboardingStep.LANGUAGE -> OnboardingStep.PROFILE
            OnboardingStep.MICROPHONE -> OnboardingStep.LANGUAGE
            OnboardingStep.NOTIFICATIONS -> OnboardingStep.MICROPHONE
        }
        setProgress { it.copy(onboardingStep = prev) }
        describe()
    }

    /** The profile's problems, by field key, for these answers; empty when they can be kept. */
    fun profileProblems(values: Map<String, String>): Map<String, String> = ProfileRules.problems(journey.onboarding.fields, values, today())

    /**
     * The profile screen's Continue: the answers kept, then `identify(user_id, name)` and `setVariables(…)` — the
     * agents know who the user is and what they said, from the next conversation on. Returns the problems, if any.
     */
    fun saveProfile(values: Map<String, String>): Map<String, String> {
        val clean = values.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        val problems = profileProblems(clean)
        if (problems.isNotEmpty()) return problems
        updateProfile(profile.copy(values = clean))
        if (stage == JourneyStage.ONBOARDING && onboardingStep == OnboardingStep.PROFILE) onboardingNext()
        return emptyMap()
    }

    /** The language chosen: its code goes to the agents as the language variable (`agent_language`), with its own variables. */
    fun chooseLanguage(id: String) {
        if (journey.onboarding.language.options.none { it.id == id }) return
        updateProfile(profile.copy(languageId = id))
        if (stage == JourneyStage.ONBOARDING && onboardingStep == OnboardingStep.LANGUAGE) onboardingNext()
    }

    private fun updateProfile(next: JourneyProfile) {
        profile = next
        repo.saveProfile(next)
        if (profileComplete()) tellAgentsWhoItIs()
    }

    /** The microphone screen's Allow: `Graine.calls.requestCallPermissions()`. Either answer moves on. */
    fun allowMicrophone() {
        if (busy) return
        busy = true
        scope.launch {
            microphoneAllowed = attempt { sdk.requestCallPermissions() }.getOrNull()?.microphone
            busy = false
            if (onboardingStep == OnboardingStep.MICROPHONE) onboardingNext()
        }
    }

    /** The notifications screen's Allow: the phone's own question (check-in reminders; on Android 13+ calls ring with it too). */
    fun allowNotifications() {
        if (busy) return
        busy = true
        scope.launch {
            notificationsAllowed = attempt { reminders.requestPermission() }.getOrNull() ?: false
            busy = false
            if (onboardingStep == OnboardingStep.NOTIFICATIONS) onboardingNext()
        }
    }

    private fun finishOnboarding() {
        setProgress { it.copy(stage = JourneyStage.FIRST_CALL) }
        describe()
    }

    private fun profileComplete(): Boolean = ProfileRules.complete(journey, profile, today())

    /** identify, then setVariables: the agents know who it is and the profile, from their next conversation on. */
    private fun tellAgentsWhoItIs() {
        val acc = account ?: return
        sdk.identify(ProfileRules.traits(journey, acc.userId, profile))
        sdk.setVariables(ProfileRules.variables(journey, profile, today()))
    }

    // ── 2. the first call ───────────────────────────────────────────────────

    val firstCallReady: Boolean get() = sdk.isConfigured(JourneyAgent.FIRST_CALL)

    /** "Start now" (and "Start now instead" while one is booked): `Graine.calls.startCall(reason, agent = "consultation")`. */
    fun startFirstCall() {
        if (stage == JourneyStage.IN_CALL || stage == JourneyStage.BUILDING_PLAN || stage == JourneyStage.READY) return
        if (!firstCallReady) {
            notice = NO_FIRST_CALL_AGENT
            return
        }
        val id = sdk.startCall(journey.firstCall.callReason, JourneyAgent.FIRST_CALL)
        if (id == null) {
            notice = "Another call is in progress. Try again when it is over."
            return
        }
        picker = null
        setProgress { it.copy(stage = JourneyStage.IN_CALL, firstCallId = id, noConnect = null, booked = null) }
        // The call happens now: any first call booked for later would ring again.
        cancelOtherFirstCalls(keep = null)
        describe()
    }

    /**
     * Every first call still booked, but [keep], called off — two bookings never overlap, and none rings once the
     * first call has happened (a time changed twice, a cancel that failed, a booking made on another phone).
     */
    private fun cancelOtherFirstCalls(keep: String?) {
        scope.launch {
            val calls = attempt { sdk.scheduledCalls(JourneyAgent.FIRST_CALL) }.getOrNull() ?: return@launch
            for (c in calls) if (c.canCancel && c.id != keep) attempt { sdk.cancelScheduledCall(c.id, JourneyAgent.FIRST_CALL) }
        }
    }

    /** "Choose a time": the picker, from the first-call agent's own times (`bookingSlots(day)`), a few of them a day. */
    fun openPicker() {
        if (!firstCallReady) {
            notice = NO_FIRST_CALL_AGENT
            return
        }
        val rules = sdk.schedulingRules(JourneyAgent.FIRST_CALL)
        val days = Picker.days(
            today = today(),
            dayCount = journey.firstCall.dayCount,
            maxDaysAhead = rules.maxDaysAhead,
            preferred = journey.firstCall.preferredTimes,
            quietStart = settings.quietStart,
            quietEnd = settings.quietEnd,
            zone = zone,
            slots = { sdk.bookingSlots(it, JourneyAgent.FIRST_CALL) },
        )
        picker = Picker.open(days)
        describe()
    }

    /** The picker's times read again (the agent's rules arrived or changed), keeping the day and time still offered. */
    private fun refreshPicker() {
        val p = picker ?: return
        if (p.busy) return
        openPicker()
        val fresh = picker ?: return
        val day = p.day?.takeIf { d -> fresh.days.any { it.date == d && it.times.isNotEmpty() } } ?: fresh.day
        val time = p.time?.takeIf { t -> fresh.days.firstOrNull { it.date == day }?.times?.contains(t) == true }
        picker = fresh.copy(day = day, time = time)
    }

    fun closePicker() {
        picker = null
        describe()
    }

    fun pickDay(date: LocalDate) {
        val p = picker ?: return
        if (p.busy || p.days.none { it.date == date }) return
        picker = p.copy(day = date, time = null, error = null)
    }

    fun pickTime(at: Instant) {
        val p = picker ?: return
        if (p.busy || at !in p.times) return
        picker = p.copy(time = at, error = null)
    }

    /**
     * Confirm: `scheduleCall(at, reason)` with the first-call agent — the SDK checks the agent's rules, then Graine
     * its calling hours and the calls already booked; a refusal stays in the picker, in the SDK's words. A time
     * booked in place of another calls the other off once the new one is safe.
     */
    fun confirmPicker() {
        val p = picker ?: return
        val at = p.time ?: return
        if (p.busy) return
        picker = p.copy(busy = true, error = null)
        scope.launch {
            val result = attempt { sdk.scheduleCall(at, journey.firstCall.bookingReason, JourneyAgent.FIRST_CALL) }
            result.onSuccess { call ->
                picker = null
                sdk.dismissMissedCall()
                setProgress {
                    it.copy(stage = JourneyStage.BOOKED, booked = BookedCall(call.id, (call.at ?: at).toEpochMilliseconds()), noConnect = null, firstCallId = null)
                }
                // The new time is safe: every other booking goes (the one it replaces, and any other).
                cancelOtherFirstCalls(keep = call.id)
                describe()
            }.onFailure { e ->
                picker = picker?.copy(busy = false, error = (e as? GraineException)?.message?.takeIf { it.isNotBlank() } ?: CallText.message(e))
            }
        }
    }

    // ── 3. we couldn't connect ──────────────────────────────────────────────

    /** "Call me now": the missed ring called back (`Graine.calls.callBack()`), else a new call to the first-call agent. */
    fun callMeNow() {
        val id = if (call.missedCall != null) sdk.callBack() else null
        if (id != null) {
            setProgress { it.copy(stage = JourneyStage.IN_CALL, firstCallId = id, noConnect = null) }
            describe()
        } else {
            startFirstCall()
        }
    }

    /** "Continue in chat": the first-call agent's chat, which asks the same questions and saves what it learned the same way. */
    fun continueInChat() {
        if (!firstCallReady) {
            notice = NO_FIRST_CALL_AGENT
            return
        }
        sdk.dismissMissedCall()
        picker = null
        setProgress { it.copy(stage = JourneyStage.FIRST_CALL_CHAT) }
        describe()
    }

    /** From the chat version, back to calling: the first-call screen again. */
    fun leaveFirstCallChat() {
        if (stage != JourneyStage.FIRST_CALL_CHAT) return
        setProgress { it.copy(stage = if (booked != null) JourneyStage.BOOKED else JourneyStage.FIRST_CALL) }
        describe()
    }

    /** The call as the SDK reports it: a first call ringing or answered is under way; a missed ring is "We couldn't connect". */
    private fun onCall(info: GraineCallInfo) {
        call = info
        if (!open) return
        val ours = info.agent == JourneyAgent.FIRST_CALL.sdkName || (info.agent == null && stage in WAITING_FOR_A_RING)
        if (ours && info.state in LIVE && stage in FIRST_CALL_STAGES && stage != JourneyStage.IN_CALL) {
            setProgress { it.copy(stage = JourneyStage.IN_CALL, firstCallId = info.callId ?: it.firstCallId, noConnect = null) }
            describe()
        }
        val missed = info.missedCall
        if (missed != null && (stage == JourneyStage.BOOKED || stage == JourneyStage.IN_CALL) &&
            (missed.callId == repo.progress.firstCallId || repo.progress.firstCallId == null)
        ) {
            couldNotConnect(NoConnect.MISSED)
        }
    }

    /** What became of a call: for the first-call agent's, the journey moves on. */
    private fun onOutcome(o: GraineCallOutcome) {
        if (!open || stage !in FIRST_CALL_STAGES) return
        val ours = o.agent == JourneyAgent.FIRST_CALL.sdkName || (o.agent == null && o.callId == repo.progress.firstCallId)
        if (!ours) return
        when (o.outcome) {
            GraineCallOutcome.Kind.RINGING, GraineCallOutcome.Kind.ANSWERED -> if (stage != JourneyStage.IN_CALL) {
                setProgress { it.copy(stage = JourneyStage.IN_CALL, firstCallId = o.callId, noConnect = null) }
                describe()
            }
            GraineCallOutcome.Kind.DECLINED -> couldNotConnect(NoConnect.DECLINED)
            GraineCallOutcome.Kind.MISSED -> couldNotConnect(if (o.reason == "busy") NoConnect.BUSY else NoConnect.MISSED)
            GraineCallOutcome.Kind.FAILED -> couldNotConnect(NoConnect.FAILED)
            GraineCallOutcome.Kind.ENDED -> firstCallEnded()
        }
    }

    /**
     * The first call ended. With what it learned saved, the plan is next; otherwise the call ended early — after a
     * short wait, since the save and the call's end come by different routes.
     */
    private fun firstCallEnded() {
        if (intake != null && stage != JourneyStage.BUILDING_PLAN) {
            buildPlan()
            return
        }
        endedGrace?.cancel()
        endedGrace = scope.launch {
            delay(INTAKE_GRACE_MS)
            if (intake == null && stage in FIRST_CALL_STAGES) couldNotConnect(NoConnect.ENDED_EARLY)
        }
    }

    private fun couldNotConnect(reason: NoConnect) {
        if (stage == JourneyStage.BUILDING_PLAN || stage == JourneyStage.READY || stage == JourneyStage.FIRST_CALL_CHAT) return
        setProgress { it.copy(stage = JourneyStage.COULD_NOT_CONNECT, noConnect = reason, booked = null, firstCallId = it.firstCallId) }
        describe()
    }

    /**
     * Where a journey that was waiting on a call stands now — at launch, or on returning: a booked call the user
     * missed while the app was closed reads Missed in `scheduledCalls()`; a first call cut off by the app closing
     * has no live call left.
     */
    private fun reconcile() {
        // A ring missed before the journey was back on screen — the app woken by it, opened afterwards.
        val missed = call.missedCall
        if (missed != null && (stage == JourneyStage.BOOKED || stage == JourneyStage.IN_CALL) &&
            (missed.callId == repo.progress.firstCallId || repo.progress.firstCallId == null)
        ) {
            couldNotConnect(NoConnect.MISSED)
            return
        }
        when (stage) {
            JourneyStage.IN_CALL -> if (call.state == GraineCallState.IDLE || call.state == GraineCallState.ENDED) {
                if (intake != null) buildPlan() else couldNotConnect(if (call.missedCall != null) NoConnect.MISSED else NoConnect.ENDED_EARLY)
            }
            JourneyStage.BOOKED -> {
                val b = booked ?: return
                scope.launch {
                    val calls = attempt { sdk.scheduledCalls(JourneyAgent.FIRST_CALL) }.getOrNull()
                    if (stage != JourneyStage.BOOKED || booked?.id != b.id) return@launch
                    // Unreadable (offline): only a time long past is decided — the screen asks again on return.
                    if (calls == null) {
                        if (now() > b.at + BOOKED_GONE_MS) booked(b, null)
                        return@launch
                    }
                    booked(b, calls.firstOrNull { it.id == b.id })
                }
            }
            JourneyStage.BUILDING_PLAN -> buildPlan()
            else -> Unit
        }
    }

    /** The booked call as Graine lists it: still to come, rung and over, or gone. */
    private fun booked(b: BookedCall, listed: GraineScheduledCall?) {
        when (listed?.status) {
            GraineCallStatus.Scheduled, GraineCallStatus.Ringing -> Unit
            GraineCallStatus.Missed -> couldNotConnect(NoConnect.MISSED)
            GraineCallStatus.Declined -> couldNotConnect(NoConnect.DECLINED)
            GraineCallStatus.Failed -> couldNotConnect(NoConnect.FAILED)
            GraineCallStatus.Expired, GraineCallStatus.NoDevice -> couldNotConnect(NoConnect.NOT_REACHED)
            GraineCallStatus.Answered, GraineCallStatus.Completed -> if (intake != null) buildPlan() else couldNotConnect(NoConnect.ENDED_EARLY)
            GraineCallStatus.Cancelled -> {
                setProgress { it.copy(stage = JourneyStage.FIRST_CALL, booked = null) }
                describe()
            }
            is GraineCallStatus.Unknown -> Unit
            null -> if (now() > b.at + BOOKED_GONE_MS) {
                if (intake != null) buildPlan() else couldNotConnect(NoConnect.NOT_REACHED)
            }
        }
    }

    /**
     * The app came back to the front: a booked call's fate is read again, and a plan still on its way gets its
     * chat back (the conversation ends while the app is in the background).
     */
    fun onForeground() {
        if (!open) return
        if (stage == JourneyStage.BOOKED || stage == JourneyStage.IN_CALL) reconcile()
        if (stage == JourneyStage.BUILDING_PLAN && coachStatus != GraineChatStatus.ONLINE && coachStatus != GraineChatStatus.CONNECTING) {
            sdk.chatRetry(JourneyAgent.COACH)
        }
    }

    // ── 4. the plan ─────────────────────────────────────────────────────────

    /**
     * The progress screen: the coach's chat connected (`chat.shown(true)`), and once it is ONLINE, `Graine.reportEvent(
     * "consultation_finished", …)` — the coach reads what the first call learned and saves the week with the plan
     * action, which moves the journey on.
     */
    private fun buildPlan() {
        endedGrace?.cancel()
        setProgress { it.copy(stage = JourneyStage.BUILDING_PLAN, firstCallId = null, booked = null, noConnect = null) }
        planSlow = false
        planAsked = false
        cancelOtherFirstCalls(keep = null)
        holdCoachChat()
        report(journey.events.firstCallFinished, journey.events.firstCallFinishedDetail)
        slowTimer?.cancel()
        slowTimer = scope.launch {
            delay(PLAN_SLOW_MS)
            if (stage == JourneyStage.BUILDING_PLAN) planSlow = true
        }
        describe()
    }

    /** "Ask again", once the plan is slow: the same event, to the coach's live chat. */
    fun askForPlanAgain() {
        if (stage != JourneyStage.BUILDING_PLAN) return
        planSlow = false
        report(journey.events.firstCallFinished, journey.events.firstCallFinishedDetail)
        slowTimer?.cancel()
        slowTimer = scope.launch {
            delay(PLAN_SLOW_MS)
            if (stage == JourneyStage.BUILDING_PLAN) planSlow = true
        }
    }

    /** "Open the chat" while the plan is on its way: the chat, where the card appears when it arrives. */
    fun skipToChat() {
        if (stage != JourneyStage.BUILDING_PLAN) return
        setProgress { it.copy(stage = JourneyStage.READY) }
        releaseCoachChat()
        route = JourneyRoute.CHAT
        describe()
    }

    /** Testing without the agent: the pack's sample plan, as if the coach had saved it. */
    fun loadSamplePlan() {
        val parsed = Plan.parse(journey.plan.sample, now(), Plan.Source.SAMPLE)
        if (parsed !is Plan.Parsed.Ok) {
            notice = "The sample plan could not be read."
            return
        }
        planArrived(parsed.plan, forceReady = true)
    }

    private fun planArrived(next: Plan, forceReady: Boolean = false) {
        plan = next
        repo.savePlan(next)
        if (stage == JourneyStage.BUILDING_PLAN || (forceReady && stage != JourneyStage.READY)) {
            setProgress { it.copy(stage = JourneyStage.READY, noConnect = null, booked = null, firstCallId = null) }
            planFresh = true
            planSlow = false
            slowTimer?.cancel()
            route = JourneyRoute.CHAT
            releaseCoachChat()
        }
        rescheduleReminders()
        describe()
    }

    // ── the coach's chat and the events it hears ───────────────────────────

    private fun holdCoachChat() {
        if (holdingCoachChat) return
        holdingCoachChat = true
        sdk.chatShown(JourneyAgent.COACH, true)
    }

    private fun releaseCoachChat() {
        if (!holdingCoachChat) return
        holdingCoachChat = false
        sdk.chatShown(JourneyAgent.COACH, false)
    }

    /**
     * An event for the coach to speak up about: reported once its chat is ONLINE (`Graine.reportEvent` is dropped
     * with no conversation live), in the order they came. A chat that gave up is asked to connect again.
     */
    private fun report(name: String, detail: String?) {
        pendingEvents.addLast(name to detail)
        if (coachStatus == GraineChatStatus.ONLINE) flushEvents()
        else if (coachStatus == GraineChatStatus.OFFLINE || coachStatus == GraineChatStatus.DISCONNECTED) sdk.chatRetry(JourneyAgent.COACH)
    }

    /**
     * The coach's chat as it goes. A plan asked for in a conversation that then ended — the app went to the background,
     * Graine closed an idle chat — is asked for again when the chat is back: the new conversation never heard it.
     */
    private fun onCoachStatus(status: GraineChatStatus) {
        coachStatus = status
        when (status) {
            GraineChatStatus.ONLINE -> {
                if (askAgainWhenOnline && stage == JourneyStage.BUILDING_PLAN) {
                    askAgainWhenOnline = false
                    pendingEvents.addLast(journey.events.firstCallFinished to journey.events.firstCallFinishedDetail)
                }
                flushEvents()
            }
            GraineChatStatus.IDLE, GraineChatStatus.OFFLINE, GraineChatStatus.DISCONNECTED ->
                if (planAsked && stage == JourneyStage.BUILDING_PLAN) askAgainWhenOnline = true
            else -> Unit
        }
    }

    private fun flushEvents() {
        while (pendingEvents.isNotEmpty()) {
            val (name, detail) = pendingEvents.removeFirst()
            sdk.reportEvent(name, detail)
            if (name == journey.events.firstCallFinished) planAsked = true
        }
    }

    /** Events still waiting for the coach's chat to come online (for the screens and the tests). */
    val waitingEvents: List<String> get() = pendingEvents.map { it.first }

    // ── 5. check-ins ────────────────────────────────────────────────────────

    /**
     * A check-in reminder opened — tapped in the notification shade, or "Simulate reminder": the chat (the coach's
     * chat screen connects it), then, once it is ONLINE, `Graine.reportEvent("meal_check_in", "<which item>")`, and
     * the coach opens with the question.
     */
    fun openCheckIn(id: String) {
        val (date, time) = CheckInSchedule.parse(id) ?: return
        if (!open || stage != JourneyStage.READY) return
        val day = Weekday.of(date)
        val item = plan?.item(day, time) ?: return
        openItem = null
        shareOpen = false
        route = JourneyRoute.CHAT
        describe()
        report(journey.events.checkIn, JourneyText.fill(journey.checkIns.detail, journey, item, day))
    }

    /** "Simulate reminder": the next check-in the plan makes — or, when none is left this week, today's first. */
    fun simulateReminder() {
        val p = plan ?: return
        val next = CheckInSchedule.build(p, journey, settings.copy(reminders = true, quietStart = "00:00", quietEnd = "00:00"), Instant.fromEpochMilliseconds(now()), zone)
            .firstOrNull()
        val id = next?.id ?: p.items(Weekday.of(today())).firstOrNull { it.kind in journey.checkIns.kinds }?.let { CheckInSchedule.id(today(), it.time) }
        if (id == null) {
            notice = "The plan has no items to check in on."
            return
        }
        openCheckIn(id)
    }

    /** The check-in reminders, from the plan and the settings, replacing what was scheduled. None while signed out. */
    fun rescheduleReminders() {
        val list = if (open && stage == JourneyStage.READY) CheckInSchedule.build(plan, journey, settings, Instant.fromEpochMilliseconds(now()), zone) else emptyList()
        reminders.replaceAll(list)
    }

    fun setReminders(on: Boolean) = updateSettings(settings.copy(reminders = on))

    fun setQuietHours(start: String, end: String) {
        val s = JourneyTime.normalise(start) ?: return
        val e = JourneyTime.normalise(end) ?: return
        updateSettings(settings.copy(quietStart = s, quietEnd = e))
    }

    fun setTestingTools(on: Boolean) = updateSettings(settings.copy(testingTools = on))

    private fun updateSettings(next: JourneySettings) {
        settings = next
        repo.saveSettings(next)
        rescheduleReminders()
    }

    // ── Today, My week, Progress ────────────────────────────────────────────

    fun todayDate(): LocalDate = today()
    fun timeNow(): String = nowTime()
    fun nowMs(): Long = now()

    /** An item marked done (or not) in Today: kept as a check-in, and the coach told (`Graine.track`). */
    fun markDone(ref: ItemRef, done: Boolean) {
        val item = plan?.item(ref.day, ref.time) ?: return
        val date = dateOf(ref.day)
        saveActivity(activity.copy(checkIns = activity.checkIns + CheckIn(date, ref.time, done, null, "app", now())))
        sdk.track(journey.events.itemDone, linkedMapOf("day" to ref.day.wire, "time" to ref.time, "title" to item.title, "done" to done))
    }

    /** Today's counter, one step up (or down). */
    fun addToTracker(steps: Int = 1) {
        val t = journey.today.tracker ?: return
        val date = today()
        val next = ((activity.tracker[date] ?: 0.0) + steps * t.step).coerceAtLeast(0.0)
        saveActivity(activity.copy(tracker = activity.tracker + (date to kotlin.math.round(next * 100) / 100)))
    }

    /** A new value of the pack's measure, today's; outside its range it is refused, with why. */
    fun logMeasure(text: String): String? {
        val m = journey.progress.measure ?: return "Nothing to log."
        val v = Args.number(text) ?: return "Enter a number."
        if (v < m.min || v > m.max) return "Between ${Args.numberText(m.min)} and ${Args.numberText(m.max)} ${m.unit}."
        val date = today()
        saveActivity(activity.copy(measures = activity.measures.filter { it.date != date } + MeasureEntry(date, v)))
        return null
    }

    /** The measure's values, oldest first — the profile's answer first, when it has one and nothing was logged that day. */
    fun measureTrend(): List<MeasureEntry> {
        val m = journey.progress.measure ?: return emptyList()
        val logged = activity.measures.sortedBy { it.date }
        val start = m.profileField?.let { profile.values[it] }?.let { Args.number(it) }
        val created = account?.createdAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }
        return if (start != null && created != null && logged.none { it.date <= created }) listOf(MeasureEntry(created, start)) + logged else logged
    }

    /** The weekly 1–5 check-in: kept for this week, and the coach told (`Graine.track("weekly_check_in", mapOf("score" to n))`). */
    fun weeklyCheckIn(score: Int) {
        if (score !in 1..5) return
        saveActivity(activity.copy(weekly = activity.weekly + (JourneyStats.weekOf(today()) to score)))
        sdk.track(journey.events.weeklyCheckIn, linkedMapOf("score" to score))
    }

    val weeklyScore: Int? get() = activity.weekly[JourneyStats.weekOf(today())]

    /** Kept bounded by the repository; the screens draw what it kept. */
    private fun saveActivity(next: JourneyActivity) {
        repo.saveActivity(next, today())
        activity = repo.activity
    }

    /** The date a plan day means now: today for today's weekday, else its most recent date. */
    private fun dateOf(day: Weekday): LocalDate {
        val today = today()
        val back = (today.dayOfWeek.ordinal - day.dayOfWeek.ordinal + 7) % 7
        return LocalDate.fromEpochDays(today.toEpochDays() - back)
    }

    // ── navigation ──────────────────────────────────────────────────────────

    fun go(next: JourneyRoute) {
        route = next
        openItem = null
        shareOpen = false
        if (next == JourneyRoute.WEEK) planFresh = false
        describe()
    }

    fun openItem(ref: ItemRef) {
        if (plan?.item(ref.day, ref.time) == null) return
        openItem = ref
        describe()
    }

    fun closeItem() {
        openItem = null
        describe()
    }

    fun openShare(open: Boolean) {
        shareOpen = open
        describe()
    }

    /** The plan as text, for the share sheet. */
    fun sharePlan() {
        val p = plan ?: return
        share.shareText(journey.plan.shareTitle, PlanText.share(journey, p))
    }

    /**
     * "Use selected swap": the item changed in the plan (the old one becomes a swap to go back to), the reminders
     * follow, and the coach is told (`Graine.track("item_swapped", …)`).
     */
    fun useSwap(ref: ItemRef, swap: String) {
        val p = plan ?: return
        val item = p.item(ref.day, ref.time) ?: return
        if (swap !in item.swaps) return
        val next = item.swappedFor(swap, null, emptyMap(), reason = "Chosen in the app")
        planArrived(p.replace(ref.day, ref.time, next).copy(savedAt = now()))
        sdk.track(journey.events.itemSwapped, linkedMapOf("day" to ref.day.wire, "time" to ref.time, "from" to item.title, "to" to swap))
        notice = "${journey.plan.kindLabel(item.kind)} on ${ref.day.label}: $swap."
    }

    /** "Ask about this": the coach's chat, its composer started with the item. */
    fun askAbout(ref: ItemRef) {
        val item = plan?.item(ref.day, ref.time) ?: return
        chatDraft = "About ${journey.plan.kindLabel(item.kind).lowercase()} on ${ref.day.label} (${item.title}): "
        go(JourneyRoute.CHAT)
    }

    /** The call button in the chat's header: `Graine.calls.startCall(reason = "coach_call")`, the coach being the default agent. */
    fun callCoach() {
        val id = sdk.startCall(journey.coach.callReason, JourneyAgent.COACH)
        if (id == null) notice = "Another call is in progress."
    }

    // ── the call screen's controls ──────────────────────────────────────────

    fun answerCall() = sdk.answer()
    fun declineCall() = sdk.decline()
    fun endCall() = sdk.end()
    fun setMuted(muted: Boolean) = sdk.setMuted(muted)
    fun setSpeaker(on: Boolean) = sdk.setSpeaker(on)

    /** Language changed in Account: the agents hear it from their next conversation. */
    fun changeLanguage(id: String) = chooseLanguage(id)

    // ── 6. privacy: download my data ────────────────────────────────────────

    /**
     * Download my data: what the coach remembers (`Graine.memories()`), the user's conversations with each agent
     * (`chat.pastSessions`, `sessionTranscript(id)`), and everything this phone keeps — one JSON file, to the share
     * sheet. Bounded: the newest [SESSIONS_PER_AGENT] conversations an agent.
     */
    fun exportData() {
        if (exporting) return
        val acc = account ?: return
        exporting = true
        scope.launch {
            val problems = mutableListOf<String>()
            val memories = attempt { sdk.memories() }.onFailure { problems += "memories: ${CallText.message(it)}" }.getOrNull().orEmpty()
            val sessions = LinkedHashMap<String, List<Map<String, Any?>>>()
            for (agent in JourneyAgent.entries) {
                if (!sdk.isConfigured(agent)) continue
                val list = attempt { sdk.pastSessions(agent, SESSIONS_PER_AGENT) }
                    .onFailure { problems += "${agent.sdkName} conversations: ${CallText.message(it)}" }.getOrNull().orEmpty()
                sessions[agent.sdkName] = list.map { s ->
                    val t = attempt { sdk.sessionTranscript(agent, s.id) }.getOrNull()
                    JourneyExport.session(s, t)
                }
            }
            val text = JourneyExport.build(
                journey = journey,
                account = acc,
                profile = profile,
                intake = intake,
                plan = plan,
                activity = activity,
                memories = memories,
                sessions = sessions,
                problems = problems,
                exportedAt = now(),
            )
            exporting = false
            share.shareFile("my-data.json", text)
        }
    }

    // ── the actions the agents call (the app is the store) ─────────────────

    /** The journey's actions, by the pack's names: what the first call learned on its agent, the plan's on the coach. */
    private fun registerActions() {
        val a = journey.actions
        sdk.unregisterActions()
        sdk.registerAction(JourneyAgent.FIRST_CALL, a.saveIntake) { saveIntake(it) }
        sdk.registerAction(JourneyAgent.COACH, a.getIntake) { getIntake() }
        sdk.registerAction(JourneyAgent.COACH, a.savePlan) { savePlan(it) }
        sdk.registerAction(JourneyAgent.COACH, a.getPlan) { getPlan(it) }
        sdk.registerAction(JourneyAgent.COACH, a.swapItem) { swapItem(it) }
        sdk.registerAction(JourneyAgent.COACH, a.logCheckIn) { logCheckIn(it) }
    }

    private fun signedIn(): JourneyAnswer? =
        if (account?.signedIn == true) null else JourneyAnswer.refused("Nobody is signed in to this app's journey on this phone. Ask the user to sign in.")

    /** The first call's save: everything it learned, as sent (bounded), and — from its chat version — the plan next. */
    internal fun saveIntake(args: Map<String, Any?>): JourneyAnswer {
        signedIn()?.let { return it }
        val values = IntakeRules.clean(args)
        if (values.isEmpty()) return JourneyAnswer.refused("Nothing was sent. Call it once more with everything the user told you.")
        val missing = journey.intakeRequired.filter { Args.string(values[it]) == null && Args.list(values[it]).isNullOrEmpty() }
        if (missing.isNotEmpty()) {
            return JourneyAnswer.refused("Missing: ${missing.joinToString(", ")}. Ask the user, then call it again with everything they told you.")
        }
        val saved = Intake(values, now())
        intake = saved
        repo.saveIntake(saved)
        when (stage) {
            JourneyStage.FIRST_CALL_CHAT -> buildPlan()
            JourneyStage.COULD_NOT_CONNECT -> if (noConnect == NoConnect.ENDED_EARLY) buildPlan()
            // The call already ended and was waiting for this save.
            JourneyStage.IN_CALL -> if (endedGrace?.isActive == true) buildPlan()
            else -> Unit
        }
        return JourneyAnswer.ok("Saved ${values.size} answers. The app will build the plan from them.")
    }

    internal fun getIntake(): JourneyAnswer {
        signedIn()?.let { return it }
        val i = intake ?: return JourneyAnswer.ok("No consultation is saved yet.", mapOf("has_consultation" to false))
        return JourneyAnswer.ok("The user's consultation.", LinkedHashMap(i.values).apply { put("has_consultation", true) })
    }

    /** The whole week, saved: the plan card and My week read it, and the reminders follow it. */
    internal fun savePlan(args: Map<String, Any?>): JourneyAnswer {
        signedIn()?.let { return it }
        return when (val r = Plan.parse(args, now())) {
            is Plan.Parsed.Refused -> JourneyAnswer.refused(r.message)
            is Plan.Parsed.Ok -> {
                planArrived(r.plan)
                val notes = if (r.notes.isEmpty()) "" else " " + r.notes.joinToString(" ")
                JourneyAnswer.ok(
                    "Saved \"${r.plan.title}\": ${r.plan.days.size} days, ${r.plan.itemCount} items. The user sees it in the app now.$notes",
                    mapOf("days" to r.plan.days.size, "items" to r.plan.itemCount),
                )
            }
        }
    }

    internal fun getPlan(args: Map<String, Any?>): JourneyAnswer {
        signedIn()?.let { return it }
        val p = plan ?: return JourneyAnswer.ok("No plan is saved yet.", mapOf("has_plan" to false))
        val raw = args["day"]
        val day = Args.weekday(raw)
        if (Args.string(raw) != null && day == null) return JourneyAnswer.refused("\"${Args.string(raw)}\" is not a day: use mon, tue, wed, thu, fri, sat or sun.")
        val data = LinkedHashMap(p.toWire(day)).apply {
            put("has_plan", true)
            put("today", Weekday.of(today()).wire)
            put("done_today", plan?.items(Weekday.of(today())).orEmpty().filter { activity.followed(today(), it.time) == true }.map { it.time })
        }
        return JourneyAnswer.ok(if (day == null) "The whole week." else "${day.label}.", data)
    }

    /** One item changed: the agent names the day and time, and sends the new item. */
    internal fun swapItem(args: Map<String, Any?>): JourneyAnswer {
        signedIn()?.let { return it }
        val p = plan ?: return JourneyAnswer.refused("There is no plan yet to change. Build the week and save it first.")
        val day = Args.weekday(args["day"]) ?: return JourneyAnswer.refused("Say which day: mon, tue, wed, thu, fri, sat or sun.")
        val time = Args.time(args["time"]) ?: return JourneyAnswer.refused("Say the item's time as it is on the plan, e.g. 08:00.")
        val title = Args.string(args["title"]) ?: return JourneyAnswer.refused("Say what the new item is (title).")
        val item = p.item(day, time) ?: return JourneyAnswer.refused(
            "There is no item at $time on ${day.label}. That day: " +
                (p.items(day).joinToString(", ") { "${it.time} ${it.title}" }.ifEmpty { "nothing planned" }) + ".",
        )
        val figures = args.filterKeys { it !in SWAP_KEYS }.mapNotNull { (k, v) -> Args.number(v)?.let { k to it } }.toMap()
        val next = item.swappedFor(title, Args.string(args["details"]), figures, Args.string(args["reason"]))
        planArrived(p.replace(day, time, next).copy(savedAt = now()))
        return JourneyAnswer.ok("Changed ${day.label} $time: ${item.title} → ${next.title}. The user sees it in the app now.")
    }

    /** How an item went, from what the user told the coach: kept for the day it means, and counted in Progress. */
    internal fun logCheckIn(args: Map<String, Any?>): JourneyAnswer {
        signedIn()?.let { return it }
        val day = Args.weekday(args["day"]) ?: return JourneyAnswer.refused("Say which day: mon, tue, wed, thu, fri, sat or sun.")
        val followed = Args.bool(args["followed"]) ?: return JourneyAnswer.refused("Say whether they followed it (followed: true or false).")
        val rawTime = Args.string(args["time"])
        val time = rawTime?.let { JourneyTime.normalise(it) ?: return JourneyAnswer.refused("\"$it\" is not a time: use the item's time as on the plan, e.g. 08:00.") }
        val note = Args.string(args["note"])?.let { Args.clip(it, 200) }
        val date = dateOf(day)
        saveActivity(activity.copy(checkIns = activity.checkIns + CheckIn(date, time, followed, note, "agent", now())))
        val item = time?.let { plan?.item(day, it) }
        val what = item?.title ?: time ?: "the day"
        val off = if (time != null && item == null) " (no plan item at $time that day)" else ""
        return JourneyAnswer.ok("Logged: ${day.label}, $what — ${if (followed) "followed" else "not followed"}$off.")
    }

    // ── what is on screen ───────────────────────────────────────────────────

    /**
     * Tell the agents what the user is looking at — `Graine.setScreen(...)`: on My week the day and its items, on an
     * item its day, time and title; elsewhere the journey's step. Called on every change of screen.
     */
    fun describe() {
        if (!open) return
        sdk.setScreen(screen())
    }

    fun screen(): GraineScreen {
        val p = plan
        val ref = openItem
        val item = ref?.let { p?.item(it.day, it.time) }
        return when {
            stage == JourneyStage.ONBOARDING -> GraineScreen("onboarding_${onboardingStep.wire}", title = journey.onboarding.welcome.title)
            stage == JourneyStage.BUILDING_PLAN -> GraineScreen("plan_building", title = journey.plan.building.title)
            stage != JourneyStage.READY -> GraineScreen(
                screen = "first_call",
                title = journey.firstCall.title,
                fields = listOfNotNull(
                    GraineField("step", "Step", stage.wire, GraineFieldStatus.FILLED),
                    noConnect?.let { GraineField("could_not_connect", "Couldn't connect", it.wire, GraineFieldStatus.FILLED) },
                ),
            )
            item != null && ref != null -> GraineScreen(
                screen = "plan_item",
                title = item.title,
                fields = listOf(
                    GraineField("day", "Day", ref.day.label, GraineFieldStatus.FILLED),
                    GraineField("time", "Time", ref.time, GraineFieldStatus.FILLED),
                    GraineField("kind", "Kind", journey.plan.kindLabel(item.kind), GraineFieldStatus.FILLED),
                    GraineField("item", "Item", item.title, GraineFieldStatus.FILLED),
                ),
                data = linkedMapOf("day" to ref.day.wire, "time" to ref.time, "swaps" to item.swaps),
            )
            route == JourneyRoute.WEEK && p != null -> {
                val day = weekDay
                GraineScreen(
                    screen = "plan_week",
                    title = journey.plan.weekTitle,
                    fields = listOf(GraineField("day", "Day", day.label, GraineFieldStatus.FILLED)) +
                        p.items(day).map { GraineField("item_${it.time.replace(":", "")}", it.time, it.title, GraineFieldStatus.FILLED) },
                    data = linkedMapOf("day" to day.wire),
                )
            }
            else -> GraineScreen(route.name.lowercase(), title = route.name.lowercase().replaceFirstChar { it.uppercase() })
        }
    }

    fun showDay(day: Weekday) {
        weekDay = day
        describe()
    }

    // ── private ─────────────────────────────────────────────────────────────

    private fun setProgress(change: (JourneyProgress) -> JourneyProgress) {
        val next = change(repo.progress)
        repo.saveProgress(next)
        stage = next.stage
        onboardingStep = next.onboardingStep
        noConnect = next.noConnect
        booked = next.booked
        if (open) rescheduleRemindersIfReady(next.stage)
    }

    private fun rescheduleRemindersIfReady(next: JourneyStage) {
        if (next == lastStage) return
        lastStage = next
        if (next == JourneyStage.READY) rescheduleReminders()
    }

    /** A suspending SDK call as a Result — never swallowing cancellation. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    companion object {
        /** How long after a first call ends its save may still arrive. */
        const val INTAKE_GRACE_MS = 4_000L
        /** When the progress screen says the plan is slow. */
        const val PLAN_SLOW_MS = 90_000L
        /** A booked call Graine no longer lists, this long after its time, is over. */
        const val BOOKED_GONE_MS = 20 * 60_000L
        const val SESSIONS_PER_AGENT = 10
        /** How long log out waits for a booked first call to be called off before it goes on regardless. */
        const val CANCEL_WAIT_MS = 5_000L
        const val NO_FIRST_CALL_AGENT = "The first-call agent is not set up in this build: add its publishable key (graine.consultationPublishableKey)."

        private val LIVE = setOf(GraineCallState.RINGING, GraineCallState.CONNECTING, GraineCallState.ACTIVE)
        private val FIRST_CALL_STAGES = setOf(JourneyStage.FIRST_CALL, JourneyStage.BOOKED, JourneyStage.IN_CALL, JourneyStage.COULD_NOT_CONNECT, JourneyStage.FIRST_CALL_CHAT)
        private val WAITING_FOR_A_RING = setOf(JourneyStage.BOOKED, JourneyStage.IN_CALL, JourneyStage.COULD_NOT_CONNECT)
        private val SWAP_KEYS = setOf("day", "time", "title", "details", "reason")

        /** A new user's id on this phone: random, never derived from the device. */
        fun newId(): String = "user-" + (1..16).joinToString("") { Random.nextInt(16).toString(16) }
    }
}

/** What the first call learned, as the agent sent it — bounded, so a runaway answer never fills the phone. */
object IntakeRules {
    const val KEYS_MAX = 30
    const val TEXT_MAX = 500
    const val LIST_MAX = 20

    fun clean(args: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in args) {
            if (out.size >= KEYS_MAX) break
            val value: Any? = when (v) {
                is String -> v.trim().takeIf { it.isNotEmpty() }?.let { Args.clip(it, TEXT_MAX) }
                is Number -> Args.number(v)
                is Boolean -> v
                is List<*> -> v.mapNotNull { e ->
                    when (e) {
                        is String -> e.trim().takeIf { it.isNotEmpty() }?.let { Args.clip(it, 120) }
                        is Number -> Args.number(e)
                        is Boolean -> e
                        else -> null
                    }
                }.take(LIST_MAX).takeIf { it.isNotEmpty() }
                is Map<*, *> -> Args.map(v)?.mapValues { (_, x) -> Args.string(x)?.let { Args.clip(it, 120) } }?.filterValues { it != null }?.takeIf { it.isNotEmpty() }
                else -> null
            }
            if (value != null) out[k] = value
        }
        return out
    }
}

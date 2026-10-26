package ai.graine.demo.ui.journey

import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyPack
import ai.graine.demo.journey.OnboardingStep
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * ONBOARDING: welcome, the profile (`identify(user_id, name)` and `setVariables(…)` on Continue), the language
 * (`agent_language`), then the microphone (`Graine.calls.requestCallPermissions()`) and notifications.
 */
@Composable
fun OnboardingScreens(journey: JourneyModel) {
    val j = journey.journey
    val count = OnboardingStep.entries.size
    val step = "${journey.onboardingStep.ordinal + 1} of $count"
    when (journey.onboardingStep) {
        OnboardingStep.WELCOME -> StepScreen(
            title = j.onboarding.welcome.title,
            body = j.onboarding.welcome.body,
            step = step,
            footer = {
                PrimaryButton("Start", { journey.onboardingNext() }, tag = "journey_welcome_start")
                j.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
        OnboardingStep.PROFILE -> ProfileStep(journey, step)
        OnboardingStep.LANGUAGE -> LanguageStep(journey, step, onBack = { journey.onboardingBack() })
        OnboardingStep.MICROPHONE -> StepScreen(
            title = j.onboarding.microphone.title,
            body = j.onboarding.microphone.body,
            step = step,
            onBack = { journey.onboardingBack() },
            footer = {
                PrimaryButton(j.onboarding.microphone.allow, { journey.allowMicrophone() }, busy = journey.busy, tag = "journey_allow_microphone")
                QuietButton(j.onboarding.microphone.skip, { journey.onboardingNext() })
            },
        )
        OnboardingStep.NOTIFICATIONS -> StepScreen(
            title = j.onboarding.notifications.title,
            body = j.onboarding.notifications.body,
            step = step,
            onBack = { journey.onboardingBack() },
            footer = {
                PrimaryButton(j.onboarding.notifications.allow, { journey.allowNotifications() }, busy = journey.busy, tag = "journey_allow_notifications")
                QuietButton(j.onboarding.notifications.skip, { journey.onboardingNext() })
            },
        )
    }
}

@Composable
private fun ProfileStep(journey: JourneyModel, step: String) {
    val j = journey.journey
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(journey.profile.values) } }
    var problems by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    StepScreen(
        title = j.onboarding.profileTitle,
        body = j.onboarding.profileBody,
        step = step,
        onBack = { journey.onboardingBack() },
        footer = {
            PrimaryButton("Continue", { problems = journey.saveProfile(values.toMap()) }, tag = "journey_profile_continue")
        },
    ) {
        ProfileFields(j.onboarding.fields, values, problems, journey.todayDate())
    }
}

/** The pack's profile questions, each drawn by its type; [problems] under the ones that cannot be kept. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileFields(fields: List<JourneyPack.ProfileField>, values: MutableMap<String, String>, problems: Map<String, String>, today: LocalDate) {
    for (f in fields) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (f.type) {
                JourneyPack.FieldType.TEXT -> OutlinedTextField(
                    value = values[f.key].orEmpty(),
                    onValueChange = { v -> values[f.key] = f.maxLength?.let { v.take(it) } ?: v },
                    label = { Text(f.label) },
                    placeholder = f.placeholder?.let { { Text(it) } },
                    singleLine = true,
                    isError = f.key in problems,
                    modifier = Modifier.fillMaxWidth().testTag("journey_field_${f.key}"),
                )
                JourneyPack.FieldType.NUMBER -> OutlinedTextField(
                    value = values[f.key].orEmpty(),
                    onValueChange = { v -> values[f.key] = v.filter { it.isDigit() || it == '.' }.take(7) },
                    label = { Text(f.label) },
                    suffix = f.unit?.let { { Text(it) } },
                    singleLine = true,
                    isError = f.key in problems,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("journey_field_${f.key}"),
                )
                JourneyPack.FieldType.CHOICE -> {
                    Text(f.label, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.options.forEach { o ->
                            FilterChip(
                                selected = values[f.key] == o.value,
                                onClick = { values[f.key] = o.value },
                                label = { Text(o.label) },
                                modifier = Modifier.testTag("journey_option_${f.key}_${o.value}"),
                            )
                        }
                    }
                }
                JourneyPack.FieldType.DATE -> DateField(f, values, today)
            }
            problems[f.key]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(f: JourneyPack.ProfileField, values: MutableMap<String, String>, today: LocalDate) {
    var open by remember { mutableStateOf(false) }
    val chosen = values[f.key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Text(f.label, style = MaterialTheme.typography.titleSmall)
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("journey_field_${f.key}")) {
        Text(chosen?.let { "${it.dayOfMonth} ${MONTHS[it.monthNumber - 1]} ${it.year}" } ?: "Choose a date")
    }
    if (open) {
        val youngest = today.minus((f.min ?: 0.0).toInt(), DateTimeUnit.YEAR)
        val oldest = today.minus((f.max ?: 120.0).toInt(), DateTimeUnit.YEAR)
        val start = chosen ?: today.minus(30, DateTimeUnit.YEAR)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            yearRange = oldest.year..youngest.year,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> values[f.key] = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.toString() }
                    open = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** The languages the pack offers; a change later (Account) goes the same way. */
@Composable
fun LanguageStep(journey: JourneyModel, step: String?, onBack: () -> Unit) {
    val language = journey.journey.onboarding.language
    StepScreen(title = language.title, body = language.body, step = step, onBack = onBack) {
        LanguageChoices(journey)
    }
}

@Composable
fun LanguageChoices(journey: JourneyModel) {
    journey.journey.onboarding.language.options.forEach { o ->
        Row(
            Modifier.fillMaxWidth().clickable { journey.chooseLanguage(o.id) }.padding(vertical = 8.dp).testTag("journey_language_${o.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = journey.profile.languageId == o.id, onClick = { journey.chooseLanguage(o.id) })
            Text(o.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

internal val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

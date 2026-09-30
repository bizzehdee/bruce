package com.bizzeh.bruce.reminders

import android.Manifest
import android.os.Build
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * The reminders skill (TASK-094): a notification at a time the user gave. Reminders are listed and
 * cancelled in Settings, Reminders. Off by default; needs the notification permission from Android 13.
 * [inexact] is told when a reminder may be late, so the user can be offered exact timing once.
 */
class ReminderSkills(
    private val store: ReminderStore,
    private val inexact: () -> Unit = {},
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdk: Int = Build.VERSION.SDK_INT,
) {
    fun create(): List<Skill> = listOf(remind())

    private fun remind() = Skill(
        id = "set_reminder",
        version = 1,
        description = "Remind the user with a notification at a time, given either as a date and time or in minutes from now.",
        input = InputSchema(
            listOf(
                Parameter(TEXT, ParameterType.STRING, "What to remind them of", maxLength = MAX_TEXT),
                Parameter(AT, ParameterType.STRING, "When, YYYY-MM-DDTHH:MM in the phone's time", required = false, maxLength = 16),
                Parameter(IN_MINUTES, ParameterType.INTEGER, "Or in how many minutes from now", required = false, minimum = 1.0, maximum = MAX_MINUTES.toDouble()),
            ),
        ),
        capabilities = setOf(Capability.TIME),
        defaultState = SkillState.DECLINED,
        androidPermissions = if (sdk >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
    ) { arguments -> remind(arguments) }

    private suspend fun remind(arguments: SkillArguments): SkillOutcome {
        val text = arguments.string(TEXT)?.trim().orEmpty().ifEmpty { return invalid("Say what to remind them of.") }
        val now = clock()
        val at = arguments.long(IN_MINUTES)?.let { now + it * MINUTE_MS }
            ?: arguments.string(AT)?.let { time(it) ?: return invalid("The time must be YYYY-MM-DDTHH:MM.") }
            ?: return invalid("Give a time, or minutes from now.")
        if (at <= now) return invalid("That time has passed.")
        if (at - now > MAX_MINUTES * MINUTE_MS) return invalid("Reminders can be at most a year ahead.")
        val (_, exact) = store.add(text, at)
        val shown = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(at).atZone(zone()))
        if (!exact) inexact()
        return SkillOutcome.Done("Reminder set for $shown." + if (exact) "" else " Android may deliver it a few minutes late, as exact alarms are not allowed.")
    }

    private fun time(text: String): Long? = try {
        LocalDateTime.parse(text).atZone(zone()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    private fun invalid(reason: String) = SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, reason, retryable = true)

    private companion object {
        const val TEXT = "text"
        const val AT = "at"
        const val IN_MINUTES = "in_minutes"
        const val MAX_TEXT = 500
        const val MINUTE_MS = 60_000L
        const val MAX_MINUTES = 366 * 24 * 60L
    }
}

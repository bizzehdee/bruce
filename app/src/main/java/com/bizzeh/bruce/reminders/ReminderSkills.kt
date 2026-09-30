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
    private val tasks: ScheduledTasks? = null,
    private val inexact: () -> Unit = {},
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdk: Int = Build.VERSION.SDK_INT,
) {
    fun create(): List<Skill> = listOfNotNull(remind(), tasks?.let { schedule(it) })

    /**
     * Scheduled tasks (TASK-095). Every one is approved by the user, as a task later runs on its
     * own with the skills the user allows without asking.
     */
    private fun schedule(tasks: ScheduledTasks) = Skill(
        id = "schedule_task",
        version = 1,
        description = "Schedule a request for Bruce to answer later, once or every day or week; the answer arrives as a notification and a chat.",
        input = InputSchema(
            listOf(
                Parameter(PROMPT, ParameterType.STRING, "The request to answer then, as the user would ask it", maxLength = MAX_TEXT),
                Parameter(AT, ParameterType.STRING, "When, YYYY-MM-DDTHH:MM in the phone's time", required = false, maxLength = 16),
                Parameter(IN_MINUTES, ParameterType.INTEGER, "Or in how many minutes from now", required = false, minimum = 1.0, maximum = MAX_MINUTES.toDouble()),
                Parameter(REPEAT, ParameterType.STRING, "once, daily or weekly", required = false, allowed = Repeat.entries.map { it.name.lowercase() }),
            ),
        ),
        capabilities = setOf(Capability.TIME),
        defaultState = SkillState.DECLINED,
        confirmEveryUse = true,
        androidPermissions = if (sdk >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
    ) { arguments ->
        val prompt = arguments.string(PROMPT)?.trim().orEmpty().ifEmpty { return@Skill invalid("Say what to do then.") }
        val at = when (val time = when_(arguments)) {
            is When.Invalid -> return@Skill invalid(time.reason)
            is When.At -> time.millis
        }
        val repeat = Repeat.valueOf(arguments.string(REPEAT)?.uppercase() ?: Repeat.ONCE.name)
        tasks.add(prompt, at, repeat)
        val how = when (repeat) {
            Repeat.ONCE -> ""
            Repeat.DAILY -> ", then every day"
            Repeat.WEEKLY -> ", then every week"
        }
        SkillOutcome.Done("Scheduled for ${shown(at)}$how. With the phone's own model it runs once the phone is charging.")
    }

    private sealed interface When {
        data class At(val millis: Long) : When

        data class Invalid(val reason: String) : When
    }

    private fun when_(arguments: SkillArguments): When {
        val now = clock()
        val at = arguments.long(IN_MINUTES)?.let { now + it * MINUTE_MS }
            ?: arguments.string(AT)?.let { time(it) ?: return When.Invalid("The time must be YYYY-MM-DDTHH:MM.") }
            ?: return When.Invalid("Give a time, or minutes from now.")
        if (at <= now) return When.Invalid("That time has passed.")
        if (at - now > MAX_MINUTES * MINUTE_MS) return When.Invalid("That is more than a year ahead.")
        return When.At(at)
    }

    private fun shown(at: Long): String = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(at).atZone(zone()))

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
        val at = when (val time = when_(arguments)) {
            is When.Invalid -> return invalid(time.reason)
            is When.At -> time.millis
        }
        val (_, exact) = store.add(text, at)
        if (!exact) inexact()
        return SkillOutcome.Done("Reminder set for ${shown(at)}." + if (exact) "" else " Android may deliver it a few minutes late, as exact alarms are not allowed.")
    }

    private fun time(text: String): Long? = try {
        LocalDateTime.parse(text).atZone(zone()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    private fun invalid(reason: String) = SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, reason, retryable = true)

    private companion object {
        const val TEXT = "text"
        const val PROMPT = "prompt"
        const val REPEAT = "repeat"
        const val AT = "at"
        const val IN_MINUTES = "in_minutes"
        const val MAX_TEXT = 500
        const val MINUTE_MS = 60_000L
        const val MAX_MINUTES = 366 * 24 * 60L
    }
}

package com.bizzeh.bruce.skills.calendar

import android.Manifest
import com.bizzeh.bruce.policy.ResourceTarget
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One calendar change, worked out from the model's arguments before it is shown or made. */
sealed interface CalendarPlan {
    data class Invalid(val reason: String) : CalendarPlan

    data class Create(val calendar: CalendarInfo, val fields: EventFields) : CalendarPlan

    data class Update(val event: CalendarEvent, val fields: EventFields) : CalendarPlan

    data class Delete(val event: CalendarEvent) : CalendarPlan
}

/**
 * Calendar skills (TASK-086): reading, and creating, changing or deleting events in calendars the
 * phone can write. All are off by default; changes are asked about every time (owner, 2026-09-30),
 * with the approval showing exactly what changes and bound to the event as it was. Recurring events
 * are listed but not changed: a change would reach every occurrence.
 */
class CalendarSkills(
    private val store: CalendarStore,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun create(): List<Skill> = listOf(list(), createEvent(), updateEvent(), deleteEvent())

    /** The policy engine's scope check for changes: what the approval card shows and binds. */
    suspend fun check(request: SkillRequest): ScopeCheck = withContext(io) {
        when (val plan = plan(request.skill.id, request.arguments)) {
            is CalendarPlan.Invalid -> ScopeCheck.OutOfScope(plan.reason)
            is CalendarPlan.Create -> ScopeCheck.InScope(listOf(ResourceTarget("New event: ${describe(plan.fields)} (${plan.calendar.name})", "calendar:${plan.calendar.id}:new")))
            is CalendarPlan.Update -> ScopeCheck.InScope(
                listOf(ResourceTarget("Change: ${describe(plan.event)} → ${describe(plan.fields)}", identity(plan.event))),
            )
            is CalendarPlan.Delete -> ScopeCheck.InScope(listOf(ResourceTarget("Delete: ${describe(plan.event)}", identity(plan.event))))
        }
    }

    private fun list() = Skill(
        id = "list_events",
        version = 1,
        description = "List calendar events from a date for some days. Gives each event's id for changes.",
        input = InputSchema(
            listOf(
                Parameter(FROM, ParameterType.STRING, "First day, YYYY-MM-DD; today if left out", required = false, maxLength = 10),
                Parameter(DAYS, ParameterType.INTEGER, "How many days, 1 to $MAX_LIST_DAYS; 7 if left out", required = false, minimum = 1.0, maximum = MAX_LIST_DAYS.toDouble()),
            ),
        ),
        capabilities = setOf(Capability.CALENDAR_READ),
        defaultState = SkillState.DECLINED,
        androidPermissions = listOf(Manifest.permission.READ_CALENDAR),
    ) { arguments -> withContext(io) { listEvents(arguments) } }

    private fun timeParameters(required: Boolean) = listOf(
        Parameter(START, ParameterType.STRING, "Start, YYYY-MM-DDTHH:MM in the phone's time, or YYYY-MM-DD for an all-day event", required = required, maxLength = 16),
        Parameter(END, ParameterType.STRING, "End in the same form; an hour (or one day) after the start if left out", required = false, maxLength = 16),
        Parameter(LOCATION, ParameterType.STRING, "Where", required = false, maxLength = MAX_TEXT),
        Parameter(NOTES, ParameterType.STRING, "Notes to keep with the event", required = false, maxLength = MAX_NOTES),
    )

    private fun createEvent() = Skill(
        id = "create_event",
        version = 1,
        description = "Add an event to the phone's calendar. The user approves each one.",
        input = InputSchema(
            listOf(Parameter(TITLE, ParameterType.STRING, "The event's title", maxLength = MAX_TEXT)) + timeParameters(required = true) +
                Parameter(CALENDAR, ParameterType.STRING, "Which calendar, by name; the main one if left out", required = false, maxLength = MAX_TEXT),
        ),
        capabilities = setOf(Capability.CALENDAR_WRITE),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.CALENDAR,
        androidPermissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        confirmEveryUse = true,
    ) { arguments -> withContext(io) { apply(plan(CREATE, arguments)) } }

    private fun updateEvent() = Skill(
        id = "update_event",
        version = 1,
        description = "Change an event from list_events: only the fields given change. The user approves each change.",
        input = InputSchema(
            listOf(
                Parameter(EVENT_ID, ParameterType.INTEGER, "The event's id from list_events", minimum = 1.0),
                Parameter(TITLE, ParameterType.STRING, "A new title", required = false, maxLength = MAX_TEXT),
            ) + timeParameters(required = false),
        ),
        capabilities = setOf(Capability.CALENDAR_WRITE),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.CALENDAR,
        androidPermissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        confirmEveryUse = true,
    ) { arguments -> withContext(io) { apply(plan(UPDATE, arguments)) } }

    private fun deleteEvent() = Skill(
        id = "delete_event",
        version = 1,
        description = "Delete an event from list_events. The user approves each deletion.",
        input = InputSchema(listOf(Parameter(EVENT_ID, ParameterType.INTEGER, "The event's id from list_events", minimum = 1.0))),
        capabilities = setOf(Capability.CALENDAR_WRITE),
        defaultState = SkillState.DECLINED,
        highRisk = true,
        scope = ResourceScope.CALENDAR,
        androidPermissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        confirmEveryUse = true,
    ) { arguments -> withContext(io) { apply(plan(DELETE, arguments)) } }

    private fun listEvents(arguments: SkillArguments): SkillOutcome {
        val zone = zone()
        val first = arguments.string(FROM)?.let { day(it) ?: return invalid("The date must be YYYY-MM-DD.") } ?: Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        val days = (arguments.long(DAYS) ?: DEFAULT_DAYS.toLong()).toInt()
        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = first.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val names = store.calendars().associate { it.id to it.name }
        val events = store.occurrences(from, to, MAX_LISTED + 1)
        if (events.isEmpty()) return SkillOutcome.Done("No events from ${first} for $days days.")
        val lines = events.take(MAX_LISTED).map { event ->
            "id ${event.id}: ${describe(event)}" + (names[event.calendarId]?.let { " [$it]" } ?: "") + (if (event.recurring) " (repeats)" else "")
        }
        return SkillOutcome.Done(lines.joinToString("\n") + if (events.size > MAX_LISTED) "\n[More events not shown; ask for fewer days.]" else "")
    }

    private fun apply(plan: CalendarPlan): SkillOutcome = when (plan) {
        is CalendarPlan.Invalid -> invalid(plan.reason)
        is CalendarPlan.Create -> store.insert(plan.calendar.id, plan.fields, zone().id)?.let { SkillOutcome.Done("Added the event (id $it).") } ?: refused()
        is CalendarPlan.Update -> if (store.update(plan.event.id, plan.fields, zone().id)) SkillOutcome.Done("Changed the event.") else refused()
        is CalendarPlan.Delete -> if (store.delete(plan.event.id)) SkillOutcome.Done("Deleted the event.") else refused()
    }

    fun plan(skill: String, arguments: SkillArguments): CalendarPlan = when (skill) {
        CREATE -> planCreate(arguments)
        UPDATE, DELETE -> {
            val event = arguments.long(EVENT_ID)?.let(store::event) ?: return CalendarPlan.Invalid("No event has that id. Use list_events for ids.")
            when {
                event.recurring -> CalendarPlan.Invalid("That event repeats, so Bruce does not change it; the user can change it in their calendar app.")
                store.calendars().none { it.id == event.calendarId && it.writable } -> CalendarPlan.Invalid("That event's calendar cannot be changed from this phone.")
                skill == DELETE -> CalendarPlan.Delete(event)
                else -> planUpdate(event, arguments)
            }
        }
        else -> CalendarPlan.Invalid("Not a calendar change.")
    }

    private fun planCreate(arguments: SkillArguments): CalendarPlan {
        val writable = store.calendars().filter { it.writable }
        val named = arguments.string(CALENDAR)
        val calendar = if (named != null) {
            writable.firstOrNull { it.name.equals(named, ignoreCase = true) } ?: return CalendarPlan.Invalid("No calendar on this phone that Bruce can add to has that name.")
        } else {
            writable.firstOrNull { it.primary } ?: writable.firstOrNull() ?: return CalendarPlan.Invalid("This phone has no calendar Bruce can add events to.")
        }
        val title = arguments.string(TITLE)?.trim().orEmpty().ifEmpty { return CalendarPlan.Invalid("The event needs a title.") }
        val times = times(arguments.string(START).orEmpty(), arguments.string(END)) ?: return CalendarPlan.Invalid(TIMES)
        return CalendarPlan.Create(calendar, EventFields(title, times.first, times.second, times.third, arguments.string(LOCATION).orEmpty(), arguments.string(NOTES).orEmpty()))
    }

    private fun planUpdate(event: CalendarEvent, arguments: SkillArguments): CalendarPlan {
        val start = arguments.string(START)
        val end = arguments.string(END)
        val times = when {
            start != null -> times(start, end) ?: return CalendarPlan.Invalid(TIMES)
            end != null -> {
                val newEnd = moment(end, event.allDay) ?: return CalendarPlan.Invalid(TIMES)
                if (newEnd <= event.start) return CalendarPlan.Invalid(TIMES)
                Triple(event.start, newEnd, event.allDay)
            }
            else -> Triple(event.start, event.end, event.allDay)
        }
        val fields = EventFields(
            title = arguments.string(TITLE)?.trim()?.ifEmpty { null } ?: event.title,
            start = times.first,
            end = times.second,
            allDay = times.third,
            location = arguments.string(LOCATION) ?: event.location,
            description = arguments.string(NOTES),
        )
        return if (listOf(arguments.string(TITLE), start, end, arguments.string(LOCATION), arguments.string(NOTES)).all { it == null }) {
            CalendarPlan.Invalid("Give at least one field to change.")
        } else {
            CalendarPlan.Update(event, fields)
        }
    }

    /** Start, end and whether all-day, from the model's text; null if they do not make an event. */
    private fun times(start: String, end: String?): Triple<Long, Long, Boolean>? {
        val allDay = !start.contains('T')
        val from = moment(start, allDay) ?: return null
        val to = end?.let { moment(it, allDay) ?: return null } ?: if (allDay) from + DAY_MS else from + HOUR_MS
        if (to <= from || to - from > MAX_EVENT_MS) return null
        return Triple(from, to, allDay)
    }

    /** An all-day moment is UTC midnight, as Android stores all-day events; others are the phone's own time. */
    private fun moment(text: String, allDay: Boolean): Long? = if (allDay) {
        day(text)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    } else {
        try {
            LocalDateTime.parse(text).atZone(zone()).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun day(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun describe(event: CalendarEvent) = describe(EventFields(event.title, event.start, event.end, event.allDay, event.location, null))

    private fun describe(fields: EventFields): String {
        val zone = if (fields.allDay) ZoneOffset.UTC else zone()
        val start = Instant.ofEpochMilli(fields.start).atZone(zone)
        val end = Instant.ofEpochMilli(fields.end).atZone(zone)
        val date = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
        val lastDay = end.minusNanos(1).toLocalDate()
        val span = when {
            fields.allDay && lastDay == start.toLocalDate() -> "${date.format(start)}, all day"
            fields.allDay -> "${date.format(start)} to ${date.format(lastDay)}, all day"
            end.toLocalDate() == start.toLocalDate() -> "${date.format(start)} ${time.format(start)}–${time.format(end)}"
            else -> "${date.format(start)} ${time.format(start)} to ${date.format(end)} ${time.format(end)}"
        }
        return "\"${fields.title}\", $span" + if (fields.location.isNotEmpty()) ", at ${fields.location}" else ""
    }

    /** An approval holds only while the event is as it was when the user was asked. */
    private fun identity(event: CalendarEvent) = "calendar-event:${event.id}:${event.start}:${event.end}:${event.title.hashCode()}:${event.location.hashCode()}"

    private fun invalid(reason: String) = SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, reason, retryable = true)

    private fun refused() = SkillOutcome.Failed(DenialCode.TOOL_FAILED, "The calendar did not accept the change.")

    companion object {
        private const val CREATE = "create_event"
        private const val UPDATE = "update_event"
        private const val DELETE = "delete_event"
        private const val FROM = "from"
        private const val DAYS = "days"
        private const val TITLE = "title"
        private const val START = "start"
        private const val END = "end"
        private const val LOCATION = "location"
        private const val NOTES = "notes"
        private const val CALENDAR = "calendar"
        private const val EVENT_ID = "event_id"
        private const val DEFAULT_DAYS = 7
        private const val MAX_LIST_DAYS = 31
        private const val MAX_LISTED = 50
        private const val MAX_TEXT = 200
        private const val MAX_NOTES = 2000
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val DAY_MS = 24 * HOUR_MS
        /** Longer than any meeting or trip; keeps a mistyped year from making a year-long event. */
        private const val MAX_EVENT_MS = 31 * DAY_MS
        private const val TIMES = "The times must be YYYY-MM-DDTHH:MM (or YYYY-MM-DD for all day), with the end after the start and within 31 days."
    }
}

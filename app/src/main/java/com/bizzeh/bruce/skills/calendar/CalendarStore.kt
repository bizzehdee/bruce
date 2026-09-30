package com.bizzeh.bruce.skills.calendar

/** A calendar on the phone; only [writable] ones take new or changed events. */
data class CalendarInfo(val id: Long, val name: String, val writable: Boolean, val primary: Boolean)

/** One occurrence of an event; [recurring] events are shown but not changed (TASK-086). Times are epoch milliseconds. */
data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val recurring: Boolean,
)

/**
 * What a new or changed event holds; all-day events start and end at UTC midnight, as Android
 * stores them. A null [description] leaves an event's notes as they are.
 */
data class EventFields(
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val description: String?,
)

/** The phone's calendars through CalendarContract; an interface so the skills are tested without a provider. */
interface CalendarStore {
    fun calendars(): List<CalendarInfo>

    /** Occurrences overlapping [from] to [to], earliest first, at most [limit]. */
    fun occurrences(from: Long, to: Long, limit: Int): List<CalendarEvent>

    fun event(id: Long): CalendarEvent?

    /** The new event's id, or null if the provider refused it. */
    fun insert(calendarId: Long, fields: EventFields, timeZone: String): Long?

    fun update(id: Long, fields: EventFields, timeZone: String): Boolean

    fun delete(id: Long): Boolean
}

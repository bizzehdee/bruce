package com.bizzeh.bruce.skills.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances

/** The calendars Android holds, read and changed with the calendar permissions. */
class AndroidCalendarStore(private val resolver: ContentResolver) : CalendarStore {
    override fun calendars(): List<CalendarInfo> =
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.IS_PRIMARY),
            "${Calendars.VISIBLE} = 1", null, null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(CalendarInfo(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getInt(2) >= Calendars.CAL_ACCESS_CONTRIBUTOR, cursor.getInt(3) == 1))
                }
            }
        }.orEmpty()

    override fun occurrences(from: Long, to: Long, limit: Int): List<CalendarEvent> {
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        return resolver.query(
            uri,
            arrayOf(Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.RRULE),
            null, null, "${Instances.BEGIN} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < limit) add(event(cursor))
            }
        }.orEmpty()
    }

    override fun event(id: Long): CalendarEvent? =
        resolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, id),
            arrayOf(Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.EVENT_LOCATION, Events.RRULE),
            "${Events.DELETED} = 0", null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) event(cursor) else null }

    private fun event(cursor: android.database.Cursor) = CalendarEvent(
        id = cursor.getLong(0),
        calendarId = cursor.getLong(1),
        title = cursor.getString(2).orEmpty(),
        start = cursor.getLong(3),
        end = cursor.getLong(4),
        allDay = cursor.getInt(5) == 1,
        location = cursor.getString(6).orEmpty(),
        recurring = !cursor.isNull(7),
    )

    override fun insert(calendarId: Long, fields: EventFields, timeZone: String): Long? =
        resolver.insert(Events.CONTENT_URI, values(fields, timeZone).apply { put(Events.CALENDAR_ID, calendarId) })?.let(ContentUris::parseId)

    override fun update(id: Long, fields: EventFields, timeZone: String): Boolean =
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values(fields, timeZone), null, null) == 1

    override fun delete(id: Long): Boolean = resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) == 1

    private fun values(fields: EventFields, timeZone: String) = ContentValues().apply {
        put(Events.TITLE, fields.title)
        put(Events.DTSTART, fields.start)
        put(Events.DTEND, fields.end)
        put(Events.ALL_DAY, if (fields.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (fields.allDay) "UTC" else timeZone)
        put(Events.EVENT_LOCATION, fields.location)
        fields.description?.let { put(Events.DESCRIPTION, it) }
    }
}

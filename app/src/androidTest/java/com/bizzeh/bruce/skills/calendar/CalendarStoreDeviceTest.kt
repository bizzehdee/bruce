package com.bizzeh.bruce.skills.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract.Calendars
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The calendar store against Android's calendar provider, in a local calendar of its own that it
 * removes afterwards, and in 2099, so none of the phone owner's events are read or changed. Needs
 * the calendar permissions: `adb shell pm grant com.bizzeh.bruce android.permission.READ_CALENDAR`
 * (and WRITE_CALENDAR), revoked again after.
 */
@ManualOnly("changes the phone's calendar provider; needs calendar permissions granted by hand")
@RunWith(AndroidJUnit4::class)
class CalendarStoreDeviceTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val store = AndroidCalendarStore(resolver)
    private val zone = ZoneId.systemDefault()
    private var calendarId = -1L

    private fun asSyncAdapter(uri: Uri) = uri.buildUpon()
        .appendQueryParameter("caller_is_syncadapter", "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, "LOCAL")
        .build()

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @After
    fun tearDown() {
        if (calendarId > 0) resolver.delete(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)), null, null)
    }

    @Test
    fun eventsAreAddedReadChangedAndDeletedInAWritableCalendar() {
        calendarId = ContentUris.parseId(
            resolver.insert(
                asSyncAdapter(Calendars.CONTENT_URI),
                ContentValues().apply {
                    put(Calendars.ACCOUNT_NAME, ACCOUNT)
                    put(Calendars.ACCOUNT_TYPE, "LOCAL")
                    put(Calendars.NAME, ACCOUNT)
                    put(Calendars.CALENDAR_DISPLAY_NAME, ACCOUNT)
                    put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                    put(Calendars.OWNER_ACCOUNT, ACCOUNT)
                    put(Calendars.VISIBLE, 1)
                    put(Calendars.SYNC_EVENTS, 1)
                },
            )!!,
        )
        assertTrue(store.calendars().single { it.id == calendarId }.writable)

        val fields = EventFields("Bruce device test", at("2099-06-01T10:00"), at("2099-06-01T11:00"), false, "Nowhere", "made by a test")
        val id = store.insert(calendarId, fields, zone.id)!!
        assertEquals(CalendarEvent(id, calendarId, "Bruce device test", fields.start, fields.end, false, "Nowhere", recurring = false), store.event(id))
        assertEquals(listOf(id), store.occurrences(at("2099-06-01T00:00"), at("2099-06-02T00:00"), 10).filter { it.calendarId == calendarId }.map { it.id })

        assertTrue(store.update(id, fields.copy(title = "Moved", start = at("2099-06-01T12:00"), end = at("2099-06-01T13:00"), description = null), zone.id))
        assertEquals("Moved", store.event(id)!!.title)
        assertEquals(at("2099-06-01T12:00"), store.event(id)!!.start)

        assertTrue(store.delete(id))
        assertNull(store.event(id))
    }

    private companion object {
        const val ACCOUNT = "Bruce test"
    }
}

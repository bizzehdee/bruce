package com.bizzeh.bruce.skills.calendar

import com.bizzeh.bruce.policy.ResourceTarget
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarSkillsTest {
    private val london = ZoneId.of("Europe/London")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(london).toInstant().toEpochMilli()

    private class FakeCalendar : CalendarStore {
        val calendars = mutableListOf(CalendarInfo(1, "Personal", writable = true, primary = true), CalendarInfo(2, "Holidays", writable = false, primary = false))
        val events = mutableMapOf<Long, CalendarEvent>()
        val saved = mutableMapOf<Long, EventFields>()
        var refuse = false
        var lastRange: Pair<Long, Long>? = null

        override fun calendars() = calendars.toList()
        override fun occurrences(from: Long, to: Long, limit: Int): List<CalendarEvent> {
            lastRange = from to to
            return events.values.filter { it.end > from && it.start < to }.sortedBy { it.start }.take(limit)
        }
        override fun event(id: Long) = events[id]
        override fun insert(calendarId: Long, fields: EventFields, timeZone: String): Long? {
            if (refuse) return null
            val id = (events.keys.maxOrNull() ?: 0) + 1
            events[id] = CalendarEvent(id, calendarId, fields.title, fields.start, fields.end, fields.allDay, fields.location, recurring = false)
            saved[id] = fields
            return id
        }
        override fun update(id: Long, fields: EventFields, timeZone: String): Boolean {
            if (refuse) return false
            events[id] = events.getValue(id).copy(title = fields.title, start = fields.start, end = fields.end, allDay = fields.allDay, location = fields.location)
            saved[id] = fields
            return true
        }
        override fun delete(id: Long) = !refuse && events.remove(id) != null
    }

    private val calendar = FakeCalendar()
    private val skills = CalendarSkills(calendar, Dispatchers.Unconfined, { london }, { at("2026-09-30T09:00") })
    private val byId = skills.create().associateBy { it.id }

    private fun run(id: String, vararg arguments: Pair<String, Any>) = runBlocking { byId.getValue(id).execute(SkillArguments(mapOf(*arguments))) }

    private fun check(id: String, vararg arguments: Pair<String, Any>) = runBlocking { skills.check(SkillRequest(byId.getValue(id), SkillArguments(mapOf(*arguments)))) }

    private fun dentist() = CalendarEvent(7, 1, "Dentist", at("2026-10-01T15:00"), at("2026-10-01T16:00"), allDay = false, location = "High St", recurring = false)

    @Test
    fun everythingIsOffAndChangesAreAlwaysAsked() {
        assertEquals(listOf("list_events", "create_event", "update_event", "delete_event"), byId.keys.toList())
        assertTrue(byId.values.all { it.defaultState == SkillState.DECLINED })
        assertEquals(listOf("create_event", "update_event", "delete_event"), byId.values.filter { it.confirmEveryUse }.map { it.id })
        assertEquals(listOf("delete_event"), byId.values.filter { it.highRisk }.map { it.id })
        assertTrue(byId.getValue("list_events").androidPermissions == listOf(android.Manifest.permission.READ_CALENDAR))
    }

    @Test
    fun eventsAreListedWithIdsCalendarsAndRepeats() {
        calendar.events[7] = dentist()
        calendar.events[8] = CalendarEvent(8, 2, "Bank holiday", at("2026-10-02T00:00").let { java.time.LocalDate.parse("2026-10-02").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            java.time.LocalDate.parse("2026-10-03").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay = true, location = "", recurring = true)

        val listed = (run("list_events") as SkillOutcome.Done).content

        assertEquals(
            "id 7: \"Dentist\", Thu 1 Oct 2026 15:00–16:00, at High St [Personal]\n" +
                "id 8: \"Bank holiday\", Fri 2 Oct 2026, all day [Holidays] (repeats)",
            listed,
        )
        assertEquals(at("2026-09-30T00:00") to at("2026-10-07T00:00"), calendar.lastRange)
        run("list_events", "from" to "2026-12-01", "days" to 2L)
        assertEquals(at("2026-12-01T00:00") to at("2026-12-03T00:00"), calendar.lastRange)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("list_events", "from" to "tomorrow") as SkillOutcome.Failed).code)
        calendar.events.clear()
        assertEquals("No events from 2026-09-30 for 7 days.", (run("list_events") as SkillOutcome.Done).content)
    }

    @Test
    fun aNewEventIsShownExactlyBeforeItIsAdded() {
        val asked = check("create_event", "title" to "Lunch", "start" to "2026-10-05T12:30", "location" to "Cafe") as ScopeCheck.InScope
        assertEquals(listOf(ResourceTarget("New event: \"Lunch\", Mon 5 Oct 2026 12:30–13:30, at Cafe (Personal)", "calendar:1:new")), asked.targets)

        assertEquals(SkillOutcome.Done("Added the event (id 1)."), run("create_event", "title" to "Lunch", "start" to "2026-10-05T12:30", "location" to "Cafe"))
        assertEquals(EventFields("Lunch", at("2026-10-05T12:30"), at("2026-10-05T13:30"), false, "Cafe", ""), calendar.saved.getValue(1))
    }

    @Test
    fun allDayEventsUseUtcMidnights() {
        run("create_event", "title" to "Trip", "start" to "2026-10-10", "end" to "2026-10-12")

        val saved = calendar.saved.getValue(1)
        assertTrue(saved.allDay)
        assertEquals(java.time.LocalDate.parse("2026-10-10").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), saved.start)
        assertEquals("New event: \"Trip\", Sat 10 Oct 2026 to Sun 11 Oct 2026, all day (Personal)",
            (check("create_event", "title" to "Trip", "start" to "2026-10-10", "end" to "2026-10-12") as ScopeCheck.InScope).targets.single().display)
    }

    @Test
    fun badNewEventsAreRefused() {
        fun reason(vararg arguments: Pair<String, Any>) = (check("create_event", *arguments) as ScopeCheck.OutOfScope).message
        assertTrue(reason("title" to "X", "start" to "next tuesday").startsWith("The times must be"))
        assertTrue(reason("title" to "X", "start" to "2026-10-05T12:00", "end" to "2026-10-05T11:00").startsWith("The times must be"))
        assertTrue(reason("title" to "X", "start" to "2026-10-05T12:00", "end" to "2027-10-05T11:00").startsWith("The times must be"))
        assertEquals("The event needs a title.", reason("title" to " ", "start" to "2026-10-05T12:00"))
        assertTrue(reason("title" to "X", "start" to "2026-10-05T12:00", "calendar" to "Holidays").startsWith("No calendar"))
        calendar.calendars.clear()
        assertEquals("This phone has no calendar Bruce can add events to.", reason("title" to "X", "start" to "2026-10-05T12:00"))
    }

    @Test
    fun changesKeepWhatIsNotGivenAndBindTheEventAsItWas() {
        calendar.events[7] = dentist()

        val asked = (check("update_event", "event_id" to 7L, "start" to "2026-10-01T16:00") as ScopeCheck.InScope).targets.single()
        assertEquals("Change: \"Dentist\", Thu 1 Oct 2026 15:00–16:00, at High St → \"Dentist\", Thu 1 Oct 2026 16:00–17:00, at High St", asked.display)
        assertEquals(SkillOutcome.Done("Changed the event."), run("update_event", "event_id" to 7L, "start" to "2026-10-01T16:00"))
        assertEquals(EventFields("Dentist", at("2026-10-01T16:00"), at("2026-10-01T17:00"), false, "High St", null), calendar.saved.getValue(7))

        val after = (check("update_event", "event_id" to 7L, "start" to "2026-10-01T16:00") as ScopeCheck.InScope).targets.single()
        assertFalse(after.identity == asked.identity, "an approval for the event as it was no longer matches")

        run("update_event", "event_id" to 7L, "end" to "2026-10-01T18:00", "notes" to "Bring card")
        assertEquals(EventFields("Dentist", at("2026-10-01T16:00"), at("2026-10-01T18:00"), false, "High St", "Bring card"), calendar.saved.getValue(7))
        assertTrue((check("update_event", "event_id" to 7L) as ScopeCheck.OutOfScope).message.startsWith("Give at least one field"))
        assertTrue(check("update_event", "event_id" to 7L, "end" to "2026-10-01T10:00") is ScopeCheck.OutOfScope)
    }

    @Test
    fun deletingShowsTheEventAndRepeatsOrReadOnlyCalendarsAreLeftAlone() {
        calendar.events[7] = dentist()
        calendar.events[9] = dentist().copy(id = 9, recurring = true)
        calendar.events[10] = dentist().copy(id = 10, calendarId = 2)

        assertEquals("Delete: \"Dentist\", Thu 1 Oct 2026 15:00–16:00, at High St", (check("delete_event", "event_id" to 7L) as ScopeCheck.InScope).targets.single().display)
        assertEquals(SkillOutcome.Done("Deleted the event."), run("delete_event", "event_id" to 7L))
        assertFalse(7L in calendar.events)
        assertTrue((check("delete_event", "event_id" to 7L) as ScopeCheck.OutOfScope).message.startsWith("No event has that id"))
        assertTrue((check("delete_event", "event_id" to 9L) as ScopeCheck.OutOfScope).message.startsWith("That event repeats"))
        assertTrue((check("delete_event", "event_id" to 10L) as ScopeCheck.OutOfScope).message.startsWith("That event's calendar cannot"))
    }

    @Test
    fun aCalendarThatRefusesIsReported() {
        calendar.events[7] = dentist()
        calendar.refuse = true

        assertEquals(DenialCode.TOOL_FAILED, (run("create_event", "title" to "X", "start" to "2026-10-05T12:00") as SkillOutcome.Failed).code)
        assertEquals(DenialCode.TOOL_FAILED, (run("update_event", "event_id" to 7L, "title" to "Y") as SkillOutcome.Failed).code)
        assertEquals(DenialCode.TOOL_FAILED, (run("delete_event", "event_id" to 7L) as SkillOutcome.Failed).code)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("delete_event", "event_id" to 99L) as SkillOutcome.Failed).code)
        assertTrue(skills.plan("list_events", SkillArguments(emptyMap())) is CalendarPlan.Invalid)
    }
}

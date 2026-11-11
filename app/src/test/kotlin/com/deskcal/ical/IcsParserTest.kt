package com.deskcal.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IcsParserTest {

    private val tz = TimeZone.getTimeZone("Europe/Berlin")

    private val sample = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Test//Test//EN
        BEGIN:VEVENT
        UID:event-1@test
        DTSTART;TZID=Europe/Berlin:20240610T090000
        DTEND;TZID=Europe/Berlin:20240610T100000
        SUMMARY:Team standup
        LOCATION:Room 4
        END:VEVENT
        BEGIN:VEVENT
        UID:event-2@test
        DTSTART;VALUE=DATE:20240612
        SUMMARY:Conference
        END:VEVENT
        BEGIN:VEVENT
        UID:event-3@test
        DTSTART:20240613T120000Z
        DURATION:PT1H30M
        SUMMARY:UTC event
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun parsesAllEvents() {
        val events = IcsParser.parseCalendar(sample, tz)
        assertEquals(3, events.size)
    }

    @Test
    fun parsesTimedEventWithTzid() {
        val e = IcsParser.parseCalendar(sample, tz).first { it.uid == "event-1@test" }
        assertEquals("Team standup", e.title)
        assertEquals("Room 4", e.location)
        assertFalse(e.allDay)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = e.startMillis }
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(60 * 60 * 1000L, e.endMillis - e.startMillis)
    }

    @Test
    fun parsesAllDayEventWithDefaultOneDayDuration() {
        val e = IcsParser.parseCalendar(sample, tz).first { it.uid == "event-2@test" }
        assertTrue(e.allDay)
        assertEquals("Conference", e.title)
        assertEquals(24L * 60 * 60 * 1000, e.endMillis - e.startMillis)
    }

    @Test
    fun parsesUtcEventWithDuration() {
        val e = IcsParser.parseCalendar(sample, tz).first { it.uid == "event-3@test" }
        assertEquals(90 * 60 * 1000L, e.endMillis - e.startMillis)
    }

    @Test
    fun skipsMalformedEventButKeepsTheRest() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:bad@test
            SUMMARY:No DTSTART here
            END:VEVENT
            BEGIN:VEVENT
            UID:good@test
            DTSTART:20240610T090000Z
            SUMMARY:Good one
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val events = IcsParser.parseCalendar(ics, tz)
        assertEquals(1, events.size)
        assertEquals("good@test", events[0].uid)
        assertEquals("Good one", events[0].title)
    }

    @Test
    fun parsesRruleAndExdate() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:rec@test
            DTSTART:20240603T080000Z
            DTEND:20240603T083000Z
            SUMMARY:Daily sync
            RRULE:FREQ=DAILY;COUNT=5
            EXDATE:20240604T080000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val e = IcsParser.parseCalendar(ics, tz).single()
        assertNotNull(e.rule)
        assertEquals(RecurrenceRule.Freq.DAILY, e.rule!!.freq)
        assertEquals(5, e.rule!!.count)
        assertEquals(1, e.exceptions.size)
    }
}

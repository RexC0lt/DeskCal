package com.deskcal.week

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayGroupingTest {

    private val tz = TimeZone.getTimeZone("Europe/Berlin")

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long {
        val cal = Calendar.getInstance(tz)
        cal.clear()
        cal.set(y, m - 1, d, h, min, 0)
        return cal.timeInMillis
    }

    private fun occ(
        uid: String,
        start: Long,
        end: Long,
        allDay: Boolean = false,
    ) = OccurrenceLike(uid, "title-$uid", "loc", start, end, allDay, null)

    @Test
    fun multiDayEventAppearsOnEachCoveredDay() {
        val ws = WeekLogic.weekStart(millis(2024, 6, 10), tz)
        val trip = occ("trip", millis(2024, 6, 12, 22, 0), millis(2024, 6, 14, 10, 0))
        val days = buildDays(ws, listOf(trip), millis(2024, 6, 13, 12, 0), tz)
        assertEquals(7, days.size)
        val covered = days.filter { it.events.isNotEmpty() }
        assertEquals(3, covered.size)
        assertTrue(covered.all { it.events[0].uid == "trip" })
    }

    @Test
    fun pastEventsAreDimmedAndTodayIsFlagged() {
        val ws = WeekLogic.weekStart(millis(2024, 6, 10), tz)
        val now = millis(2024, 6, 12, 12, 0)
        val past = occ("past", millis(2024, 6, 11, 9, 0), millis(2024, 6, 11, 10, 0))
        val future = occ("future", millis(2024, 6, 12, 15, 0), millis(2024, 6, 12, 16, 0))
        val days = buildDays(ws, listOf(past, future), now, tz)
        val tuesday = days.first { WeekLogic.isSameDay(it.startMillis, millis(2024, 6, 11), tz) }
        assertTrue(tuesday.events.single { it.uid == "past" }.isPast)
        val wednesday = days.first { WeekLogic.isSameDay(it.startMillis, millis(2024, 6, 12), tz) }
        assertFalse(wednesday.events.single { it.uid == "future" }.isPast)
        assertFalse(days.first { WeekLogic.isSameDay(it.startMillis, millis(2024, 6, 10), tz) }.isToday)
        assertTrue(days.first { WeekLogic.isSameDay(it.startMillis, millis(2024, 6, 12), tz) }.isToday)
    }

    @Test
    fun allDayEventsSortBeforeTimedEvents() {
        val ws = WeekLogic.weekStart(millis(2024, 6, 10), tz)
        val timed = occ("timed", millis(2024, 6, 11, 8, 0), millis(2024, 6, 11, 9, 0))
        val allDay = occ("allday", millis(2024, 6, 11), millis(2024, 6, 12), allDay = true)
        val days = buildDays(ws, listOf(timed, allDay), millis(2024, 6, 10, 12, 0), tz)
        val tuesday = days.first { WeekLogic.isSameDay(it.startMillis, millis(2024, 6, 11), tz) }
        assertEquals(listOf("allday", "timed"), tuesday.events.map { it.uid })
        assertTrue(tuesday.events[0].allDay)
    }

    @Test
    fun eventsOutsideTheWeekAreIgnored() {
        val ws = WeekLogic.weekStart(millis(2024, 6, 10), tz)
        val other = occ("other", millis(2024, 6, 20, 9, 0), millis(2024, 6, 20, 10, 0))
        val days = buildDays(ws, listOf(other), millis(2024, 6, 10, 12, 0), tz)
        assertTrue(days.all { it.events.isEmpty() })
    }
}

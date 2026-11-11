package com.deskcal.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RecurrenceTest {

    private val tz = TimeZone.getTimeZone("Europe/Berlin")
    private val utc = TimeZone.getTimeZone("UTC")

    private val rangeStart = utc(2024, 6, 1, 0, 0)
    private val rangeEnd = utc(2024, 7, 1, 0, 0)

    private fun utc(y: Int, m: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance(utc)
        c.clear()
        c.set(y, m - 1, d, h, mi, 0)
        return c.timeInMillis
    }

    private fun event(
        start: Long,
        end: Long,
        rule: RecurrenceRule? = null,
        exceptions: Set<Long> = emptySet(),
    ) = ParsedEvent("u", "t", "", start, end, false, rule, exceptions)

    private fun dow(millis: Long): Int =
        Calendar.getInstance(utc).apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)

    private fun dom(millis: Long): Int =
        Calendar.getInstance(utc).apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    private fun month(millis: Long): Int =
        Calendar.getInstance(utc).apply { timeInMillis = millis }.get(Calendar.MONTH)

    private fun year(millis: Long): Int =
        Calendar.getInstance(utc).apply { timeInMillis = millis }.get(Calendar.YEAR)

    @Test
    fun nonRecurringInsideRangeIsReturned() {
        val e = event(utc(2024, 6, 10, 9, 0), utc(2024, 6, 10, 10, 0))
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(1, occ.size)
        assertEquals(utc(2024, 6, 10, 9, 0), occ[0].startMillis)
        assertEquals(utc(2024, 6, 10, 10, 0), occ[0].endMillis)
    }

    @Test
    fun nonRecurringOutsideRangeIsSkipped() {
        val e = event(utc(2024, 8, 10, 9, 0), utc(2024, 8, 10, 10, 0))
        assertTrue(RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz).isEmpty())
    }

    @Test
    fun eventOverlappingRangeStartIsReturned() {
        val e = event(utc(2024, 5, 31, 23, 0), utc(2024, 6, 1, 1, 0))
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(1, occ.size)
    }

    @Test
    fun dailyWithCount() {
        val rule = RecurrenceRule(RecurrenceRule.Freq.DAILY, 1, 5, null, null, null)
        val e = event(utc(2024, 6, 3, 8, 0), utc(2024, 6, 3, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(5, occ.size)
        assertEquals(utc(2024, 6, 3, 8, 0), occ[0].startMillis)
        assertEquals(utc(2024, 6, 7, 8, 0), occ[4].startMillis)
    }

    @Test
    fun dailyWithInterval() {
        val rule = RecurrenceRule(RecurrenceRule.Freq.DAILY, 2, null, null, null, null)
        val e = event(utc(2024, 6, 3, 8, 0), utc(2024, 6, 3, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(utc(2024, 6, 3, 8, 0), occ[0].startMillis)
        assertEquals(utc(2024, 6, 5, 8, 0), occ[1].startMillis)
        assertEquals(utc(2024, 6, 7, 8, 0), occ[2].startMillis)
    }

    @Test
    fun weeklyByDay() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR", tz)!!
        val e = event(utc(2024, 6, 3, 8, 0), utc(2024, 6, 3, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertTrue(occ.all {
            dow(it.startMillis) == Calendar.MONDAY ||
                dow(it.startMillis) == Calendar.WEDNESDAY ||
                dow(it.startMillis) == Calendar.FRIDAY
        })
        assertEquals(utc(2024, 6, 3, 8, 0), occ[0].startMillis)
        assertEquals(utc(2024, 6, 5, 8, 0), occ[1].startMillis)
        assertEquals(utc(2024, 6, 7, 8, 0), occ[2].startMillis)
        assertEquals(utc(2024, 6, 10, 8, 0), occ[3].startMillis)
    }

    @Test
    fun weeklyWithInterval() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2", tz)!!
        val e = event(utc(2024, 6, 3, 8, 0), utc(2024, 6, 3, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(utc(2024, 6, 3, 8, 0), occ[0].startMillis)
        assertEquals(utc(2024, 6, 17, 8, 0), occ[1].startMillis)
        assertEquals(2, occ.size)
    }

    @Test
    fun monthlySkipsMonthsWithoutTheDay() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY", tz)!!
        val e = event(utc(2024, 1, 31, 8, 0), utc(2024, 1, 31, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, utc(2024, 1, 1, 0, 0), utc(2025, 1, 1, 0, 0), tz)
        assertTrue(occ.isNotEmpty())
        assertTrue(occ.all { dom(it.startMillis) == 31 })
        assertFalse(occ.any { month(it.startMillis) == Calendar.FEBRUARY })
        assertTrue(occ.any { month(it.startMillis) == Calendar.MARCH })
    }

    @Test
    fun yearlyOnFeb29OnlyInLeapYears() {
        val rule = RecurrenceRule.parse("FREQ=YEARLY", tz)!!
        val e = event(utc(2024, 2, 29, 8, 0), utc(2024, 2, 29, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, utc(2024, 1, 1, 0, 0), utc(2029, 1, 1, 0, 0), tz)
        assertEquals(listOf(2024, 2028), occ.map { year(it.startMillis) })
    }

    @Test
    fun untilLimitsOccurrences() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;UNTIL=20240605T080000Z", tz)!!
        val e = event(utc(2024, 6, 3, 8, 0), utc(2024, 6, 3, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(3, occ.size)
        assertEquals(utc(2024, 6, 5, 8, 0), occ.last().startMillis)
    }

    @Test
    fun exdateRemovesOccurrence() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=5", tz)!!
        val e = event(
            utc(2024, 6, 3, 8, 0),
            utc(2024, 6, 3, 9, 0),
            rule,
            setOf(utc(2024, 6, 4, 8, 0)),
        )
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(4, occ.size)
        assertFalse(occ.any { it.startMillis == utc(2024, 6, 4, 8, 0) })
    }

    @Test
    fun rangeClippingKeepsOnlyOverlappingOccurrences() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=30", tz)!!
        val e = event(utc(2024, 5, 20, 8, 0), utc(2024, 5, 20, 9, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(18, occ.size)
        assertEquals(utc(2024, 6, 1, 8, 0), occ.first().startMillis)
        assertEquals(utc(2024, 6, 18, 8, 0), occ.last().startMillis)
    }

    @Test
    fun multiDayRecurringEventKeepsDuration() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;COUNT=2", tz)!!
        val e = event(utc(2024, 6, 3, 8, 0), utc(2024, 6, 5, 8, 0), rule)
        val occ = RecurrenceExpander.expand(e, rangeStart, rangeEnd, tz)
        assertEquals(2, occ.size)
        assertEquals(2L * 24 * 60 * 60 * 1000, occ[0].endMillis - occ[0].startMillis)
        assertEquals(utc(2024, 6, 10, 8, 0), occ[1].startMillis)
    }
}

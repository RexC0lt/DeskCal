package com.deskcal.week

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeekLogicTest {

    private val tz = TimeZone.getTimeZone("Europe/Berlin")
    private var oldTz: TimeZone? = null

    @Before
    fun setUp() {
        oldTz = TimeZone.getDefault()
        TimeZone.setDefault(tz)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(oldTz)
    }

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long {
        val cal = Calendar.getInstance(tz)
        cal.clear()
        cal.set(y, m - 1, d, h, min, 0)
        return cal.timeInMillis
    }

    @Test
    fun weekStart_mondayIsItsOwnWeekStart() {
        assertEquals(millis(2024, 6, 3), WeekLogic.weekStart(millis(2024, 6, 3, 10, 30), tz))
    }

    @Test
    fun weekStart_sundayBelongsToPreviousMonday() {
        assertEquals(millis(2024, 6, 24), WeekLogic.weekStart(millis(2024, 6, 30, 23, 59), tz))
    }

    @Test
    fun weekStart_monthBoundary() {
        assertEquals(millis(2024, 4, 29), WeekLogic.weekStart(millis(2024, 5, 1), tz))
        assertEquals(millis(2024, 4, 29), WeekLogic.weekStart(millis(2024, 5, 5), tz))
    }

    @Test
    fun weekStart_yearBoundary() {
        assertEquals(millis(2024, 12, 30), WeekLogic.weekStart(millis(2025, 1, 1), tz))
        assertEquals(millis(2024, 12, 30), WeekLogic.weekStart(millis(2025, 1, 5), tz))
        assertEquals(millis(2025, 1, 6), WeekLogic.weekStart(millis(2025, 1, 6), tz))
    }

    @Test
    fun weekDays_returnsSevenConsecutiveDaysStartingMonday() {
        val ws = WeekLogic.weekStart(millis(2024, 6, 12, 15, 0), tz)
        val days = WeekLogic.weekDays(ws, tz)
        assertEquals(7, days.size)
        assertEquals(ws, days[0])
        for (i in 1..6) {
            assertEquals(WeekLogic.addDays(days[i - 1], 1, tz), days[i])
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = ws }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun addWeeks_acrossMonthBoundary() {
        assertEquals(millis(2024, 2, 5), WeekLogic.addWeeks(millis(2024, 1, 29), 1, tz))
        assertEquals(millis(2023, 12, 25), WeekLogic.addWeeks(millis(2024, 1, 1), -1, tz))
    }

    @Test
    fun isSameDay_acrossMidnight() {
        assertTrue(WeekLogic.isSameDay(millis(2024, 6, 10, 23, 59), millis(2024, 6, 10, 0, 1), tz))
        assertFalse(WeekLogic.isSameDay(millis(2024, 6, 10, 23, 59), millis(2024, 6, 11, 0, 1), tz))
    }

    @Test
    fun nightMode_wrapsAroundMidnight() {
        assertTrue(WeekLogic.isInNightMode(23, 22, 7))
        assertTrue(WeekLogic.isInNightMode(3, 22, 7))
        assertFalse(WeekLogic.isInNightMode(12, 22, 7))
        assertFalse(WeekLogic.isInNightMode(7, 22, 7))
        assertTrue(WeekLogic.isInNightMode(12, 8, 14))
        assertFalse(WeekLogic.isInNightMode(7, 8, 14))
        assertFalse(WeekLogic.isInNightMode(10, 10, 10))
    }

    @Test
    fun weeksBetween_roundsAcrossDstChange() {
        val a = WeekLogic.weekStart(millis(2024, 3, 4), tz)
        val b = WeekLogic.weekStart(millis(2024, 4, 1), tz)
        assertEquals(4, WeekLogic.weeksBetween(a, b))
        assertEquals(-4, WeekLogic.weeksBetween(b, a))
    }
}

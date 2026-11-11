package com.deskcal.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IcsDateTimeTest {

    private val tz = TimeZone.getTimeZone("Europe/Berlin")

    private fun utcMillis(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y, m - 1, d, h, mi, s)
        return cal.timeInMillis
    }

    @Test
    fun parsesUtcDateTime() {
        val p = IcsDateTime.parse("20240115T090000Z", null, tz)!!
        assertFalse(p.isDate)
        assertEquals(utcMillis(2024, 1, 15, 9, 0, 0), p.millis)
    }

    @Test
    fun parsesFloatingTimeInDefaultZone() {
        val p = IcsDateTime.parse("20240115T090000", null, tz)!!
        assertFalse(p.isDate)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = p.millis }
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parsesTzidTime() {
        val p = IcsDateTime.parse("20240615T120000", "Europe/Berlin", TimeZone.getTimeZone("UTC"))!!
        assertEquals(utcMillis(2024, 6, 15, 10, 0, 0), p.millis)
    }

    @Test
    fun parsesAllDayDateAtLocalMidnight() {
        val p = IcsDateTime.parse("20240115", null, tz)!!
        assertTrue(p.isDate)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = p.millis }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(IcsDateTime.parse("not-a-date", null, tz))
        assertNull(IcsDateTime.parse("", null, tz))
        assertNull(IcsDateTime.parse("20241345T250000", null, tz))
        assertNull(IcsDateTime.parse("2024-01-15", null, tz))
    }

    @Test
    fun parsesIsoDurations() {
        assertEquals(3_600_000L, IcsDateTime.parseDurationMillis("PT1H"))
        assertEquals(5_400_000L, IcsDateTime.parseDurationMillis("PT1H30M"))
        assertEquals(90_000_000L, IcsDateTime.parseDurationMillis("P1DT1H"))
        assertEquals(604_800_000L, IcsDateTime.parseDurationMillis("P1W"))
        assertEquals(60_000L, IcsDateTime.parseDurationMillis("PT1M"))
        assertEquals(45_000L, IcsDateTime.parseDurationMillis("PT45S"))
        assertNull(IcsDateTime.parseDurationMillis("P1M"))
        assertNull(IcsDateTime.parseDurationMillis("junk"))
    }
}

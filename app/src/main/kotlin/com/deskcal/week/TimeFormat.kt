package com.deskcal.week

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormat {

    fun time(millis: Long, hour24: Boolean, tz: TimeZone = TimeZone.getDefault()): String {
        val pattern = if (hour24) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = tz }
            .format(Date(millis))
    }

    fun timeRange(
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        hour24: Boolean,
        tz: TimeZone = TimeZone.getDefault(),
    ): String {
        if (allDay) return "All day"
        val start = time(startMillis, hour24, tz)
        return if (endMillis <= startMillis) start else "$start – ${time(endMillis, hour24, tz)}"
    }

    fun dayHeader(dayMillis: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply { timeZone = tz }
            .format(Date(dayMillis))

    fun weekLabel(weekStart: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val end = WeekLogic.addDays(weekStart, 6, tz)
        val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault()).apply { timeZone = tz }
        val fullFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply { timeZone = tz }
        val startStr = if (sameYear(weekStart, end, tz)) {
            dayFmt.format(Date(weekStart))
        } else {
            fullFmt.format(Date(weekStart))
        }
        return "$startStr – ${fullFmt.format(Date(end))}"
    }

    fun statusDate(millis: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).apply { timeZone = tz }
            .format(Date(millis))

    private fun sameYear(a: Long, b: Long, tz: TimeZone): Boolean {
        val ca = Calendar.getInstance(tz).apply { timeInMillis = a }
        val cb = Calendar.getInstance(tz).apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }
}

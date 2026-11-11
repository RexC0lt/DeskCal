package com.deskcal.week

import java.util.Calendar
import java.util.TimeZone

object WeekLogic {

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun dayStart(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun weekStart(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = dayStart(millis, tz)
        val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        return cal.timeInMillis
    }

    fun addDays(millis: Long, days: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = millis
        cal.add(Calendar.DAY_OF_MONTH, days)
        return cal.timeInMillis
    }

    fun addWeeks(weekStart: Long, weeks: Int, tz: TimeZone = TimeZone.getDefault()): Long =
        addDays(weekStart, weeks * 7, tz)

    fun weekDays(weekStart: Long, tz: TimeZone = TimeZone.getDefault()): List<Long> =
        (0..6).map { addDays(weekStart, it, tz) }

    fun isSameDay(a: Long, b: Long, tz: TimeZone = TimeZone.getDefault()): Boolean =
        dayStart(a, tz) == dayStart(b, tz)

    fun isInNightMode(hourOfDay: Int, startHour: Int, endHour: Int): Boolean =
        if (startHour == endHour) {
            false
        } else if (startHour < endHour) {
            hourOfDay >= startHour && hourOfDay < endHour
        } else {
            hourOfDay >= startHour || hourOfDay < endHour
        }

    fun weeksBetween(fromWeekStart: Long, toWeekStart: Long): Int {
        val days = ((toWeekStart - fromWeekStart) / DAY_MILLIS).toInt()
        return Math.round(days / 7f)
    }
}

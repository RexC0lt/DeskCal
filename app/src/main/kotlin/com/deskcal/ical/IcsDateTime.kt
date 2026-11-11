package com.deskcal.ical

import java.util.Calendar
import java.util.TimeZone

object IcsDateTime {

    data class Parsed(val millis: Long, val isDate: Boolean)

    fun parse(value: String, tzid: String?, defaultTz: TimeZone = TimeZone.getDefault()): Parsed? {
        val v = value.trim()
        if (v.isEmpty()) return null
        val utc = v.endsWith("Z") || v.endsWith("z")
        val core = if (utc) v.dropLast(1) else v
        val cal = Calendar.getInstance(timeZoneFor(tzid, utc, defaultTz))
        return if (core.length == 8 && core.all { it.isDigit() }) {
            if (!setDate(cal, core)) return null
            Parsed(cal.timeInMillis, isDate = true)
        } else if (core.length == 15 && core[8] == 'T' && core.all { it.isDigit() || it == 'T' }) {
            if (!setDateTime(cal, core)) return null
            Parsed(cal.timeInMillis, isDate = false)
        } else {
            null
        }
    }

    private fun timeZoneFor(tzid: String?, utc: Boolean, defaultTz: TimeZone): TimeZone = when {
        utc -> TimeZone.getTimeZone("UTC")
        !tzid.isNullOrBlank() -> TimeZone.getTimeZone(tzid)
        else -> defaultTz
    }

    private fun setDate(cal: Calendar, core: String): Boolean {
        val y = core.substring(0, 4).toIntOrNull() ?: return false
        val m = core.substring(4, 6).toIntOrNull() ?: return false
        val d = core.substring(6, 8).toIntOrNull() ?: return false
        if (m !in 1..12 || d !in 1..31) return false
        cal.clear()
        cal.set(y, m - 1, d, 0, 0, 0)
        return true
    }

    private fun setDateTime(cal: Calendar, core: String): Boolean {
        val y = core.substring(0, 4).toIntOrNull() ?: return false
        val mo = core.substring(4, 6).toIntOrNull() ?: return false
        val d = core.substring(6, 8).toIntOrNull() ?: return false
        val h = core.substring(9, 11).toIntOrNull() ?: return false
        val mi = core.substring(11, 13).toIntOrNull() ?: return false
        val s = core.substring(13, 15).toIntOrNull() ?: return false
        if (mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || s > 60) return false
        cal.clear()
        cal.set(y, mo - 1, d, h, mi, s)
        return true
    }

    fun parseDurationMillis(value: String): Long? {
        val v = value.trim().uppercase()
        if (!v.startsWith("P")) return null
        var weeks = 0L
        var days = 0L
        var hours = 0L
        var minutes = 0L
        var seconds = 0L
        var num = 0L
        var inTime = false
        for (ch in v.substring(1)) {
            when {
                ch.isDigit() -> num = num * 10 + (ch - '0')
                ch == 'T' -> inTime = true
                ch == 'W' && !inTime -> weeks = num
                ch == 'D' && !inTime -> days = num
                ch == 'H' && inTime -> hours = num
                ch == 'M' && inTime -> minutes = num
                ch == 'S' && inTime -> seconds = num
                else -> return null
            }
            num = 0
        }
        return ((weeks * 7 + days) * 24 + hours) * 3_600_000L +
            minutes * 60_000L +
            seconds * 1_000L
    }
}

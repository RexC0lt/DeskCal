package com.deskcal.ical

import java.util.Calendar
import java.util.TimeZone

data class ParsedEvent(
    val uid: String,
    val title: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val rule: RecurrenceRule?,
    val exceptions: Set<Long>,
)

data class Occurrence(
    val uid: String,
    val title: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

data class RecurrenceRule(
    val freq: Freq,
    val interval: Int,
    val count: Int?,
    val untilMillis: Long?,
    val byDay: Set<Int>?,
    val byMonthDay: Set<Int>?,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    companion object {
        private val DAY_CODES = mapOf(
            "MO" to Calendar.MONDAY,
            "TU" to Calendar.TUESDAY,
            "WE" to Calendar.WEDNESDAY,
            "TH" to Calendar.THURSDAY,
            "FR" to Calendar.FRIDAY,
            "SA" to Calendar.SATURDAY,
            "SU" to Calendar.SUNDAY,
        )

        fun parse(value: String, tz: TimeZone = TimeZone.getDefault()): RecurrenceRule? {
            val parts = value.split(';').mapNotNull { entry ->
                val kv = entry.split('=', limit = 2)
                if (kv.size == 2) kv[0].trim().uppercase() to kv[1].trim() else null
            }.toMap()
            val freq = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> Freq.DAILY
                "WEEKLY" -> Freq.WEEKLY
                "MONTHLY" -> Freq.MONTHLY
                "YEARLY" -> Freq.YEARLY
                else -> return null
            }
            val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(1)
            val until = parts["UNTIL"]?.let { IcsDateTime.parse(it, null, tz)?.millis }
            val byDay = parts["BYDAY"]
                ?.split(',')
                ?.mapNotNull { DAY_CODES[it.trim().uppercase().takeLast(2)] }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
            val byMonthDay = parts["BYMONTHDAY"]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
            return RecurrenceRule(freq, interval, count, until, byDay, byMonthDay)
        }
    }
}

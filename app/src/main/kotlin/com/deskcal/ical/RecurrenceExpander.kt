package com.deskcal.ical

import com.deskcal.week.WeekLogic
import java.util.Calendar
import java.util.TimeZone

object RecurrenceExpander {

    private const val MAX_ITERATIONS = 1000

    fun expand(
        event: ParsedEvent,
        rangeStart: Long,
        rangeEnd: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<Occurrence> {
        val duration = (event.endMillis - event.startMillis).coerceAtLeast(0L)
        val rule = event.rule
        val starts = if (rule == null) {
            listOf(event.startMillis)
        } else {
            generateStarts(event, rule, rangeEnd, tz)
        }
        val limited = rule?.count?.let { starts.take(it) } ?: starts
        val until = rule?.untilMillis
        return limited
            .filter { start ->
                (until == null || start <= until) &&
                    start < rangeEnd &&
                    start + duration > rangeStart &&
                    !event.exceptions.contains(start)
            }
            .map { start ->
                Occurrence(
                    uid = event.uid,
                    title = event.title,
                    location = event.location,
                    startMillis = start,
                    endMillis = start + duration,
                    allDay = event.allDay,
                )
            }
    }

    private fun generateStarts(
        event: ParsedEvent,
        rule: RecurrenceRule,
        rangeEnd: Long,
        tz: TimeZone,
    ): List<Long> {
        val base = Calendar.getInstance(tz).apply { timeInMillis = event.startMillis }
        val baseDayOfMonth = base.get(Calendar.DAY_OF_MONTH)
        val baseMonth = base.get(Calendar.MONTH)
        val result = mutableListOf<Long>()
        var weekCounter = 0
        var lastWeekStart = WeekLogic.weekStart(event.startMillis, tz)
        var i = 0
        while (i < MAX_ITERATIONS) {
            val candidate: Long? = when (rule.freq) {
                RecurrenceRule.Freq.DAILY -> {
                    val c = base.clone() as Calendar
                    c.add(Calendar.DAY_OF_MONTH, i * rule.interval)
                    c.timeInMillis
                }
                RecurrenceRule.Freq.WEEKLY -> {
                    if (rule.byDay.isNullOrEmpty()) {
                        val c = base.clone() as Calendar
                        c.add(Calendar.DAY_OF_MONTH, i * 7 * rule.interval)
                        c.timeInMillis
                    } else {
                        val c = base.clone() as Calendar
                        c.add(Calendar.DAY_OF_MONTH, i)
                        val ws = WeekLogic.weekStart(c.timeInMillis, tz)
                        if (ws != lastWeekStart) {
                            weekCounter++
                            lastWeekStart = ws
                        }
                        if (rule.byDay.contains(c.get(Calendar.DAY_OF_WEEK)) &&
                            weekCounter % rule.interval == 0
                        ) {
                            c.timeInMillis
                        } else {
                            null
                        }
                    }
                }
                RecurrenceRule.Freq.MONTHLY -> {
                    val c = base.clone() as Calendar
                    c.add(Calendar.MONTH, i * rule.interval)
                    if (c.get(Calendar.DAY_OF_MONTH) != baseDayOfMonth) {
                        null
                    } else if (rule.byMonthDay != null &&
                        !rule.byMonthDay.contains(c.get(Calendar.DAY_OF_MONTH))
                    ) {
                        null
                    } else {
                        c.timeInMillis
                    }
                }
                RecurrenceRule.Freq.YEARLY -> {
                    val c = base.clone() as Calendar
                    c.add(Calendar.YEAR, i * rule.interval)
                    if (c.get(Calendar.MONTH) != baseMonth ||
                        c.get(Calendar.DAY_OF_MONTH) != baseDayOfMonth
                    ) {
                        null
                    } else {
                        c.timeInMillis
                    }
                }
            }
            if (candidate != null) {
                if (candidate >= rangeEnd) break
                result.add(candidate)
            }
            i++
        }
        return result
    }
}

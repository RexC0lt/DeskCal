package com.deskcal.week

import java.util.TimeZone

data class OccurrenceLike(
    val uid: String,
    val title: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarColor: String?,
)

data class EventUi(
    val uid: String,
    val title: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val isPast: Boolean,
    val calendarColor: String?,
)

data class DayUi(
    val startMillis: Long,
    val isToday: Boolean,
    val events: List<EventUi>,
)

fun buildDays(
    weekStart: Long,
    occurrences: List<OccurrenceLike>,
    now: Long,
    tz: TimeZone = TimeZone.getDefault(),
): List<DayUi> {
    val days = WeekLogic.weekDays(weekStart, tz)
    return days.map { dayStart ->
        val dayEnd = WeekLogic.addDays(dayStart, 1, tz)
        val events = occurrences
            .filter { it.startMillis < dayEnd && it.endMillis > dayStart }
            .sortedWith(compareBy({ !it.allDay }, { it.startMillis }))
            .map { o ->
                EventUi(
                    uid = o.uid,
                    title = o.title,
                    location = o.location,
                    startMillis = o.startMillis,
                    endMillis = o.endMillis,
                    allDay = o.allDay,
                    isPast = o.endMillis <= now,
                    calendarColor = o.calendarColor,
                )
            }
        DayUi(
            startMillis = dayStart,
            isToday = WeekLogic.isSameDay(dayStart, now, tz),
            events = events,
        )
    }
}

package com.deskcal.ical

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import java.io.StringReader
import java.util.TimeZone

object IcsParser {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun parseCalendar(ics: String, defaultTz: TimeZone = TimeZone.getDefault()): List<ParsedEvent> {
        val calendar = CalendarBuilder().build(StringReader(ics))
        val result = mutableListOf<ParsedEvent>()
        for (component in calendar.components) {
            if (component.name != Component.VEVENT) continue
            val event = component as? VEvent ?: continue
            try {
                parseEvent(event, defaultTz)?.let { result.add(it) }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun parseEvent(event: VEvent, defaultTz: TimeZone): ParsedEvent? {
        val dtStart = event.getProperty<Property>(Property.DTSTART) ?: return null
        val startTzid = dtStart.getParameter<Parameter>(Parameter.TZID)?.value
        val start = IcsDateTime.parse(dtStart.value, startTzid, defaultTz) ?: return null
        val allDay = start.isDate
        val endMillis: Long = run {
            val dtEnd = event.getProperty<Property>(Property.DTEND)
            if (dtEnd != null) {
                val endTzid = dtEnd.getParameter<Parameter>(Parameter.TZID)?.value
                IcsDateTime.parse(dtEnd.value, endTzid, defaultTz)?.millis
                    ?: start.millis + if (allDay) DAY_MILLIS else 0L
            } else {
                val duration = event.getProperty<Property>(Property.DURATION)?.value
                    ?.let { IcsDateTime.parseDurationMillis(it) }
                when {
                    duration != null -> start.millis + duration
                    allDay -> start.millis + DAY_MILLIS
                    else -> start.millis
                }
            }
        }
        val uid = event.getProperty<Property>(Property.UID)?.value?.trim()?.takeIf { it.isNotBlank() }
            ?: "${dtStart.value}@local"
        val title = event.getProperty<Property>(Property.SUMMARY)?.value?.trim().orEmpty()
            .ifBlank { "(No title)" }
        val location = event.getProperty<Property>(Property.LOCATION)?.value?.trim().orEmpty()
        val rule = event.getProperty<Property>(Property.RRULE)?.value?.let { RecurrenceRule.parse(it, defaultTz) }
        val exceptions = mutableSetOf<Long>()
        for (exdate in event.getProperties<Property>(Property.EXDATE)) {
            val tzid = exdate.getParameter<Parameter>(Parameter.TZID)?.value
            for (v in exdate.value.split(',')) {
                IcsDateTime.parse(v.trim(), tzid, defaultTz)?.let { exceptions.add(it.millis) }
            }
        }
        return ParsedEvent(
            uid = uid,
            title = title,
            location = location,
            startMillis = start.millis,
            endMillis = endMillis.coerceAtLeast(start.millis),
            allDay = allDay,
            rule = rule,
            exceptions = exceptions,
        )
    }
}

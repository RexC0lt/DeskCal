package com.deskcal.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "occurrences",
    primaryKeys = ["calendarPath", "eventUid", "startMillis"],
    indices = [Index(value = ["startMillis"]), Index(value = ["calendarPath"])],
)
data class OccurrenceEntity(
    val calendarPath: String,
    val eventUid: String,
    val startMillis: Long,
    val title: String,
    val location: String,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarColor: String?,
)

@Entity(tableName = "calendars", primaryKeys = ["path"])
data class CalendarEntity(
    val path: String,
    val displayName: String,
    val color: String?,
)

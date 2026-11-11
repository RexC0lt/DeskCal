package com.deskcal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OccurrenceEntity::class, CalendarEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun calendarDao(): CalendarDao
}

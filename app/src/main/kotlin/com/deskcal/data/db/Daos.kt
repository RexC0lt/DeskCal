package com.deskcal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OccurrenceDao {

    @Query(
        "SELECT * FROM occurrences " +
            "WHERE startMillis < :rangeEnd AND endMillis > :rangeStart " +
            "ORDER BY startMillis ASC"
    )
    fun observeRange(rangeStart: Long, rangeEnd: Long): Flow<List<OccurrenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(occurrences: List<OccurrenceEntity>)

    @Query(
        "DELETE FROM occurrences WHERE calendarPath = :path " +
            "AND (startMillis >= :rangeEnd OR endMillis <= :rangeStart)"
    )
    suspend fun deleteOutsideRange(path: String, rangeStart: Long, rangeEnd: Long)

    @Query("DELETE FROM occurrences WHERE calendarPath = :path")
    suspend fun deleteForCalendar(path: String)
}

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendars ORDER BY displayName ASC")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(calendars: List<CalendarEntity>)

    @Query("SELECT * FROM calendars ORDER BY displayName ASC")
    suspend fun getAll(): List<CalendarEntity>
}

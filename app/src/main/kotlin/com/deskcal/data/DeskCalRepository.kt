package com.deskcal.data

import com.deskcal.caldav.CalDavClient
import com.deskcal.caldav.CalDavException
import com.deskcal.caldav.DiscoveredCalendar
import com.deskcal.data.db.AppDatabase
import com.deskcal.data.db.CalendarEntity
import com.deskcal.data.db.OccurrenceEntity
import com.deskcal.ical.IcsParser
import com.deskcal.ical.RecurrenceExpander
import com.deskcal.util.Clock
import com.deskcal.week.OccurrenceLike
import com.deskcal.week.WeekLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex

class DeskCalRepository(
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val clientFactory: (Settings) -> CalDavClient,
    private val clock: Clock,
) {

    private val syncMutex = Mutex()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    fun setOnline(online: Boolean) {
        _online.value = online
    }

    fun observeRange(rangeStart: Long, rangeEnd: Long): Flow<List<OccurrenceLike>> =
        db.occurrenceDao().observeRange(rangeStart, rangeEnd).map { rows ->
            rows.map {
                OccurrenceLike(
                    uid = it.eventUid,
                    title = it.title,
                    location = it.location,
                    startMillis = it.startMillis,
                    endMillis = it.endMillis,
                    allDay = it.allDay,
                    calendarColor = it.calendarColor,
                )
            }
        }

    fun observeCalendars(): Flow<List<CalendarEntity>> = db.calendarDao().observeAll()

    suspend fun discoverCalendars(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): List<DiscoveredCalendar> {
        val client = CalDavClient.create(serverUrl, username, password, trustSelfSigned)
        val calendars = client.discover()
        db.calendarDao().upsertAll(
            calendars.map { CalendarEntity(path = it.path, displayName = it.displayName, color = it.color) }
        )
        return calendars
    }

    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): Result<List<DiscoveredCalendar>> =
        try {
            Result.success(discoverCalendars(serverUrl, username, password, trustSelfSigned))
        } catch (e: Throwable) {
            Result.failure(CalDavException.from(e))
        }

    suspend fun syncNow(anchorWeek: Long? = null) {
        if (!syncMutex.tryLock()) return
        try {
            val s = settings.settingsFlow.value
            if (!s.setupCompleted || s.serverUrl.isBlank()) {
                _syncStatus.value = SyncStatus.Idle
                return
            }
            _syncStatus.value = SyncStatus.Syncing
            val now = clock.now()
            val anchor = anchorWeek ?: WeekLogic.weekStart(now)
            val rangeStart = WeekLogic.addDays(WeekLogic.weekStart(anchor), -1)
            val rangeEnd = maxOf(now, anchor) + s.lookAheadWeeks * 7L * WeekLogic.DAY_MILLIS
            val client = clientFactory(s)
            val colors = db.calendarDao().getAll().associate { it.path to it.color }
            var firstError: Throwable? = null
            for (path in s.selectedCalendars) {
                try {
                    val url = client.resolve(path) ?: continue
                    val icsBodies = client.fetchEvents(url, rangeStart, rangeEnd)
                    val rows = mutableListOf<OccurrenceEntity>()
                    for (body in icsBodies) {
                        try {
                            for (event in IcsParser.parseCalendar(body)) {
                                for (occ in RecurrenceExpander.expand(event, rangeStart, rangeEnd)) {
                                    rows.add(
                                        OccurrenceEntity(
                                            calendarPath = path,
                                            eventUid = occ.uid,
                                            startMillis = occ.startMillis,
                                            title = occ.title,
                                            location = occ.location,
                                            endMillis = occ.endMillis,
                                            allDay = occ.allDay,
                                            calendarColor = colors[path],
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                    db.occurrenceDao().deleteOutsideRange(path, rangeStart, rangeEnd)
                    db.occurrenceDao().insertAll(rows)
                } catch (e: Throwable) {
                    if (firstError == null) firstError = e
                }
            }
            val at = clock.now()
            if (firstError == null) {
                _syncStatus.value = SyncStatus.Ok(at)
                settings.update { copy(lastSyncAt = at, lastSyncError = null) }
            } else {
                val message = CalDavException.describe(firstError)
                _syncStatus.value = SyncStatus.Error(message, at)
                settings.update { copy(lastSyncError = message) }
            }
        } finally {
            syncMutex.unlock()
        }
    }
}

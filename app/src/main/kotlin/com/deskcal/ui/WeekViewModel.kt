package com.deskcal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deskcal.data.DeskCalRepository
import com.deskcal.data.Settings
import com.deskcal.data.SettingsRepository
import com.deskcal.data.SyncStatus
import com.deskcal.util.Clock
import com.deskcal.week.OccurrenceLike
import com.deskcal.week.WeekLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeekUiState(
    val displayedWeekStart: Long,
    val currentWeekStart: Long,
    val now: Long,
    val occurrences: List<OccurrenceLike> = emptyList(),
    val settings: Settings = Settings(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val online: Boolean = true,
    val loading: Boolean = true,
)

class WeekViewModel(
    private val repository: DeskCalRepository,
    private val settingsRepo: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _displayedWeek = MutableStateFlow(WeekLogic.weekStart(clock.now()))
    private val _currentWeek = MutableStateFlow(WeekLogic.weekStart(clock.now()))
    private val _now = MutableStateFlow(clock.now())

    val uiState: StateFlow<WeekUiState> =
        combine(
            combine(repository.syncStatus, repository.online) { sync, online -> sync to online },
            _displayedWeek,
            _currentWeek,
            _now,
            settingsRepo.settingsFlow,
        ) { (sync, online), displayed, current, now, settings ->
            Quint(displayed, current, now, settings, sync, online)
        }
            .flatMapLatest { t ->
                val rangeStart = WeekLogic.addWeeks(t.current, -8)
                val rangeEnd = WeekLogic.addWeeks(t.current, t.settings.lookAheadWeeks + 4)
                repository.observeRange(rangeStart, rangeEnd).map { occurrences ->
                    WeekUiState(
                        displayedWeekStart = t.displayed,
                        currentWeekStart = t.current,
                        now = t.now,
                        occurrences = occurrences,
                        settings = t.settings,
                        syncStatus = t.sync,
                        online = t.online,
                        loading = false,
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = WeekUiState(
                    displayedWeekStart = _displayedWeek.value,
                    currentWeekStart = _currentWeek.value,
                    now = _now.value,
                    settings = settingsRepo.settingsFlow.value,
                ),
            )

    init {
        viewModelScope.launch {
            while (true) {
                val now = clock.now()
                _now.value = now
                val current = WeekLogic.weekStart(now)
                if (current != _currentWeek.value) {
                    _currentWeek.value = current
                }
                delay(TICK_MS)
            }
        }
    }

    fun onPageSelected(page: Int, currentWeek: Long) {
        _displayedWeek.value = WeekLogic.addWeeks(currentWeek, page - CENTER_PAGE)
    }

    fun refresh() {
        viewModelScope.launch { repository.syncNow(_displayedWeek.value) }
    }

    private data class Quint(
        val displayed: Long,
        val current: Long,
        val now: Long,
        val settings: Settings,
        val sync: SyncStatus,
        val online: Boolean,
    )

    class Factory(
        private val repository: DeskCalRepository,
        private val settingsRepo: SettingsRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WeekViewModel(repository, settingsRepo, clock) as T
        }
    }

    companion object {
        const val CENTER_PAGE = 500
        const val PAGE_COUNT = 1001
        private const val TICK_MS = 15_000L
    }
}

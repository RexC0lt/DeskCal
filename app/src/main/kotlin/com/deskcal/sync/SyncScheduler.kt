package com.deskcal.sync

import com.deskcal.data.DeskCalRepository
import com.deskcal.data.Settings
import com.deskcal.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncScheduler(
    private val scope: CoroutineScope,
    private val repository: DeskCalRepository,
    private val settings: SettingsRepository,
) {

    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch {
            while (isActive) {
                repository.syncNow()
                val minutes = settings.settingsFlow.value.refreshIntervalMinutes
                    .coerceAtLeast(Settings.MIN_REFRESH_MINUTES)
                delay(minutes * 60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun restart() = start()

    fun onConnectivityRestored() {
        scope.launch { repository.syncNow() }
    }
}

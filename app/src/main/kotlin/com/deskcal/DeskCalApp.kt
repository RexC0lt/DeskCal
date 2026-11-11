package com.deskcal

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deskcal.di.AppContainer
import com.deskcal.sync.SyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DeskCalApp : Application() {

    lateinit var container: AppContainer
        private set

    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        container.repository.setOnline(cm.activeNetwork != null)

        connectivityCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                container.repository.setOnline(true)
                container.syncScheduler.onConnectivityRestored()
            }

            override fun onLost(network: Network) {
                if (cm.activeNetwork == null) {
                    container.repository.setOnline(false)
                }
            }
        }
        cm.registerDefaultNetworkCallback(connectivityCallback!!)

        container.syncScheduler.start()

        var lastInterval = container.settings.settingsFlow.value.refreshIntervalMinutes
        container.appScope.launch {
            container.settings.settingsFlow.collect { s ->
                if (s.refreshIntervalMinutes != lastInterval) {
                    lastInterval = s.refreshIntervalMinutes
                    schedulePeriodicWork(s.refreshIntervalMinutes)
                    container.syncScheduler.restart()
                }
            }
        }
        schedulePeriodicWork(lastInterval)
    }

    private fun schedulePeriodicWork(intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceAtLeast(WORKMANAGER_MIN_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityCallback?.let { cm.unregisterNetworkCallback(it) }
    }

    companion object {
        const val SYNC_WORK_NAME = "deskcal_periodic_sync"
        private const val WORKMANAGER_MIN_MINUTES = 15
    }
}

package com.deskcal.di

import android.content.Context
import androidx.room.Room
import com.deskcal.caldav.CalDavClient
import com.deskcal.data.DeskCalRepository
import com.deskcal.data.Settings
import com.deskcal.data.SettingsRepository
import com.deskcal.data.db.AppDatabase
import com.deskcal.sync.SyncScheduler
import com.deskcal.ui.WeekViewModel
import com.deskcal.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val settings: SettingsRepository = SettingsRepository(appContext)

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "deskcal.db").build()

    val clock: Clock = Clock.SYSTEM

    val clientFactory: (Settings) -> CalDavClient = { s ->
        CalDavClient.create(s.serverUrl, s.username, s.password, s.trustSelfSigned)
    }

    val repository: DeskCalRepository = DeskCalRepository(settings, db, clientFactory, clock)

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncScheduler: SyncScheduler = SyncScheduler(appScope, repository, settings)

    fun weekViewModelFactory(): WeekViewModel.Factory = WeekViewModel.Factory(repository, settings, clock)
}

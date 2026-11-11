package com.deskcal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        SECURE_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settingsFlow: StateFlow<Settings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        securePrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(): Settings = Settings(
        serverUrl = securePrefs.getString(KEY_SERVER_URL, "").orEmpty(),
        username = securePrefs.getString(KEY_USERNAME, "").orEmpty(),
        password = securePrefs.getString(KEY_PASSWORD, "").orEmpty(),
        trustSelfSigned = securePrefs.getBoolean(KEY_TRUST_SELF_SIGNED, false),
        selectedCalendars = prefs.getStringSet(KEY_SELECTED_CALENDARS, emptySet())?.toSet() ?: emptySet(),
        refreshIntervalMinutes = prefs.getInt(KEY_REFRESH_INTERVAL, Settings.DEFAULT_REFRESH_MINUTES),
        timeFormat24h = prefs.getBoolean(KEY_24H, true),
        hideEmptyDays = prefs.getBoolean(KEY_HIDE_EMPTY, false),
        nightModeEnabled = prefs.getBoolean(KEY_NIGHT_MODE, false),
        nightModeStartHour = prefs.getInt(KEY_NIGHT_START, 22),
        nightModeEndHour = prefs.getInt(KEY_NIGHT_END, 7),
        launchOnBoot = prefs.getBoolean(KEY_BOOT, false),
        burnInProtection = prefs.getBoolean(KEY_BURNIN, true),
        lookAheadWeeks = prefs.getInt(KEY_LOOKAHEAD, Settings.DEFAULT_LOOKAHEAD_WEEKS),
        setupCompleted = prefs.getBoolean(KEY_SETUP_DONE, false),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        lastSyncError = prefs.getString(KEY_LAST_ERROR, null),
    )

    fun update(block: Settings.() -> Settings) {
        val current = _settings.value
        val next = current.block()
        if (next == current) return
        prefs.edit().apply {
            putStringSet(KEY_SELECTED_CALENDARS, next.selectedCalendars)
            putInt(KEY_REFRESH_INTERVAL, next.refreshIntervalMinutes)
            putBoolean(KEY_24H, next.timeFormat24h)
            putBoolean(KEY_HIDE_EMPTY, next.hideEmptyDays)
            putBoolean(KEY_NIGHT_MODE, next.nightModeEnabled)
            putInt(KEY_NIGHT_START, next.nightModeStartHour)
            putInt(KEY_NIGHT_END, next.nightModeEndHour)
            putBoolean(KEY_BOOT, next.launchOnBoot)
            putBoolean(KEY_BURNIN, next.burnInProtection)
            putInt(KEY_LOOKAHEAD, next.lookAheadWeeks)
            putBoolean(KEY_SETUP_DONE, next.setupCompleted)
            putLong(KEY_LAST_SYNC, next.lastSyncAt)
            if (next.lastSyncError == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, next.lastSyncError)
            apply()
        }
        securePrefs.edit().apply {
            putString(KEY_SERVER_URL, next.serverUrl)
            putString(KEY_USERNAME, next.username)
            putString(KEY_PASSWORD, next.password)
            putBoolean(KEY_TRUST_SELF_SIGNED, next.trustSelfSigned)
            apply()
        }
    }

    companion object {
        private const val SECURE_FILE = "deskcal_secure"
        private const val PLAIN_FILE = "deskcal_settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TRUST_SELF_SIGNED = "trust_self_signed"
        private const val KEY_SELECTED_CALENDARS = "selected_calendars"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval_minutes"
        private const val KEY_24H = "time_format_24h"
        private const val KEY_HIDE_EMPTY = "hide_empty_days"
        private const val KEY_NIGHT_MODE = "night_mode_enabled"
        private const val KEY_NIGHT_START = "night_mode_start_hour"
        private const val KEY_NIGHT_END = "night_mode_end_hour"
        private const val KEY_BOOT = "launch_on_boot"
        private const val KEY_BURNIN = "burn_in_protection"
        private const val KEY_LOOKAHEAD = "look_ahead_weeks"
        private const val KEY_SETUP_DONE = "setup_completed"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_LAST_ERROR = "last_sync_error"
    }
}

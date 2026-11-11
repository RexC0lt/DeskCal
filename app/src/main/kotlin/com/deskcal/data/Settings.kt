package com.deskcal.data

data class Settings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
    val selectedCalendars: Set<String> = emptySet(),
    val refreshIntervalMinutes: Int = 15,
    val timeFormat24h: Boolean = true,
    val hideEmptyDays: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val nightModeStartHour: Int = 22,
    val nightModeEndHour: Int = 7,
    val launchOnBoot: Boolean = false,
    val burnInProtection: Boolean = true,
    val lookAheadWeeks: Int = 8,
    val setupCompleted: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncError: String? = null,
) {
    companion object {
        const val DEFAULT_REFRESH_MINUTES = 15
        const val DEFAULT_LOOKAHEAD_WEEKS = 8
        const val MIN_REFRESH_MINUTES = 5
    }
}

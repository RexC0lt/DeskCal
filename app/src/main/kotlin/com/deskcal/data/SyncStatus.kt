package com.deskcal.data

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Ok(val atMillis: Long) : SyncStatus
    data class Error(val message: String, val atMillis: Long) : SyncStatus
}

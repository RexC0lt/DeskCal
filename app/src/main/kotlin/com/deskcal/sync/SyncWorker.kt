package com.deskcal.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deskcal.DeskCalApp

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            (applicationContext as DeskCalApp).container.repository.syncNow()
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }
}

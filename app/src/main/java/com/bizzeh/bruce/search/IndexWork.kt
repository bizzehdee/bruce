package com.bizzeh.bruce.search

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bizzeh.bruce.appContainer
import java.util.concurrent.TimeUnit

/**
 * Index updates run as background work (WorkManager, as scheduled tasks do): at once when grants
 * change or the user asks, and daily while charging to pick up changed files.
 */
class IndexWork(private val context: Context) {
    fun now() {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<IndexWorker>().build())
    }

    fun daily() {
        val request = PeriodicWorkRequestBuilder<IndexWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
        WorkManager.getInstance(context).cancelUniqueWork(DAILY)
    }

    private companion object {
        const val NOW = "file-index"
        const val DAILY = "file-index-daily"
    }
}

class IndexWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        if (container.searchModelInstalled()) container.fileSearch.update()
        return Result.success()
    }
}

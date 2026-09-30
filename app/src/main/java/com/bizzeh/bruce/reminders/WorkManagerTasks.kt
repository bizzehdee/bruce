package com.bizzeh.bruce.reminders

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bizzeh.bruce.appContainer
import java.util.concurrent.TimeUnit

/**
 * Scheduled tasks through WorkManager (owner, 2026-09-30): one unique job per task, kept across
 * reboots, with the charging condition when a phone model will run it. A run fits WorkManager's
 * ten minutes (a model load and one turn of at most three), so no foreground service is needed.
 */
class WorkManagerTasks(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) : TaskScheduler {
    override fun enqueue(id: Long, atMillis: Long, needsCharging: Boolean) {
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInitialDelay((atMillis - clock()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresCharging(needsCharging).build())
            .setInputData(workDataOf(TASK_ID to id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(name(id))
    }

    private fun name(id: Long) = "scheduled-task-$id"

    companion object {
        const val TASK_ID = "task"
    }
}

class ScheduledTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.scheduledTasks.runDue(inputData.getLong(WorkManagerTasks.TASK_ID, -1))
        return Result.success()
    }
}

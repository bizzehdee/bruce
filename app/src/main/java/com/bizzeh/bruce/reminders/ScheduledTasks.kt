package com.bizzeh.bruce.reminders

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Repeat(val periodMillis: Long?) {
    ONCE(null),
    DAILY(24 * 60 * 60 * 1000L),
    WEEKLY(7 * 24 * 60 * 60 * 1000L),
}

data class ScheduledTask(val id: Long, val prompt: String, val nextAt: Long, val repeat: Repeat)

/** What a scheduled run produced: the chat it was saved to and its reply, or why it failed. */
sealed interface TaskOutcome {
    data class Answered(val conversationId: Long, val reply: String) : TaskOutcome

    data class Failed(val reason: String) : TaskOutcome
}

/** Hands a task to Android's background work; WorkManagerTasks on a phone. */
interface TaskScheduler {
    /** Runs task [id] at [atMillis]; with [needsCharging], not before the phone is charging. */
    fun enqueue(id: Long, atMillis: Long, needsCharging: Boolean)

    fun cancel(id: Long)
}

/**
 * Scheduled tasks (TASK-095): a prompt Bruce runs later or repeatedly, unattended, with the reply
 * saved as a chat and announced. With the phone's own model a run waits for charging (owner,
 * 2026-09-30), as loading and running a model costs memory and battery; a server's model runs at
 * once. What needs the user's approval is not done in a run (BruceRuntime's unattended turns).
 */
class ScheduledTasks(
    private val dao: ScheduledTaskDao,
    private val scheduler: TaskScheduler,
    /** Runs one prompt as a new chat. */
    private val run: suspend (String) -> TaskOutcome,
    /** Whether the chosen model runs on the phone rather than a server. */
    private val usesPhoneModel: () -> Boolean,
    private val charging: () -> Boolean,
    private val announce: (TaskOutcome) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val upcoming: Flow<List<ScheduledTask>> = dao.all().map { rows -> rows.mapNotNull(::toTask) }

    suspend fun add(prompt: String, atMillis: Long, repeat: Repeat): ScheduledTask {
        val id = dao.insert(ScheduledTaskEntity(prompt = prompt, nextAt = atMillis, repeat = repeat.name))
        scheduler.enqueue(id, atMillis, usesPhoneModel())
        return ScheduledTask(id, prompt, atMillis, repeat)
    }

    suspend fun cancel(id: Long) {
        scheduler.cancel(id)
        dao.delete(id)
    }

    /** Called by Android's background work when task [id] is due. */
    suspend fun runDue(id: Long) {
        val task = dao.task(id)?.let(::toTask) ?: return
        if (usesPhoneModel() && !charging()) return scheduler.enqueue(id, clock(), needsCharging = true)
        announce(run(task.prompt))
        val period = task.repeat.periodMillis
        if (period == null) {
            dao.delete(id)
            return
        }
        var next = task.nextAt + period
        while (next <= clock()) next += period
        dao.reschedule(id, next)
        scheduler.enqueue(id, next, usesPhoneModel())
    }

    /**
     * Queues every task again. A force stop cancels Bruce's background work, and work queued just
     * before one can be lost (seen on the Xperia 1 II); replacing a queued job is harmless.
     */
    suspend fun rescheduleAll() {
        dao.allNow().forEach { scheduler.enqueue(it.id, it.nextAt, usesPhoneModel()) }
    }

    suspend fun deleteAll() {
        dao.allNow().forEach { scheduler.cancel(it.id) }
        dao.deleteAll()
    }

    private fun toTask(row: ScheduledTaskEntity): ScheduledTask? =
        Repeat.entries.firstOrNull { it.name == row.repeat }?.let { ScheduledTask(row.id, row.prompt, row.nextAt, it) }
}

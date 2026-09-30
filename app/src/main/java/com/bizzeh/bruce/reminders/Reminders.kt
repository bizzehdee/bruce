package com.bizzeh.bruce.reminders

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A reminder waiting to be shown at [atMillis] (TASK-094). */
data class Reminder(val id: Long, val text: String, val atMillis: Long)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val atMillis: Long,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY atMillis, id")
    fun all(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY atMillis, id")
    suspend fun allNow(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminder(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

/** A prompt Bruce runs at [nextAt], again each day or week when it repeats (TASK-095). */
@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val nextAt: Long,
    /** A [Repeat] name. */
    val repeat: String,
)

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY nextAt, id")
    fun all(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY nextAt, id")
    suspend fun allNow(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun task(id: Long): ScheduledTaskEntity?

    @Insert
    suspend fun insert(task: ScheduledTaskEntity): Long

    @Query("UPDATE scheduled_tasks SET nextAt = :nextAt WHERE id = :id")
    suspend fun reschedule(id: Long, nextAt: Long)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM scheduled_tasks")
    suspend fun deleteAll()
}

@Database(entities = [ReminderEntity::class, ScheduledTaskEntity::class], version = 2)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao

    abstract fun tasks(): ScheduledTaskDao

    companion object {
        const val NAME = "reminders.db"

        /** Version 2 (TASK-095): scheduled tasks. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `scheduled_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `prompt` TEXT NOT NULL, `nextAt` INTEGER NOT NULL, `repeat` TEXT NOT NULL)")
            }
        }
    }
}

/** Schedules the alarm that shows a reminder; AndroidReminderScheduler on a phone. */
interface ReminderScheduler {
    /** True if the alarm is exact; false if Android only allows it to come a few minutes late. */
    fun schedule(id: Long, atMillis: Long): Boolean

    fun cancel(id: Long)

    fun exactAllowed(): Boolean
}

/**
 * Reminders kept on the phone and handed to Android's alarms. They survive restarts: the rows stay
 * until shown or cancelled, and [rescheduleAll] sets their alarms again after a reboot or update.
 */
class ReminderStore(private val dao: ReminderDao, private val scheduler: ReminderScheduler) {
    val upcoming: Flow<List<Reminder>> = dao.all().map { rows -> rows.map(::toReminder) }

    /** Returns the reminder and whether its alarm is exact. */
    suspend fun add(text: String, atMillis: Long): Pair<Reminder, Boolean> {
        val id = dao.insert(ReminderEntity(text = text, atMillis = atMillis))
        return Reminder(id, text, atMillis) to scheduler.schedule(id, atMillis)
    }

    suspend fun cancel(id: Long) {
        scheduler.cancel(id)
        dao.delete(id)
    }

    /** The reminder whose alarm went off, removed now that it is shown; null if it was cancelled meanwhile. */
    suspend fun take(id: Long): Reminder? = dao.reminder(id)?.also { dao.delete(id) }?.let(::toReminder)

    suspend fun rescheduleAll() {
        dao.allNow().forEach { scheduler.schedule(it.id, it.atMillis) }
    }

    suspend fun deleteAll() {
        dao.allNow().forEach { scheduler.cancel(it.id) }
        dao.deleteAll()
    }

    private fun toReminder(row: ReminderEntity) = Reminder(row.id, row.text, row.atMillis)
}

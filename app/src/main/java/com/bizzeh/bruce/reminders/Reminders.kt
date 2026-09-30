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

@Database(entities = [ReminderEntity::class], version = 1)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao

    companion object {
        const val NAME = "reminders.db"
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

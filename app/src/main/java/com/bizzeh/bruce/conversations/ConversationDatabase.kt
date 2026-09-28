package com.bizzeh.bruce.conversations

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations", indices = [Index("archived", "updatedAt")])
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(ConversationEntity::class, ["id"], ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val position: Int,
    /** A [com.bizzeh.bruce.inference.ChatRole] name. */
    val role: String,
    val text: String,
    /** JSON array of the tool calls a reply asked for (version 2). */
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** A [com.bizzeh.bruce.chat.ToolStatus] name. */
    val toolStatus: String? = null,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archived = :archived ORDER BY updatedAt DESC, id DESC")
    fun conversations(archived: Boolean): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY position")
    suspend fun messages(id: Long): List<MessageEntity>

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Insert
    suspend fun insert(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteMessages(id: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long): Int

    @Transaction
    suspend fun replaceMessages(id: Long, messages: List<MessageEntity>, updatedAt: Long): Boolean {
        if (touch(id, updatedAt) == 0) return false
        deleteMessages(id)
        insert(messages)
        return true
    }

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET archived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean)

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 2)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao

    companion object {
        const val NAME = "conversations.db"

        /** Version 2 (TASK-053): tool calls and tool results are saved with the chat. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("toolCallsJson", "toolCallId", "toolName", "toolStatus").forEach { db.execSQL("ALTER TABLE messages ADD COLUMN $it TEXT") }
            }
        }
    }
}

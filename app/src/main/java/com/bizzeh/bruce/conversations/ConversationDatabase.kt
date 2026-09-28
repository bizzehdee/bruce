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

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao

    companion object {
        const val NAME = "conversations.db"
    }
}

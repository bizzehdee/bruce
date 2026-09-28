package com.bizzeh.bruce.conversations

import com.bizzeh.bruce.chat.ChatEntry
import com.bizzeh.bruce.inference.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A saved chat as the drawer and Archived screen list it. */
data class Conversation(val id: Long, val title: String, val updatedAt: Long, val archived: Boolean)

/** Saved conversations: every chat is saved automatically after each turn. */
class ConversationStore(
    private val dao: ConversationDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val active: Flow<List<Conversation>> = dao.conversations(archived = false).map { it.map(::toConversation) }
    val archived: Flow<List<Conversation>> = dao.conversations(archived = true).map { it.map(::toConversation) }

    /**
     * Saves [entries] as conversation [id], or as a new conversation titled from the first user
     * message when [id] is null or no longer exists (deleted meanwhile). Returns the id saved to.
     */
    suspend fun save(id: Long?, entries: List<ChatEntry>): Long {
        val now = clock()
        val messages = { target: Long ->
            entries.mapIndexed { index, entry -> MessageEntity(conversationId = target, position = index, role = entry.role.name, text = entry.text) }
        }
        if (id != null && dao.replaceMessages(id, messages(id), now)) return id
        val created = dao.insert(ConversationEntity(title = titleFor(entries), createdAt = now, updatedAt = now))
        dao.insert(messages(created))
        return created
    }

    /** The conversation's messages, or null if it no longer exists. Messages with a role this version does not know are skipped. */
    suspend fun load(id: Long): List<ChatEntry>? {
        dao.conversation(id) ?: return null
        return dao.messages(id).mapNotNull { message ->
            ChatRole.entries.firstOrNull { it.name == message.role }?.let { ChatEntry(it, message.text) }
        }
    }

    /** Blank titles are ignored; titles are trimmed and capped at [MAX_TITLE_LENGTH]. */
    suspend fun rename(id: Long, title: String) {
        val cleaned = title.trim().take(MAX_TITLE_LENGTH)
        if (cleaned.isNotEmpty()) dao.rename(id, cleaned)
    }

    suspend fun archive(ids: Collection<Long>) = dao.setArchived(ids.toList(), archived = true)

    suspend fun restore(ids: Collection<Long>) = dao.setArchived(ids.toList(), archived = false)

    suspend fun delete(ids: Collection<Long>) = dao.delete(ids.toList())

    suspend fun deleteAll() = dao.deleteAll()

    private fun toConversation(entity: ConversationEntity) = Conversation(entity.id, entity.title, entity.updatedAt, entity.archived)

    companion object {
        const val MAX_TITLE_LENGTH = 100

        /** Titles made from the first message are cut to about this many characters, at a word boundary where one is near. */
        const val AUTO_TITLE_LENGTH = 40

        fun titleFor(entries: List<ChatEntry>): String {
            val first = entries.firstOrNull { it.role == ChatRole.USER }?.text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            if (first.length <= AUTO_TITLE_LENGTH) return first
            val cut = first.take(AUTO_TITLE_LENGTH)
            val space = cut.lastIndexOf(' ')
            return (if (space >= AUTO_TITLE_LENGTH / 2) cut.take(space) else cut).trimEnd() + "…"
        }
    }
}

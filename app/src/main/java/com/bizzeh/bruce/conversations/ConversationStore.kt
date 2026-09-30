package com.bizzeh.bruce.conversations

import com.bizzeh.bruce.chat.ChatDocument
import com.bizzeh.bruce.chat.ChatEntry
import com.bizzeh.bruce.chat.ChatImages
import com.bizzeh.bruce.chat.ToolStatus
import com.bizzeh.bruce.chat.ToolUse
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        val messages = { target: Long -> entries.mapIndexed { index, entry -> toEntity(target, index, entry) } }
        if (id != null && dao.replaceMessages(id, messages(id), now)) return id
        val created = dao.insert(ConversationEntity(title = titleFor(entries), createdAt = now, updatedAt = now))
        dao.insert(messages(created))
        return created
    }

    /** The conversation's messages, or null if it no longer exists. Messages with a role this version does not know are skipped. */
    suspend fun load(id: Long): List<ChatEntry>? {
        dao.conversation(id) ?: return null
        return dao.messages(id).mapNotNull(::toEntry)
    }

    /** Chats [ids], or every chat (archived too) when null, newest first; each is read as it is written out. */
    fun exported(ids: Set<Long>?): Flow<ExportedChat> = flow {
        dao.allConversations().filter { ids == null || it.id in ids }.forEach { chat ->
            emit(ExportedChat(chat.title, chat.createdAt, chat.updatedAt, chat.archived, dao.messages(chat.id).mapNotNull(::toEntry)))
        }
    }

    private fun toEntity(conversationId: Long, position: Int, entry: ChatEntry) = MessageEntity(
        conversationId = conversationId,
        position = position,
        role = entry.role.name,
        text = entry.text,
        toolCallsJson = entry.toolCalls.takeIf { it.isNotEmpty() }?.let { calls ->
            JSONArray(calls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson).put("id", it.id) }).toString()
        },
        toolCallId = entry.tool?.callId,
        toolName = entry.tool?.name,
        toolStatus = entry.tool?.status?.name,
        imagesJson = entry.images.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
        documentsJson = entry.documents.takeIf { it.isNotEmpty() }?.let { documents ->
            JSONArray(documents.map { JSONObject().put("name", it.name).put("text", it.text).put("truncated", it.truncated) }).toString()
        },
    )

    private fun toEntry(message: MessageEntity): ChatEntry? {
        val role = ChatRole.entries.firstOrNull { it.name == message.role } ?: return null
        val calls = message.toolCallsJson?.let { json ->
            try {
                val array = JSONArray(json)
                (0 until array.length()).map { array.getJSONObject(it) }.map { ToolCall(it.getString("name"), it.getString("arguments"), it.optString("id")) }
            } catch (e: JSONException) {
                emptyList()
            }
        }.orEmpty()
        val tool = if (role == ChatRole.TOOL) {
            // An unknown status from another version is shown as refused rather than as having run.
            val status = ToolStatus.entries.firstOrNull { it.name == message.toolStatus } ?: ToolStatus.REFUSED
            ToolUse(message.toolCallId.orEmpty(), message.toolName.orEmpty(), message.text, status)
        } else {
            null
        }
        return ChatEntry(role, message.text, toolCalls = calls, tool = tool, images = images(message.imagesJson), documents = documents(message.documentsJson))
    }

    private fun documents(json: String?): List<ChatDocument> = json?.let {
        try {
            val array = JSONArray(it)
            (0 until array.length()).map(array::getJSONObject).map { d -> ChatDocument(d.getString("name"), d.getString("text"), d.optBoolean("truncated")) }
        } catch (e: JSONException) {
            emptyList()
        }
    }.orEmpty()

    /** Stored names only: anything else in the column is dropped, never used as a path. */
    private fun images(json: String?): List<String> = json?.let {
        try {
            val array = JSONArray(it)
            (0 until array.length()).map(array::optString).filter(ChatImages::isName)
        } catch (e: JSONException) {
            emptyList()
        }
    }.orEmpty()

    /** Every image a saved chat still shows, so the others can be deleted (TASK-082). */
    suspend fun imagesInUse(): Set<String> = dao.imageLists().flatMap(::images).toSet()

    /** Blank titles are ignored; titles are trimmed and capped at [MAX_TITLE_LENGTH]. */
    suspend fun rename(id: Long, title: String) {
        val cleaned = title.trim().take(MAX_TITLE_LENGTH)
        if (cleaned.isNotEmpty()) dao.rename(id, cleaned)
    }

    suspend fun setProfile(id: Long, profileId: String?) = dao.setProfile(id, profileId)

    suspend fun profile(id: Long): String? = dao.conversation(id)?.profileId

    suspend fun archive(ids: Collection<Long>) = dao.setArchived(ids.toList(), archived = true)

    suspend fun restore(ids: Collection<Long>) = dao.setArchived(ids.toList(), archived = false)

    suspend fun delete(ids: Collection<Long>) = dao.delete(ids.toList())

    suspend fun deleteAll() = dao.deleteAll()

    private fun toConversation(entity: ConversationEntity) = Conversation(entity.id, entity.title, entity.updatedAt, entity.archived)

    companion object {
        const val MAX_TITLE_LENGTH = 100

        /** Titles made from the first message are cut to about this many characters, at a word boundary where one is near. */
        const val AUTO_TITLE_LENGTH = 40

        const val IMAGE_TITLE = "Image"

        fun titleFor(entries: List<ChatEntry>): String {
            val request = entries.firstOrNull { it.role == ChatRole.USER }
            val first = request?.text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            if (first.isEmpty() && request?.images?.isNotEmpty() == true) return IMAGE_TITLE
            if (first.isEmpty()) request?.documents?.firstOrNull()?.let { return it.name.take(AUTO_TITLE_LENGTH) }
            if (first.length <= AUTO_TITLE_LENGTH) return first
            val cut = first.take(AUTO_TITLE_LENGTH)
            val space = cut.lastIndexOf(' ')
            return (if (space >= AUTO_TITLE_LENGTH / 2) cut.take(space) else cut).trimEnd() + "…"
        }
    }
}

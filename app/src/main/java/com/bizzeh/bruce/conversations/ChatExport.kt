package com.bizzeh.bruce.conversations

import com.bizzeh.bruce.chat.ChatEntry
import com.bizzeh.bruce.chat.ThinkingText
import com.bizzeh.bruce.inference.ChatRole
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportFormat(val mimeType: String, val extension: String) {
    MARKDOWN("text/markdown", "md"),
    JSON("application/json", "json"),
}

/** A saved chat as exported: only what the chat holds, never memory, settings or keys. */
data class ExportedChat(val title: String, val createdAt: Long, val updatedAt: Long, val archived: Boolean, val entries: List<ChatEntry>)

/**
 * Writes chats for the user to keep (TASK-078): Markdown to read, JSON with everything the chat
 * holds (reasoning, skill calls and results as the model saw them) for a later import.
 */
class ChatExport(private val zone: ZoneId) {
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)

    /** Returns how many chats were written. */
    suspend fun write(format: ExportFormat, chats: Flow<ExportedChat>, out: Writer, exportedAt: Long): Int = when (format) {
        ExportFormat.MARKDOWN -> markdown(chats, out, exportedAt)
        ExportFormat.JSON -> json(chats, out, exportedAt)
    }

    /** A file name for the export: the chat's title for one chat, otherwise dated. */
    fun fileName(format: ExportFormat, singleTitle: String?, exportedAt: Long): String {
        val base = singleTitle?.let(::safeFileName)?.takeIf { it.isNotEmpty() }
            ?: "Bruce chats ${DateTimeFormatter.ISO_LOCAL_DATE.withZone(zone).format(Instant.ofEpochMilli(exportedAt))}"
        return "$base.${format.extension}"
    }

    private suspend fun markdown(chats: Flow<ExportedChat>, out: Writer, exportedAt: Long): Int {
        var count = 0
        out.write("Exported from Bruce on ${dateTime.format(Instant.ofEpochMilli(exportedAt))}.\n")
        chats.collect { chat ->
            count++
            out.write("\n---\n\n# ${chat.title.ifBlank { UNTITLED }.replace('\n', ' ')}\n\n")
            out.write("Started ${dateTime.format(Instant.ofEpochMilli(chat.createdAt))}, last message ${dateTime.format(Instant.ofEpochMilli(chat.updatedAt))}")
            out.write(if (chat.archived) ". Archived.\n" else ".\n")
            chat.entries.forEach { out.write(markdownEntry(it)) }
        }
        return count
    }

    private fun markdownEntry(entry: ChatEntry): String = when (entry.role) {
        ChatRole.USER -> "\n**You:**\n\n" + images(entry.images.size) + entry.documents.joinToString("") { "*(Document: ${it.name})*\n\n" } + "${entry.text}\n"
        ChatRole.SYSTEM -> "\n**Summary of earlier messages:**\n\n${entry.text}\n"
        ChatRole.ASSISTANT -> buildString {
            val answer = ThinkingText.split(entry.text).answer.trim()
            append("\n**Bruce:**\n\n")
            if (answer.isNotEmpty()) append(answer).append('\n')
            entry.toolCalls.forEach { append("\n- Asked to use `").append(it.name).append("` with ").append(code(it.argumentsJson)).append('\n') }
        }
        ChatRole.TOOL -> entry.tool?.let { tool ->
            "\n*Result of `${tool.name}` (${tool.status.name.lowercase().replace('_', ' ')}):*\n\n${fenced(tool.resultJson)}\n"
        }.orEmpty()
    }

    private suspend fun json(chats: Flow<ExportedChat>, out: Writer, exportedAt: Long): Int {
        var count = 0
        out.write("{\"format\":${JSONObject.quote(JSON_FORMAT)},\"version\":$JSON_VERSION,\"exportedAt\":${JSONObject.quote(Instant.ofEpochMilli(exportedAt).toString())},\"conversations\":[")
        chats.collect { chat ->
            if (count++ > 0) out.write(",")
            out.write(jsonChat(chat).toString())
        }
        out.write("]}\n")
        return count
    }

    private fun jsonChat(chat: ExportedChat) = JSONObject()
        .put("title", chat.title)
        .put("createdAt", Instant.ofEpochMilli(chat.createdAt).toString())
        .put("updatedAt", Instant.ofEpochMilli(chat.updatedAt).toString())
        .put("archived", chat.archived)
        .put("messages", JSONArray(chat.entries.map(::jsonEntry)))

    private fun jsonEntry(entry: ChatEntry): JSONObject {
        val message = JSONObject().put("role", entry.role.wireName).put("text", entry.text)
        // The images themselves stay on the phone; the export says how many there were.
        if (entry.images.isNotEmpty()) message.put("imageCount", entry.images.size)
        if (entry.documents.isNotEmpty()) {
            message.put("documents", JSONArray(entry.documents.map { JSONObject().put("name", it.name).put("text", it.text).put("truncated", it.truncated) }))
        }
        if (entry.toolCalls.isNotEmpty()) {
            message.put("toolCalls", JSONArray(entry.toolCalls.map { JSONObject().put("id", it.id).put("name", it.name).put("arguments", it.argumentsJson) }))
        }
        entry.tool?.let { message.put("tool", JSONObject().put("callId", it.callId).put("name", it.name).put("status", it.status.name.lowercase()).put("result", it.resultJson)) }
        return message
    }

    companion object {
        const val JSON_FORMAT = "bruce-chats"
        const val JSON_VERSION = 1
        const val UNTITLED = "Untitled chat"
        private const val MAX_FILE_NAME = 60

        /** Characters file systems or providers refuse are replaced; the name is trimmed and capped. */
        fun safeFileName(title: String): String =
            title.map { if (it.isISOControl() || it in "/\\:*?\"<>|") ' ' else it }.joinToString("")
                .replace(Regex("\\s+"), " ").trim().trimStart('.').take(MAX_FILE_NAME).trim()

        private fun images(count: Int): String = when (count) {
            0 -> ""
            1 -> "*(1 image, not exported)*\n\n"
            else -> "*($count images, not exported)*\n\n"
        }

        /** Inline code that holds any text: the fence is one backtick longer than the longest run inside. */
        private fun code(text: String): String {
            val ticks = "`".repeat(longestBacktickRun(text) + 1)
            return "$ticks ${text.replace('\n', ' ')} $ticks"
        }

        private fun fenced(text: String): String {
            val fence = "`".repeat(maxOf(3, longestBacktickRun(text) + 1))
            return "$fence\n$text\n$fence"
        }

        private fun longestBacktickRun(text: String): Int = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
    }
}

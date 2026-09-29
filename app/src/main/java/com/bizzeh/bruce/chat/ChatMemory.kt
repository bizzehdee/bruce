package com.bizzeh.bruce.chat

import com.bizzeh.bruce.inference.ToolChatMessage

/**
 * What the chat asks of memory (TASK-047). Which memory, if any, follows from the Memory setting
 * and the loaded model, which the chat does not need to know.
 */
interface ChatMemory {
    /** Facts from earlier chats relevant to a chat that starts with [message]; empty when memory is off. */
    suspend fun recall(message: String): List<String>

    /** Saves any lasting facts from the chat's last exchange; [recalled] is what the chat already has. */
    suspend fun learn(history: List<ToolChatMessage>, recalled: List<String>)

    object Off : ChatMemory {
        override suspend fun recall(message: String) = emptyList<String>()

        override suspend fun learn(history: List<ToolChatMessage>, recalled: List<String>) = Unit
    }
}

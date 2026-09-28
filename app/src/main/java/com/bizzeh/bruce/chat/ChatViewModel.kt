package com.bizzeh.bruce.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.inference.ChatMessage
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.InferenceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One message in the conversation. [text] is the raw model output for replies, reasoning included. */
data class ChatEntry(val role: ChatRole, val text: String, val stats: GenerationStats? = null)

data class ChatState(
    val modelName: String? = null,
    val entries: List<ChatEntry> = emptyList(),
    val input: String = "",
    val generating: Boolean = false,
    val error: ChatError? = null,
)

enum class ChatError {
    NO_MODEL_LOADED,
    /** The conversation no longer fits the model's context. */
    CONVERSATION_TOO_LONG,
    GENERATION_FAILED,
}

class ChatViewModel(private val engine: InferenceEngine) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutableState.asStateFlow()

    /** Call when the chat screen is shown, since a model may have been loaded elsewhere. */
    fun refreshModel() {
        mutableState.update { it.copy(modelName = engine.getModelInfo()?.description) }
    }

    fun setInput(input: String) {
        mutableState.update { it.copy(input = input) }
    }

    fun send() {
        val current = mutableState.value
        val text = current.input.trim()
        if (text.isEmpty() || current.generating) return
        val conversation = current.entries + ChatEntry(ChatRole.USER, text)
        mutableState.update {
            it.copy(entries = conversation + ChatEntry(ChatRole.ASSISTANT, ""), input = "", generating = true, error = null)
        }
        viewModelScope.launch {
            val prompt = engine.formatChat(conversation.map(::toMessage))
            if (prompt == null) {
                finish(ChatError.NO_MODEL_LOADED)
                return@launch
            }
            engine.generate(GenerationRequest(prompt.text, maxTokens = MAX_REPLY_TOKENS)).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> updateReply { it.copy(text = it.text + event.text) }
                    is GenerationEvent.Completed -> updateReply { it.copy(stats = event.stats) }
                    is GenerationEvent.Failed -> finish(
                        when (event.error) {
                            GenerationError.NO_MODEL_LOADED -> ChatError.NO_MODEL_LOADED
                            GenerationError.PROMPT_TOO_LONG -> ChatError.CONVERSATION_TOO_LONG
                            GenerationError.DECODE_FAILED -> ChatError.GENERATION_FAILED
                        },
                    )
                }
            }
            mutableState.update { it.copy(generating = false) }
        }
    }

    fun stop() {
        engine.stop()
    }

    fun newChat() {
        if (mutableState.value.generating) engine.stop()
        mutableState.update { ChatState(modelName = it.modelName) }
    }

    /** Earlier replies go back to the model without their reasoning, as reasoning models expect. */
    private fun toMessage(entry: ChatEntry) = ChatMessage(
        role = entry.role,
        content = if (entry.role == ChatRole.ASSISTANT) ThinkingText.split(entry.text).answer else entry.text,
    )

    private fun updateReply(change: (ChatEntry) -> ChatEntry) = mutableState.update {
        it.copy(entries = it.entries.dropLast(1) + change(it.entries.last()))
    }

    /** Ends a failed turn: the empty reply is removed and the error shown. */
    private fun finish(error: ChatError) = mutableState.update { state ->
        val entries = if (state.entries.lastOrNull()?.let { it.role == ChatRole.ASSISTANT && it.text.isEmpty() } == true) {
            state.entries.dropLast(1)
        } else {
            state.entries
        }
        state.copy(entries = entries, generating = false, error = error)
    }

    private companion object {
        // Replies stop earlier when the context fills; this only bounds a runaway reply.
        const val MAX_REPLY_TOKENS = 4096
    }
}

package com.bizzeh.bruce.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.models.ActiveModelState
import com.bizzeh.bruce.policy.PolicyDecision
import com.bizzeh.bruce.runtime.ContextUse
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.RuntimeEvent
import com.bizzeh.bruce.skills.Denial
import com.bizzeh.bruce.skills.DenialCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** One message in the conversation. [text] is the model's output for replies, reasoning included. */
data class ChatEntry(
    val role: ChatRole,
    val text: String,
    val stats: GenerationStats? = null,
    /** Skills a reply asked for. */
    val toolCalls: List<ToolCall> = emptyList(),
    /** Set on [ChatRole.TOOL] entries: which skill ran and what went back to the model. */
    val tool: ToolUse? = null,
)

enum class ToolStatus { RAN, REFUSED, AWAITING_APPROVAL, DECLINED }

/** A skill's use in a chat; [resultJson] is exactly what the model was given. */
data class ToolUse(val callId: String, val name: String, val resultJson: String, val status: ToolStatus)

/** What the approval card shows for a call awaiting the user's decision. */
data class Confirmation(val callId: String, val skillId: String, val arguments: List<Pair<String, String>>, val targets: List<String>)

data class ChatState(
    val modelName: String? = null,
    /** The chosen personality's name, which the chat uses for the sidekick. */
    val sidekick: String = "Bruce",
    /** The saved conversation shown; null until the first turn of a new chat is saved. */
    val conversationId: Long? = null,
    val entries: List<ChatEntry> = emptyList(),
    /** Calls awaiting approval in this session, by call id. A saved chat reopened later has none: it must be asked again. */
    val confirmations: Map<String, Confirmation> = emptyMap(),
    val input: String = "",
    /** How much of the context this chat takes, as the next prompt would; null until measured. */
    val context: ContextUse? = null,
    val generating: Boolean = false,
    val error: ChatError? = null,
)

enum class ChatError {
    NO_MODEL_LOADED,
    /** The conversation no longer fits the model's context. */
    CONVERSATION_TOO_LONG,
    GENERATION_FAILED,
    /** The model kept calling skills past the per-turn limit. */
    TOO_MANY_TOOL_CALLS,
    /** The turn ran past its time limit. */
    TIMED_OUT,
}

class ChatViewModel(
    private val engine: InferenceEngine,
    activeModel: StateFlow<ActiveModelState>,
    /** Saves a conversation (null id: a new one) and returns the id it was saved as. */
    private val save: suspend (id: Long?, entries: List<ChatEntry>) -> Long,
    /** A saved conversation's messages, or null if it no longer exists. */
    private val load: suspend (id: Long) -> List<ChatEntry>?,
    /** One turn of the runtime (BruceRuntime.respond): replies, skill calls and results. */
    private val respond: (List<ToolChatMessage>) -> Flow<RuntimeEvent>,
    /** The user's answer to a call awaiting approval (BruceRuntime.answer). */
    private val answer: suspend (ToolCall, PolicyDecision.NeedsConfirmation, Boolean) -> RuntimeEvent.ToolResult,
    sidekick: Flow<String> = emptyFlow(),
    /** Context use of a conversation (BruceRuntime.measure). */
    private val measure: suspend (List<ToolChatMessage>) -> ContextUse? = { null },
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutableState.asStateFlow()

    /** Changes when another conversation is shown, so a turn that finishes later does not touch it. */
    private var session = 0
    private var turn: Job? = null
    private val pending = mutableMapOf<String, Pair<ToolCall, PolicyDecision.NeedsConfirmation>>()

    init {
        viewModelScope.launch {
            activeModel.collect { model ->
                mutableState.update { it.copy(modelName = model.active?.nameWithoutExtension) }
                remeasure()
            }
        }
        viewModelScope.launch {
            sidekick.collect { name -> mutableState.update { it.copy(sidekick = name) } }
        }
    }

    fun setInput(input: String) {
        mutableState.update { it.copy(input = input) }
    }

    fun send() {
        val current = mutableState.value
        val text = current.input.trim()
        if (text.isEmpty() || current.generating) return
        mutableState.update { it.copy(input = "") }
        runTurn(current.entries + ChatEntry(ChatRole.USER, text))
    }

    /** Approves or declines a call awaiting approval, then lets the model carry on with the outcome. */
    fun decide(callId: String, approved: Boolean) {
        val current = mutableState.value
        if (current.generating) return
        val (call, decision) = pending.remove(callId) ?: return
        val decidingSession = session
        mutableState.update { it.copy(confirmations = it.confirmations - callId, generating = true) }
        viewModelScope.launch {
            val result = answer(call, decision, approved)
            val status = when {
                result.ran -> ToolStatus.RAN
                approved -> ToolStatus.REFUSED
                else -> ToolStatus.DECLINED
            }
            val entries = current.entries.map { entry ->
                if (entry.tool?.callId == callId && entry.tool.status == ToolStatus.AWAITING_APPROVAL) toolEntry(call, result.resultJson, status) else entry
            }
            // Moved on while the skill ran: its outcome is still saved to the chat it belongs to.
            if (session == decidingSession) runTurn(entries) else withContext(NonCancellable) { save(current.conversationId, entries) }
        }
    }

    private fun runTurn(history: List<ChatEntry>) {
        val current = mutableState.value
        val turnSession = session
        mutableState.update { it.copy(entries = history + ChatEntry(ChatRole.ASSISTANT, ""), generating = true, error = null) }
        turn = viewModelScope.launch {
            val added = mutableListOf<ChatEntry>()
            var streaming = ChatEntry(ChatRole.ASSISTANT, "")
            var error: ChatError? = null
            val onScreen = { session == turnSession }
            fun show() {
                if (onScreen()) mutableState.update { it.copy(entries = history + added + streaming) }
            }
            try {
                respond(history.map(::toMessage)).collect { event ->
                    when (event) {
                        is RuntimeEvent.Text -> streaming = streaming.copy(text = streaming.text + event.text)
                        is RuntimeEvent.Step -> {
                            added += ChatEntry(ChatRole.ASSISTANT, event.content, event.stats, event.toolCalls)
                            streaming = ChatEntry(ChatRole.ASSISTANT, "")
                        }
                        is RuntimeEvent.ToolResult -> added += toolEntry(event.call, event.resultJson, if (event.ran) ToolStatus.RAN else ToolStatus.REFUSED)
                        is RuntimeEvent.NeedsConfirmation -> {
                            added += toolEntry(event.call, AWAITING_APPROVAL, ToolStatus.AWAITING_APPROVAL)
                            if (onScreen()) {
                                pending[event.call.id] = event.call to event.decision
                                mutableState.update { it.copy(confirmations = it.confirmations + (event.call.id to confirmation(event.call, event.decision))) }
                            }
                        }
                        is RuntimeEvent.Finished -> Unit
                        is RuntimeEvent.Failed -> error = chatError(event.error)
                    }
                    show()
                }
            } finally {
                // Stopped part-way, what was said so far is kept; saved to the chat the turn began in.
                withContext(NonCancellable) {
                    if (streaming.text.isNotEmpty()) added += streaming
                    val entries = history + added.filterNot { it.role == ChatRole.ASSISTANT && it.text.isEmpty() && it.toolCalls.isEmpty() }
                    val saved = save(current.conversationId, entries)
                    if (onScreen()) mutableState.update { it.copy(entries = entries, generating = false, error = error, conversationId = saved) }
                }
                if (onScreen()) remeasure()
            }
        }
    }

    /** Shows a saved conversation; a turn in progress is stopped and saved to its own chat. */
    fun open(id: Long) {
        viewModelScope.launch {
            val entries = load(id) ?: return@launch
            endTurn()
            session++
            pending.clear()
            mutableState.update { ChatState(modelName = it.modelName, sidekick = it.sidekick, conversationId = id, entries = entries) }
            remeasure()
        }
    }

    /** Called when conversations are archived or deleted; the chat on screen starts afresh if it was one of them. */
    fun forget(ids: Collection<Long>) {
        if (mutableState.value.conversationId in ids) newChat()
    }

    fun stop() = endTurn()

    fun newChat() {
        endTurn()
        session++
        pending.clear()
        mutableState.update { ChatState(modelName = it.modelName, sidekick = it.sidekick) }
        remeasure()
    }

    /** Measures the chat on screen when no turn is running; the engine is busy during one. */
    private fun remeasure() {
        val measuredSession = session
        viewModelScope.launch {
            if (mutableState.value.generating) return@launch
            val use = measure(mutableState.value.entries.map(::toMessage))
            if (session == measuredSession && !mutableState.value.generating) mutableState.update { it.copy(context = use) }
        }
    }

    /** Stops generation and the turn, so no skill runs after the user has moved on. */
    private fun endTurn() {
        if (turn?.isActive != true) return
        engine.stop()
        turn?.cancel()
    }

    private fun toolEntry(call: ToolCall, json: String, status: ToolStatus) =
        ChatEntry(ChatRole.TOOL, json, tool = ToolUse(call.id, call.name, json, status))

    private fun confirmation(call: ToolCall, decision: PolicyDecision.NeedsConfirmation) = Confirmation(
        callId = call.id,
        skillId = decision.request.skill.id,
        arguments = arguments(call.argumentsJson),
        targets = decision.targets.map { it.display },
    )

    /** The arguments as the user reads them; the policy engine has already validated them. */
    private fun arguments(json: String): List<Pair<String, String>> = try {
        val obj = JSONObject(json)
        obj.keys().asSequence().map { it to obj.get(it).toString() }.toList()
    } catch (e: JSONException) {
        listOf("" to json)
    }

    /** Earlier replies go back to the model without their reasoning, as reasoning models expect. */
    private fun toMessage(entry: ChatEntry) = when (entry.role) {
        ChatRole.ASSISTANT -> ToolChatMessage(ChatRole.ASSISTANT, ThinkingText.split(entry.text).answer, toolCalls = entry.toolCalls)
        ChatRole.TOOL -> ToolChatMessage(ChatRole.TOOL, entry.text, toolCallId = entry.tool?.callId.orEmpty(), toolName = entry.tool?.name.orEmpty())
        else -> ToolChatMessage(entry.role, entry.text)
    }

    private fun chatError(error: RuntimeError) = when (error) {
        RuntimeError.NO_MODEL_LOADED -> ChatError.NO_MODEL_LOADED
        RuntimeError.CONVERSATION_TOO_LONG -> ChatError.CONVERSATION_TOO_LONG
        RuntimeError.GENERATION_FAILED -> ChatError.GENERATION_FAILED
        RuntimeError.TOO_MANY_TOOL_CALLS -> ChatError.TOO_MANY_TOOL_CALLS
        RuntimeError.TIMED_OUT -> ChatError.TIMED_OUT
    }

    private companion object {
        /** What the model sees for a call still awaiting the user's approval (TASK-041). */
        val AWAITING_APPROVAL: String by lazy {
            Denial(DenialCode.CONFIRMATION_REQUIRED, "", "The user has not approved this yet.", userCanChange = true, retryable = true)
                .toJson().toString()
        }
    }
}

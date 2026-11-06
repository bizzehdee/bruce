package com.bizzeh.bruce.litert

import com.bizzeh.bruce.inference.ParsedReply
import com.bizzeh.bruce.runtime.BackendChat
import com.bizzeh.bruce.runtime.ChatBackend
import com.bizzeh.bruce.runtime.Prepared
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.StepEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * A LiteRT-LM model on the phone (TASK-118), using the Tensor G5/G6 NPU delegate where available
 * (TASK-119), CPU otherwise. Google Play and GitHub Releases builds only: LiteRT-LM ships prebuilt
 * binaries rather than building from source, so it is absent from F-Droid builds (TASK-117, owner
 * decision 2026-11-06, .learnings/litert-lm-fdroid-blocker.md).
 *
 * Unlike [com.bizzeh.bruce.inference.LlamaCppEngine], LiteRT-LM applies the model's chat template
 * and parses tool calls itself; a fresh [LiteRtConversation] is created each turn, carrying the
 * whole history Bruce already keeps, rather than Bruce formatting text or a grammar itself (ADR
 * 0001 does not apply to this pipeline).
 *
 * LiteRT-LM has no per-string token-counting API (TASK-118 research): token counts are a
 * characters-per-token estimate, corrected by the conversation's own token count after each turn,
 * the same approach [com.bizzeh.bruce.remote.RemoteBackend] uses for servers that do not report
 * usage. This is an approximation, unlike llama.cpp's exact native count.
 */
internal class LiteRtBackend(
    private val model: LiteRtModel,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatBackend {
    private data class PreparedHandle(val characters: Int)

    @Volatile private var charsPerToken = DEFAULT_CHARS_PER_TOKEN

    @Volatile private var open: LiteRtConversation? = null

    override suspend fun contextLength(): Int = model.maxContextTokens

    override suspend fun prepare(chat: BackendChat): Prepared {
        val characters = chat.system.length +
            chat.messages.sumOf { it.content.length + it.toolCalls.sumOf { call -> call.name.length + call.argumentsJson.length } } +
            chat.tools.sumOf { it.name.length + it.description.length + it.parametersJson.length }
        val tokens = (characters / charsPerToken).toInt() + PER_MESSAGE_TOKENS * (chat.messages.size + 1)
        return Prepared.Ready(chat, tokens, PreparedHandle(characters))
    }

    override fun generate(prepared: Prepared.Ready, maxTokens: Int, temperature: Float, branchOf: BackendChat?): Flow<StepEvent> = flow {
        val chat = prepared.chat
        if (chat.messages.isEmpty()) {
            emit(StepEvent.Failed(RuntimeError.GENERATION_FAILED))
            return@flow
        }
        val conversation = model.converse(chat.system, chat.messages.dropLast(1), chat.messages.last(), chat.tools, chat.thinking, maxTokens, temperature)
        open = conversation
        try {
            var failed = false
            var thinking = false
            var done: LiteRtEvent.Done? = null
            conversation.send().collect { event ->
                when (event) {
                    is LiteRtEvent.Reasoning -> {
                        if (!thinking) emit(StepEvent.Text(THINK_OPEN))
                        thinking = true
                        emit(StepEvent.Text(event.text))
                    }
                    is LiteRtEvent.Text -> {
                        if (thinking) emit(StepEvent.Text(THINK_CLOSE))
                        thinking = false
                        emit(StepEvent.Text(event.text))
                    }
                    is LiteRtEvent.Done -> done = event
                    is LiteRtEvent.Failed -> failed = true
                }
            }
            correctEstimate(prepared, conversation)
            val reply = done
            when {
                failed -> emit(StepEvent.Failed(RuntimeError.GENERATION_FAILED))
                reply != null -> emit(StepEvent.Done(ParsedReply(reply.content, reply.reasoning, reply.toolCalls), stats = null))
                else -> emit(StepEvent.Failed(RuntimeError.GENERATION_FAILED))
            }
        } finally {
            open = null
            conversation.close()
        }
    }.flowOn(dispatcher)

    private fun correctEstimate(prepared: Prepared.Ready, conversation: LiteRtConversation) {
        val characters = (prepared.handle as PreparedHandle).characters
        val used = conversation.tokenCount()
        if (used > 0) charsPerToken = characters.toDouble() / used
    }

    override fun stop() {
        open?.cancel()
    }

    companion object {
        private const val DEFAULT_CHARS_PER_TOKEN = 3.5
        private const val PER_MESSAGE_TOKENS = 4
        private const val THINK_OPEN = "<think>"
        private const val THINK_CLOSE = "</think>"
    }
}

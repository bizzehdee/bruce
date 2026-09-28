package com.bizzeh.bruce.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.ChatRole

interface ChatActions {
    fun setInput(input: String)
    fun send()
    fun stop()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    actions: ChatActions,
    navigationIcon: @Composable () -> Unit = {},
    titleAction: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = navigationIcon,
                title = {
                    TextButton(onClick = titleAction, modifier = Modifier.testTag("chatTitle")) {
                        Text(state.modelName ?: stringResource(R.string.chat_no_model), style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            Messages(state, modifier = Modifier.weight(1f))
            ChatText.error(state.error)?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("chatError"),
                )
            }
            Composer(state, actions)
        }
    }
}

@Composable
private fun Messages(state: ChatState, modifier: Modifier) {
    if (state.entries.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                if (state.modelName == null) stringResource(R.string.chat_empty) else stringResource(R.string.chat_empty_ready, state.sidekick),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.entries.size, state.entries.lastOrNull()?.text?.length) {
        listState.scrollToItem(state.entries.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag("messages"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(state.entries) { index, entry ->
            when {
                entry.role == ChatRole.USER -> UserMessage(entry.text)
                entry.tool != null -> ToolRow(index, entry.tool)
                // A reply that only asked for skills has nothing to show; its tool rows follow.
                entry.text.isEmpty() && entry.toolCalls.isNotEmpty() -> Unit
                else -> Reply(index, entry, state.sidekick)
            }
        }
    }
}

/** One skill use: its name and outcome; tapping shows exactly what the model was given. */
@Composable
private fun ToolRow(index: Int, tool: ToolUse) {
    var open by rememberSaveable(index) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag("tool:$index")) {
        TextButton(onClick = { open = !open }, modifier = Modifier.testTag("toolToggle:$index")) {
            Text(stringResource(ChatText.toolStatus(tool.status), tool.name), style = MaterialTheme.typography.labelLarge)
        }
        if (open) {
            Text(
                ChatText.toolDetail(tool.resultJson),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp).testTag("toolDetail:$index"),
            )
        }
    }
}

@Composable
private fun UserMessage(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Reply(index: Int, entry: ChatEntry, sidekick: String) {
    val reply = ThinkingText.split(entry.text)
    var showReasoning by rememberSaveable(index) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag("reply:$index"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (reply.thinking != null || reply.stillThinking) {
            TextButton(onClick = { showReasoning = !showReasoning }, modifier = Modifier.testTag("reasoningToggle:$index")) {
                Text(
                    when {
                        reply.stillThinking && !showReasoning -> stringResource(R.string.chat_thinking, sidekick)
                        showReasoning -> stringResource(R.string.chat_reasoning_hide)
                        else -> stringResource(R.string.chat_reasoning_show)
                    },
                )
            }
            if (showReasoning) {
                Text(
                    reply.thinking.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp).testTag("reasoning:$index"),
                )
            }
        }
        if (reply.answer.isNotEmpty()) {
            Text(reply.answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("answer:$index"))
        }
    }
}

@Composable
private fun Composer(state: ChatState, actions: ChatActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = actions::setInput,
            placeholder = { Text(stringResource(R.string.chat_input_hint, state.sidekick)) },
            shape = RoundedCornerShape(24.dp),
            maxLines = 5,
            modifier = Modifier.weight(1f).testTag("composer"),
        )
        if (state.generating) {
            FilledIconButton(onClick = actions::stop, modifier = Modifier.testTag("stop")) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.chat_stop))
            }
        } else {
            FilledIconButton(
                onClick = actions::send,
                enabled = state.input.isNotBlank() && state.modelName != null,
                modifier = Modifier.testTag("send"),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

internal object ChatText {
    @StringRes
    fun error(error: ChatError?): Int? = when (error) {
        null -> null
        ChatError.NO_MODEL_LOADED -> R.string.chat_error_no_model
        ChatError.CONVERSATION_TOO_LONG -> R.string.chat_error_too_long
        ChatError.GENERATION_FAILED -> R.string.chat_error_failed
        ChatError.TOO_MANY_TOOL_CALLS -> R.string.chat_error_too_many_tools
        ChatError.TIMED_OUT -> R.string.chat_error_timed_out
    }

    @StringRes
    fun toolStatus(status: ToolStatus): Int = when (status) {
        ToolStatus.RAN -> R.string.chat_tool_ran
        ToolStatus.REFUSED -> R.string.chat_tool_refused
        ToolStatus.AWAITING_APPROVAL -> R.string.chat_tool_awaiting
    }

    /** What the tool gave the model, for the expanded row: the result itself, or the refusal's message. */
    fun toolDetail(resultJson: String): String = try {
        val json = org.json.JSONObject(resultJson)
        json.optString("untrusted_data").ifEmpty { json.optString("message") }.ifEmpty { resultJson }
    } catch (e: org.json.JSONException) {
        resultJson
    }
}

package com.bizzeh.bruce.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.bizzeh.bruce.runtime.ContextUse
import com.bizzeh.bruce.skills.SkillText
import com.bizzeh.bruce.inference.ChatRole

interface ChatActions {
    fun setInput(input: String)
    fun send()
    fun stop()

    /** The user's answer to a skill call awaiting approval. */
    fun decide(callId: String, approved: Boolean)
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
            state.context?.let { ContextBar(it) }
            Messages(state, actions, modifier = Modifier.weight(1f))
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
private fun Messages(state: ChatState, actions: ChatActions, modifier: Modifier) {
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
            val dropped = state.context?.dropped ?: 0
            if (dropped > 0 && index == state.firstSeen) {
                Text(
                    stringResource(R.string.chat_context_dropped),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("contextDropped"),
                )
            }
            when {
                entry.role == ChatRole.USER -> UserMessage(entry.text)
                entry.role == ChatRole.SYSTEM -> SummaryNote(index, entry.text)
                entry.tool?.status == ToolStatus.AWAITING_APPROVAL -> ConfirmationCard(entry.tool, state.confirmations[entry.tool.callId], !state.generating, actions)
                entry.tool != null -> ToolRow(index, entry.tool)
                // A reply that only asked for skills has nothing to show; its tool rows follow.
                entry.text.isEmpty() && entry.toolCalls.isNotEmpty() -> Unit
                else -> Reply(index, entry, state.sidekick)
            }
        }
        if (state.summarising) {
            item {
                Text(
                    stringResource(R.string.chat_summarising),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("summarising"),
                )
            }
        }
    }
}

/**
 * How much of the model's context the chat takes. Tapping explains what happens when it is full;
 * near full a short note says so without being asked.
 */
@Composable
private fun ContextBar(use: ContextUse) {
    var open by rememberSaveable { mutableStateOf(false) }
    val fraction = if (use.total > 0) use.used.toFloat() / use.total else 0f
    Column(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 4.dp).testTag("contextBar"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.chat_context_use, use.used, use.total, (use.total - use.used).coerceAtLeast(0)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (use.nearlyFull) {
            Text(stringResource(R.string.chat_context_nearly_full), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("contextNearlyFull"))
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.chat_context_title)) },
            text = { Text(stringResource(R.string.chat_context_explained)) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.chat_context_ok)) } },
        )
    }
}

/**
 * A call in the Ask state: exactly what would run, with Allow once and Don't allow. A call from an
 * earlier session has no [confirmation] and can no longer be approved; the model must ask again.
 */
@Composable
private fun ConfirmationCard(tool: ToolUse, confirmation: Confirmation?, enabled: Boolean, actions: ChatActions) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("confirm:${tool.callId}")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.chat_confirm_title, SkillText.name(tool.name)), style = MaterialTheme.typography.titleSmall)
            if (confirmation == null) {
                Text(stringResource(R.string.chat_confirm_expired), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("confirmExpired:${tool.callId}"))
                return@Column
            }
            confirmation.targets.forEach { target ->
                Text(stringResource(R.string.chat_confirm_target, target), style = MaterialTheme.typography.bodyMedium)
            }
            confirmation.arguments.forEach { (name, value) ->
                Text(if (name.isEmpty()) value else "$name: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.decide(tool.callId, true) }, enabled = enabled, modifier = Modifier.testTag("approve:${tool.callId}")) {
                    Text(stringResource(R.string.chat_confirm_allow))
                }
                OutlinedButton(onClick = { actions.decide(tool.callId, false) }, enabled = enabled, modifier = Modifier.testTag("deny:${tool.callId}")) {
                    Text(stringResource(R.string.chat_confirm_deny))
                }
            }
        }
    }
}

/** A summary that replaced the messages above it in what the model sees (auto-summarise). */
@Composable
private fun SummaryNote(index: Int, text: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("summary:$index")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.chat_summary_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodySmall)
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
        ToolStatus.DECLINED -> R.string.chat_tool_declined
    }

    /** What the tool gave the model, for the expanded row: the result itself, or the refusal's message. */
    fun toolDetail(resultJson: String): String = try {
        val json = org.json.JSONObject(resultJson)
        json.optString("untrusted_data").ifEmpty { json.optString("message") }.ifEmpty { resultJson }
    } catch (e: org.json.JSONException) {
        resultJson
    }
}

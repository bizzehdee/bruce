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
                stringResource(if (state.modelName == null) R.string.chat_empty else R.string.chat_empty_ready),
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
            if (entry.role == ChatRole.USER) UserMessage(entry.text) else Reply(index, entry)
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
private fun Reply(index: Int, entry: ChatEntry) {
    val reply = ThinkingText.split(entry.text)
    var showReasoning by rememberSaveable(index) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag("reply:$index"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (reply.thinking != null || reply.stillThinking) {
            TextButton(onClick = { showReasoning = !showReasoning }, modifier = Modifier.testTag("reasoningToggle:$index")) {
                Text(
                    stringResource(
                        when {
                            reply.stillThinking && !showReasoning -> R.string.chat_thinking
                            showReasoning -> R.string.chat_reasoning_hide
                            else -> R.string.chat_reasoning_show
                        },
                    ),
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
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
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
    }
}

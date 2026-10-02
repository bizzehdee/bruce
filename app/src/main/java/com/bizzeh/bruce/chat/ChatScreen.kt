package com.bizzeh.bruce.chat

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.IconButton
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.InputChip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.bizzeh.bruce.ui.theme.LocalTrafficLights
import com.bizzeh.bruce.skills.SkillText
import com.bizzeh.bruce.inference.ChatRole

interface ChatActions {
    fun setInput(input: String)
    fun send()
    fun stop()

    /** The user's answer to a skill call awaiting approval. */
    fun decide(callId: String, approved: Boolean)

    /** Approves the call and always allows the sites it reaches from now on (TASK-068). */
    fun alwaysAllowSites(callId: String) = Unit

    /** Opens an http or https address the user confirmed, in the browser. */
    fun openLink(url: String) = Unit

    suspend fun loadImage(url: String): ImageResult = ImageResult.NotAllowed

    /** Starts listening, asking for the microphone first if needed (TASK-074). */
    fun startVoice() = Unit
    fun stopVoice() = Unit

    /** Reads entry [index]'s reply aloud (TASK-080). */
    fun readAloud(index: Int) = Unit
    fun stopReading() = Unit

    /** Opens the photo picker for an image to send (TASK-082). */
    fun attachImage() = Unit
    fun removeAttachment(name: String) = Unit

    /** Opens the document picker for a PDF or text file (TASK-084). */
    fun attachDocument() = Unit
    fun removeDocument(index: Int) = Unit

    /** A small copy of a stored chat image, for showing it; null when it has gone. */
    suspend fun thumbnail(name: String): ImageBitmap? = null

    /** Hands-free chat from a long press on the microphone (TASK-081). */
    fun startConversation() = Unit
    fun endConversation() = Unit
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
        // The Scaffold's padding already covers the navigation bar; consuming it stops imePadding adding it again.
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            state.context?.let { ContextBar(it) }
            Messages(state, actions, modifier = Modifier.weight(1f))
            if (state.profileUnavailable) {
                Text(stringResource(R.string.profiles_unavailable), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("profileUnavailable"))
            }
            ChatText.error(state.error)?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("chatError"),
                )
            }
            state.conversation?.let { ConversationBar(it, state.sidekick, actions) }
            if (state.attachments.isNotEmpty() || state.documents.isNotEmpty()) Attachments(state, actions)
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
    var confirmLink by rememberSaveable { mutableStateOf<String?>(null) }
    confirmLink?.let { url ->
        AlertDialog(
            onDismissRequest = { confirmLink = null },
            title = { Text(stringResource(R.string.markdown_link_title)) },
            text = { Text(url, modifier = Modifier.testTag("linkAddress")) },
            confirmButton = {
                TextButton(onClick = { confirmLink = null; actions.openLink(url) }, modifier = Modifier.testTag("openLink")) { Text(stringResource(R.string.markdown_link_open)) }
            },
            dismissButton = { TextButton(onClick = { confirmLink = null }) { Text(stringResource(R.string.markdown_link_cancel)) } },
        )
    }
    val markdownActions = remember(actions) {
        object : MarkdownActions {
            override fun link(url: String) {
                confirmLink = url
            }

            override suspend fun image(url: String) = actions.loadImage(url)
        }
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
                entry.role == ChatRole.USER -> UserMessage(entry, actions)
                entry.role == ChatRole.SYSTEM -> SummaryNote(index, entry.text)
                entry.tool?.status == ToolStatus.AWAITING_APPROVAL -> ConfirmationCard(entry.tool, state.confirmations[entry.tool.callId], !state.generating, actions)
                // Skill use is not shown: people want the answer (owner, 2026-09-29). It is still saved.
                entry.tool != null -> Unit
                // A reply that only asked for skills has nothing to show; its tool rows follow.
                entry.text.isEmpty() && entry.toolCalls.isNotEmpty() -> Unit
                else -> Reply(index, entry, state.sidekick, markdownActions, readAloud = state.speech.takeIf { it.available && !(state.generating && index == state.entries.lastIndex) }, actions)
            }
        }
        if (state.working) {
            item { Working(state.sidekick) }
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
        val lights = LocalTrafficLights.current
        val colour = lights?.let {
            when (ContextLight.of(use)) {
                ContextLight.GREEN -> it.green
                ContextLight.AMBER -> it.amber
                ContextLight.RED -> it.red
            }
        } ?: ProgressIndicatorDefaults.linearColor
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, color = colour, modifier = Modifier.fillMaxWidth().testTag("contextProgress"))
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

/** The context bar's traffic light, by use of what the prompt may fill, as "Nearly full" is measured (TASK-064). */
enum class ContextLight {
    GREEN,
    AMBER,
    RED,
    ;

    companion object {
        private const val AMBER_FROM = 0.65
        private const val RED_FROM = 0.85

        fun of(use: ContextUse): ContextLight {
            val share = if (use.limit > 0) use.used.toDouble() / use.limit else 1.0
            return when {
                share >= RED_FROM -> RED
                share >= AMBER_FROM -> AMBER
                else -> GREEN
            }
        }
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.decide(tool.callId, true) }, enabled = enabled, modifier = Modifier.testTag("approve:${tool.callId}")) {
                    Text(stringResource(R.string.chat_confirm_allow))
                }
                if (confirmation.newSites.isNotEmpty()) {
                    OutlinedButton(onClick = { actions.alwaysAllowSites(tool.callId) }, enabled = enabled, modifier = Modifier.testTag("alwaysAllow:${tool.callId}")) {
                        Text(stringResource(R.string.chat_confirm_always_site, confirmation.newSites.joinToString()))
                    }
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

@Composable
private fun UserMessage(entry: ChatEntry, actions: ChatActions) {
    val text = entry.text
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entry.images.forEach { Thumbnail(it, actions, size = 160.dp) }
        entry.documents.forEach { DocumentChip(it, onRemove = null) }
        if (text.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Thumbnail(name: String, actions: ChatActions, size: androidx.compose.ui.unit.Dp) {
    val image by produceState<ImageBitmap?>(null, name) { value = actions.thumbnail(name) }
    val shape = RoundedCornerShape(12.dp)
    val loaded = image
    if (loaded == null) {
        Box(modifier = Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).testTag("image:$name"))
    } else {
        Image(
            loaded,
            contentDescription = stringResource(R.string.chat_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape).testTag("image:$name"),
        )
    }
}

/** A document by name; its text is sent, never shown, as it may be long. */
@Composable
private fun DocumentChip(document: ChatDocument, onRemove: (() -> Unit)?) {
    InputChip(
        selected = false,
        onClick = { onRemove?.invoke() },
        enabled = true,
        label = { Text(document.name + if (document.truncated) " " + stringResource(R.string.chat_document_cut) else "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_attach), contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = onRemove?.let { { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.chat_document_remove), modifier = Modifier.size(18.dp)) } },
        modifier = Modifier.widthIn(max = 280.dp).testTag("document:${document.name}"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attachments(state: ChatState, actions: ChatActions) {
    FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("attachments"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.documents.forEachIndexed { index, document -> DocumentChip(document) { actions.removeDocument(index) } }
        state.attachments.forEach { name ->
            Box {
                Thumbnail(name, actions, size = 64.dp)
                IconButton(
                    onClick = { actions.removeAttachment(name) },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp).testTag("removeAttachment:$name"),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.chat_image_remove),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape).padding(2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Working(sidekick: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("working"),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.chat_working, sidekick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Reply(index: Int, entry: ChatEntry, sidekick: String, markdown: MarkdownActions, readAloud: SpeechState?, actions: ChatActions) {
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
            MarkdownText(reply.answer, markdown, modifier = Modifier.testTag("answer:$index"))
            if (readAloud != null) ReadAloudButton(index, readAloud, actions)
        }
    }
}

@Composable
private fun ReadAloudButton(index: Int, speech: SpeechState, actions: ChatActions) {
    val reading = speech.reading == index
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (reading) actions.stopReading() else actions.readAloud(index) }, modifier = Modifier.testTag("readAloud:$index")) {
            Icon(
                painterResource(if (reading) R.drawable.ic_stop else R.drawable.ic_volume_up),
                contentDescription = stringResource(if (reading) R.string.chat_read_stop else R.string.chat_read_aloud),
                tint = if (reading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (reading && speech.needsNetwork) {
            Text(
                stringResource(R.string.chat_read_network),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("readNetwork"),
            )
        }
    }
}

/** Images for models that can see (TASK-082), documents for any (TASK-084). */
@Composable
private fun AttachMenu(state: ChatState, actions: ChatActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("attach")) {
            Icon(painterResource(R.drawable.ic_attach), contentDescription = stringResource(R.string.chat_attach), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.canAttach) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_image_attach)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_image), contentDescription = null) },
                    enabled = state.attachments.size < MAX_ATTACHMENTS,
                    onClick = { open = false; actions.attachImage() },
                    modifier = Modifier.testTag("attachImage"),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_document_attach)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_attach), contentDescription = null) },
                enabled = state.documents.size < MAX_ATTACHMENTS,
                onClick = { open = false; actions.attachDocument() },
                modifier = Modifier.testTag("attachDocument"),
            )
        }
    }
}

/** A tap speaks one message; a long press starts a voice conversation; either stops what is running. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicButton(state: ChatState, actions: ChatActions) {
    val active = state.voice == VoiceState.LISTENING || state.conversation != null
    val stop = { if (state.conversation != null) actions.endConversation() else actions.stopVoice() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(if (active) R.string.chat_voice_stop else R.string.chat_voice),
                onLongClickLabel = stringResource(R.string.chat_conversation_start),
                onClick = { if (active) stop() else actions.startVoice() },
                onLongClick = { if (active) stop() else actions.startConversation() },
            )
            .testTag("voice"),
    ) {
        Icon(
            painterResource(R.drawable.ic_mic),
            contentDescription = stringResource(if (active) R.string.chat_voice_stop else R.string.chat_voice),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationBar(phase: ConversationPhase, sidekick: String, actions: ChatActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("conversation"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (phase) {
                ConversationPhase.LISTENING -> stringResource(R.string.chat_conversation_listening)
                ConversationPhase.WAITING -> stringResource(R.string.chat_conversation_waiting, sidekick)
                ConversationPhase.SPEAKING -> stringResource(R.string.chat_conversation_speaking, sidekick)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = actions::endConversation, modifier = Modifier.testTag("endConversation")) { Text(stringResource(R.string.chat_conversation_end)) }
    }
}

@Composable
private fun Composer(state: ChatState, actions: ChatActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.modelName != null) AttachMenu(state, actions)
        OutlinedTextField(
            value = state.input,
            onValueChange = actions::setInput,
            placeholder = {
                Text(if (state.voice == VoiceState.LISTENING) stringResource(R.string.chat_listening) else stringResource(R.string.chat_input_hint, state.sidekick))
            },
            trailingIcon = if (state.voice == VoiceState.UNAVAILABLE) null else {
                { MicButton(state, actions) }
            },
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
                enabled = (state.input.isNotBlank() || state.attachments.isNotEmpty() || state.documents.isNotEmpty()) && state.modelName != null,
                modifier = Modifier.testTag("send"),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

/** Each image takes hundreds of context tokens and seconds to encode on a phone. */
const val MAX_ATTACHMENTS = 4

internal object ChatText {
    @StringRes
    fun error(error: ChatError?): Int? = when (error) {
        null -> null
        ChatError.NO_MODEL_LOADED -> R.string.chat_error_no_model
        ChatError.CONVERSATION_TOO_LONG -> R.string.chat_error_too_long
        ChatError.GENERATION_FAILED -> R.string.chat_error_failed
        ChatError.TOO_MANY_TOOL_CALLS -> R.string.chat_error_too_many_tools
        ChatError.TIMED_OUT -> R.string.chat_error_timed_out
        ChatError.SERVER_UNREACHABLE -> R.string.chat_error_server
    }
}

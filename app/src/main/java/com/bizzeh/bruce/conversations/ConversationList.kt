package com.bizzeh.bruce.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R

interface ConversationActions {
    fun open(id: Long)
    fun rename(id: Long, title: String)
    fun archive(ids: Set<Long>)
    fun restore(ids: Set<Long>)
    fun delete(ids: Set<Long>)
}

/**
 * Saved chats. A tap opens one (in the Archived view it selects instead); a long press starts
 * selecting, for renaming one or archiving, restoring or deleting several.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationList(
    conversations: List<Conversation>,
    currentId: Long?,
    archivedView: Boolean,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Drop selections of conversations that have gone (deleted, archived elsewhere).
    val live = selected.filter { id -> conversations.any { it.id == id } }
    val selecting = live.isNotEmpty()
    fun toggle(id: Long) {
        selected = if (id in live) live - id else live + id
    }

    Column(modifier = modifier) {
        if (live.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.conversations_selected, live.size, live.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 28.dp, top = 8.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("selectionBar"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (live.size == 1) TextButton(onClick = { renaming = live.single() }, modifier = Modifier.testTag("rename")) { Text(stringResource(R.string.conversations_rename)) }
                if (archivedView) {
                    TextButton(onClick = { actions.restore(live.toSet()); selected = emptyList() }, modifier = Modifier.testTag("restore")) {
                        Text(stringResource(R.string.conversations_restore))
                    }
                } else {
                    TextButton(onClick = { actions.archive(live.toSet()); selected = emptyList() }, modifier = Modifier.testTag("archive")) {
                        Text(stringResource(R.string.conversations_archive))
                    }
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete")) {
                    Text(stringResource(R.string.conversations_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { selected = emptyList() }, modifier = Modifier.testTag("clearSelection")) { Text(stringResource(R.string.settings_cancel)) }
            }
        }
        if (conversations.isEmpty()) {
            Text(
                stringResource(if (archivedView) R.string.conversations_archived_empty else R.string.conversations_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
        conversations.forEach { conversation ->
            val isSelected = conversation.id in live
            ListItem(
                headlineContent = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = if (selecting) {
                    { Checkbox(checked = isSelected, onCheckedChange = null) }
                } else {
                    null
                },
                colors = if (conversation.id == currentId && !selecting) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ListItemDefaults.colors()
                },
                modifier = Modifier
                    .combinedClickable(
                        onClick = { if (selecting || archivedView) toggle(conversation.id) else actions.open(conversation.id) },
                        onLongClick = { toggle(conversation.id) },
                    )
                    .padding(horizontal = 12.dp)
                    .testTag("conversation:${conversation.id}"),
            )
        }
    }

    renaming?.let { id ->
        var title by rememberSaveable { mutableStateOf(conversations.firstOrNull { it.id == id }?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.conversations_rename)) },
            text = {
                OutlinedTextField(title, { title = it.take(ConversationStore.MAX_TITLE_LENGTH) }, singleLine = true, modifier = Modifier.testTag("renameField"))
            },
            confirmButton = {
                TextButton(
                    onClick = { actions.rename(id, title); renaming = null; selected = emptyList() },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag("confirmRename"),
                ) { Text(stringResource(R.string.conversations_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.conversations_delete_confirm, live.size, live.size)) },
            text = { Text(stringResource(R.string.conversations_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { actions.delete(live.toSet()); confirmDelete = false; selected = emptyList() }, modifier = Modifier.testTag("confirmDelete")) {
                    Text(stringResource(R.string.conversations_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

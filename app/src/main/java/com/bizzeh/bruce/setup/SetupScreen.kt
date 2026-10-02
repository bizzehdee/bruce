package com.bizzeh.bruce.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.models.DownloadState
import com.bizzeh.bruce.models.ModelBrowserViewModel
import com.bizzeh.bruce.models.ModelsState
import com.bizzeh.bruce.models.SuggestedModel
import com.bizzeh.bruce.settings.InferenceDefaults
import com.bizzeh.bruce.skills.CapabilityClass
import com.bizzeh.bruce.skills.SkillText
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.settings.SettingsText
import com.bizzeh.bruce.ui.Format

interface SetupActions {
    fun next()
    fun back()
    fun setNetworkMode(mode: NetworkMode)
    fun allowNotifications()
    fun importModel()
    fun download(model: SuggestedModel)
    fun cancelDownload(model: SuggestedModel)
    /** Null is Auto. */
    fun setContextLength(contextLength: Int?)
    fun setSkillGroup(kind: CapabilityClass, on: Boolean)
    fun finish(exit: SetupExit)
}

@Composable
fun SetupScreen(
    state: SetupState,
    models: ModelsState,
    actions: SetupActions,
    downloads: Map<String, DownloadState> = emptyMap(),
    contextChoice: ContextChoice? = null,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).testTag("setup")) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.step) {
                    SetupStep.WELCOME -> Page(R.string.setup_welcome_title, R.string.setup_welcome_text)
                    SetupStep.NETWORK -> {
                        Page(R.string.setup_network_title, R.string.setup_network_text)
                        NetworkMode.entries.forEach { mode ->
                            ListItem(
                                headlineContent = { Text(stringResource(SettingsText.networkLabel(mode))) },
                                supportingContent = { Text(stringResource(SettingsText.networkSummary(mode))) },
                                leadingContent = { RadioButton(selected = mode == state.network, onClick = null) },
                                modifier = Modifier
                                    .selectable(selected = mode == state.network, role = Role.RadioButton) { actions.setNetworkMode(mode) }
                                    .testTag("setupNetwork:$mode"),
                            )
                        }
                    }
                    SetupStep.NOTIFICATIONS -> {
                        Page(R.string.setup_notifications_title, R.string.setup_notifications_text)
                        Button(onClick = actions::allowNotifications, modifier = Modifier.testTag("setupAllowNotifications")) {
                            Text(stringResource(R.string.setup_notifications_allow))
                        }
                    }
                    SetupStep.MODEL -> ModelStep(state, models, downloads, actions)
                    SetupStep.CONTEXT -> ContextStep(state, contextChoice, actions)
                    SetupStep.SKILLS -> SkillsStep(state, state.contextLength ?: contextChoice?.auto ?: InferenceDefaults.FALLBACK_CONTEXT, actions)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.isFirst) Spacer(Modifier) else TextButton(onClick = actions::back, modifier = Modifier.testTag("setupBack")) {
                    Text(stringResource(R.string.setup_back))
                }
                when {
                    !state.isLast -> Button(onClick = actions::next, modifier = Modifier.testTag("setupNext")) {
                        Text(stringResource(if (state.step == SetupStep.NOTIFICATIONS) R.string.setup_not_now else R.string.setup_next))
                    }
                    else -> Button(onClick = { actions.finish(SetupExit.CHAT) }, modifier = Modifier.testTag("setupFinish")) {
                        Text(stringResource(if (models.models.isEmpty()) R.string.setup_skip else R.string.setup_finish))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStep(state: SetupState, models: ModelsState, downloads: Map<String, DownloadState>, actions: SetupActions) {
    Page(R.string.setup_model_title, R.string.setup_model_text)
    val online = state.network.allowsHuggingFace
    if (state.suggestions.isNotEmpty()) {
        Text(stringResource(R.string.setup_suggested_title), style = MaterialTheme.typography.titleMedium)
        val installed = models.models.map { it.file.name }.toSet()
        state.suggestions.forEach { model ->
            Suggestion(model, model.path in installed, downloads[ModelBrowserViewModel.key(model.repositoryId, model.path)], online, actions)
        }
    }
    OutlinedButton(onClick = actions::importModel, enabled = !models.importing, modifier = Modifier.testTag("setupImport")) {
        Text(stringResource(if (models.importing) R.string.setup_importing else R.string.setup_import))
    }
    models.importError?.let {
        Text(stringResource(R.string.setup_import_failed, it.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("setupImportError"))
    }
    OutlinedButton(onClick = { actions.finish(SetupExit.BROWSE_MODELS) }, enabled = online, modifier = Modifier.testTag("setupBrowse")) {
        Text(stringResource(if (state.suggestions.isEmpty()) R.string.setup_browse else R.string.setup_browse_other))
    }
    if (!online) Text(stringResource(R.string.setup_browse_offline), style = MaterialTheme.typography.bodySmall)
    models.models.forEach { Text(stringResource(R.string.setup_installed, it.file.nameWithoutExtension), modifier = Modifier.testTag("setupInstalled")) }
}

@Composable
private fun ContextStep(state: SetupState, choice: ContextChoice?, actions: SetupActions) {
    Page(R.string.setup_context_title, R.string.setup_context_text)
    if (choice == null) return
    Text(stringResource(R.string.setup_context_model, choice.modelName), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.contextLength == null,
            onClick = { actions.setContextLength(null) },
            label = { Text(stringResource(R.string.settings_context_auto_size, SettingsText.contextLabel(choice.auto))) },
            modifier = Modifier.testTag("setupContext:auto"),
        )
        choice.options.forEach { option ->
            FilterChip(
                selected = state.contextLength == option.contextLength,
                onClick = { actions.setContextLength(option.contextLength) },
                label = { Text(stringResource(R.string.setup_context_option, SettingsText.contextLabel(option.contextLength), Format.bytes(option.totalBytes))) },
                modifier = Modifier.testTag("setupContext:${option.contextLength}"),
            )
        }
    }
    Text(stringResource(R.string.setup_context_auto_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SkillsStep(state: SetupState, contextLength: Int, actions: SetupActions) {
    Page(R.string.setup_skills_title, R.string.setup_skills_text)
    state.skillGroups.forEach { group ->
        val names = group.skills.map { SkillText.name(it) }
        ListItem(
            headlineContent = { Text(stringResource(SetupText.kind(group.kind))) },
            supportingContent = { Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Switch(group.on, { actions.setSkillGroup(group.kind, it) }, modifier = Modifier.testTag("setupSkills:${group.kind}")) },
        )
    }
    if (SetupSkills.crowded(state.promptTokens, contextLength)) {
        Text(
            stringResource(R.string.setup_skills_crowded, state.promptTokens, contextLength),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("setupSkillsCrowded"),
        )
    }
}

@Composable
private fun Suggestion(model: SuggestedModel, installed: Boolean, download: DownloadState?, online: Boolean, actions: SetupActions) {
    Column(modifier = Modifier.fillMaxWidth().testTag("suggested:${model.path}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${model.name} · ${Format.bytes(model.sizeBytes)}", style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(model.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        when {
            installed -> Text(stringResource(R.string.setup_suggested_installed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("suggestedInstalled:${model.path}"))
            download == null -> OutlinedButton(onClick = { actions.download(model) }, enabled = online, modifier = Modifier.testTag("suggestedDownload:${model.path}")) {
                Text(stringResource(R.string.setup_suggested_download))
            }
            download.error == DownloadError.TOO_LARGE -> Text(
                stringResource(R.string.download_too_large, Format.bytes(download.neededBytes ?: 0), Format.bytes(download.freeBytes ?: 0)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            download.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.download_error, download.error.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { actions.download(model) }, enabled = online, modifier = Modifier.testTag("suggestedRetry:${model.path}")) {
                    Text(stringResource(R.string.browse_retry))
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.weight(1f).testTag("suggestedProgress:${model.path}"))
                OutlinedButton(onClick = { actions.cancelDownload(model) }, modifier = Modifier.testTag("suggestedCancel:${model.path}")) {
                    Text(stringResource(R.string.browse_cancel))
                }
            }
        }
    }
}

@Composable
private fun Page(title: Int, text: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

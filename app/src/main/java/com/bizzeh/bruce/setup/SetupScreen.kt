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
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.models.ModelsState
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.settings.SettingsText

interface SetupActions {
    fun next()
    fun back()
    fun setNetworkMode(mode: NetworkMode)
    fun allowNotifications()
    fun importModel()
    fun finish(exit: SetupExit)
}

@Composable
fun SetupScreen(state: SetupState, models: ModelsState, actions: SetupActions) {
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
                    SetupStep.MODEL -> ModelStep(state, models, actions)
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
private fun ModelStep(state: SetupState, models: ModelsState, actions: SetupActions) {
    Page(R.string.setup_model_title, R.string.setup_model_text)
    OutlinedButton(onClick = actions::importModel, enabled = !models.importing, modifier = Modifier.testTag("setupImport")) {
        Text(stringResource(if (models.importing) R.string.setup_importing else R.string.setup_import))
    }
    models.importError?.let {
        Text(stringResource(R.string.setup_import_failed, it.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("setupImportError"))
    }
    val online = state.network.allowsHuggingFace
    OutlinedButton(onClick = { actions.finish(SetupExit.BROWSE_MODELS) }, enabled = online, modifier = Modifier.testTag("setupBrowse")) {
        Text(stringResource(R.string.setup_browse))
    }
    if (!online) Text(stringResource(R.string.setup_browse_offline), style = MaterialTheme.typography.bodySmall)
    models.models.forEach { Text(stringResource(R.string.setup_installed, it.file.nameWithoutExtension), modifier = Modifier.testTag("setupInstalled")) }
}

@Composable
private fun Page(title: Int, text: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

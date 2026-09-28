package com.bizzeh.bruce.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.huggingface.HubAccount
import com.bizzeh.bruce.huggingface.SignInError
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.navigation.SubScreen

data class SettingsState(
    val theme: ThemeSettings = ThemeSettings(),
    val dynamicColourSupported: Boolean = false,
    val inference: InferenceDefaults = InferenceDefaults(),
    val performanceCores: Int = 4,
    val cores: Int = 8,
    val network: NetworkMode = NetworkMode.OFFLINE,
    val personality: Personality = Personality.BRUCE,
    /** Backend choices this phone can use (BackendSelection.choices). */
    val backends: List<BackendPreference> = BackendPreference.entries,
    val account: HubAccount? = null,
    val signInError: SignInError? = null,
)

interface SettingsActions {
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColour(enabled: Boolean)
    fun setBackend(backend: BackendPreference)
    fun setThreads(threads: Int?)
    fun setContextLength(contextLength: Int)
    fun clearAllData()
    fun deleteAllConversations()
    fun setNetworkMode(mode: NetworkMode)
    fun setPersonality(personality: Personality)
    fun signIn()
    fun signOut()
    fun openPermissions()
    fun openLicences()
    fun openDiagnostics()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions, onBack: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteChats by rememberSaveable { mutableStateOf(false) }
    SubScreen(stringResource(R.string.nav_settings), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings")) {
            Heading(R.string.settings_sidekick)
            Personality.entries.forEach { personality ->
                ListItem(
                    headlineContent = { Text(personality.displayName) },
                    supportingContent = { Text(stringResource(SettingsText.personalitySummary(personality))) },
                    leadingContent = { RadioButton(selected = personality == state.personality, onClick = null) },
                    modifier = Modifier
                        .selectable(selected = personality == state.personality, role = Role.RadioButton) { actions.setPersonality(personality) }
                        .testTag("personality:$personality"),
                )
            }

            Heading(R.string.settings_appearance)
            Choice(
                options = ThemeMode.entries,
                selected = state.theme.mode,
                label = { stringResource(themeLabel(it)) },
                onSelect = actions::setThemeMode,
            )
            if (state.dynamicColourSupported) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_colour)) },
                    trailingContent = {
                        Switch(state.theme.dynamicColour, actions::setDynamicColour, modifier = Modifier.testTag("dynamicColour"))
                    },
                )
            }

            Heading(R.string.settings_inference)
            Label(R.string.settings_backend)
            Choice(
                options = state.backends,
                selected = state.inference.backend,
                label = { if (it == BackendPreference.AUTO) stringResource(R.string.settings_backend_auto) else it.name.let(SettingsText::backendName) },
                onSelect = actions::setBackend,
            )
            if (state.backends.any { it.isGpu }) {
                Text(
                    stringResource(R.string.settings_backend_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Label(R.string.settings_threads)
            Chips {
                FilterChip(
                    selected = state.inference.threads == null,
                    onClick = { actions.setThreads(null) },
                    label = { Text(stringResource(R.string.settings_threads_auto, state.performanceCores)) },
                    modifier = Modifier.testTag("threads:auto"),
                )
                SettingsText.threadChoices(state.cores).forEach { threads ->
                    FilterChip(
                        selected = state.inference.threads == threads,
                        onClick = { actions.setThreads(threads) },
                        label = { Text(threads.toString()) },
                        modifier = Modifier.testTag("threads:$threads"),
                    )
                }
            }
            Label(R.string.settings_context)
            Chips {
                InferenceDefaults.CONTEXT_CHOICES.forEach { length ->
                    FilterChip(
                        selected = state.inference.contextLength == length,
                        onClick = { actions.setContextLength(length) },
                        label = { Text(SettingsText.contextLabel(length)) },
                        modifier = Modifier.testTag("context:$length"),
                    )
                }
            }
            Text(
                stringResource(R.string.settings_defaults_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Heading(R.string.settings_network)
            NetworkMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(stringResource(SettingsText.networkLabel(mode))) },
                    supportingContent = { Text(stringResource(SettingsText.networkSummary(mode))) },
                    leadingContent = { RadioButton(selected = mode == state.network, onClick = null) },
                    modifier = Modifier
                        .selectable(selected = mode == state.network, role = Role.RadioButton) { actions.setNetworkMode(mode) }
                        .testTag("network:$mode"),
                )
            }
            val account = state.account
            if (account != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.account_signed_in, account.username)) },
                    supportingContent = { Text(stringResource(R.string.account_signed_in_until, SettingsText.date(account.expiresAtMillis))) },
                    trailingContent = { TextButton(onClick = actions::signOut, modifier = Modifier.testTag("signOut")) { Text(stringResource(R.string.account_sign_out)) } },
                )
            } else {
                val allowed = state.network.allowsHuggingFace
                ListItem(
                    headlineContent = { Text(stringResource(R.string.account_sign_in)) },
                    supportingContent = {
                        Text(stringResource(if (allowed) R.string.account_sign_in_summary else R.string.account_sign_in_offline))
                    },
                    modifier = Modifier.clickable(enabled = allowed, onClick = actions::signIn).testTag("signIn"),
                )
            }
            state.signInError?.let {
                Text(
                    stringResource(R.string.account_error, it.name),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("signInError"),
                )
            }

            Heading(R.string.settings_privacy)
            Link(R.string.settings_permissions, R.string.settings_permissions_summary, "settings:permissions", actions::openPermissions)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_delete_conversations), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.settings_delete_conversations_summary)) },
                modifier = Modifier.clickable { confirmDeleteChats = true }.testTag("settings:deleteChats"),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_data), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.settings_clear_data_summary)) },
                modifier = Modifier.clickable { confirmClear = true }.testTag("settings:clear"),
            )

            Heading(R.string.settings_about)
            Link(R.string.settings_licences, null, "settings:licences", actions::openLicences)
            Link(R.string.nav_diagnostics, R.string.nav_diagnostics_summary, "settings:diagnostics", actions::openDiagnostics)
        }
    }
    if (confirmDeleteChats) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChats = false },
            title = { Text(stringResource(R.string.settings_delete_conversations_confirm_title)) },
            text = { Text(stringResource(R.string.conversations_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteChats = false; actions.deleteAllConversations() }, modifier = Modifier.testTag("confirmDeleteChats")) {
                    Text(stringResource(R.string.conversations_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteChats = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_data_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; actions.clearAllData() }, modifier = Modifier.testTag("confirmClear")) {
                    Text(stringResource(R.string.settings_clear_data_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun Heading(text: Int) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Link(title: Int, summary: Int?, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

internal object SettingsText {
    fun backendName(name: String) = when (name) {
        "VULKAN" -> "Vulkan"
        "OPENCL" -> "OpenCL"
        else -> name
    }

    /** Powers of two up to the core count, plus the core count itself. */
    fun threadChoices(cores: Int): List<Int> =
        (generateSequence(1) { it * 2 }.takeWhile { it <= minOf(cores, InferenceDefaults.MAX_THREADS) } +
            minOf(cores, InferenceDefaults.MAX_THREADS)).distinct().toList()

    fun contextLabel(length: Int) = "${length / 1024}K"

    fun networkLabel(mode: NetworkMode) = when (mode) {
        NetworkMode.OFFLINE -> R.string.network_offline
        NetworkMode.HUGGING_FACE -> R.string.network_huggingface
        NetworkMode.APPROVED_DOMAINS -> R.string.network_approved
        NetworkMode.GENERAL -> R.string.network_general
    }

    fun personalitySummary(personality: Personality) = when (personality) {
        Personality.BRUCE -> R.string.personality_bruce_summary
        Personality.MILO -> R.string.personality_milo_summary
    }

    fun networkSummary(mode: NetworkMode) = when (mode) {
        NetworkMode.OFFLINE -> R.string.network_offline_summary
        NetworkMode.HUGGING_FACE -> R.string.network_huggingface_summary
        NetworkMode.APPROVED_DOMAINS -> R.string.network_approved_summary
        NetworkMode.GENERAL -> R.string.network_general_summary
    }

    fun date(millis: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale).format(java.util.Date(millis))
}

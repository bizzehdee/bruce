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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.navigation.SubScreen

data class SettingsState(
    val theme: ThemeSettings = ThemeSettings(),
    val dynamicColourSupported: Boolean = false,
    val inference: InferenceDefaults = InferenceDefaults(),
    val performanceCores: Int = 4,
    val cores: Int = 8,
)

interface SettingsActions {
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColour(enabled: Boolean)
    fun setBackend(backend: BackendPreference)
    fun setThreads(threads: Int?)
    fun setContextLength(contextLength: Int)
    fun clearAllData()
    fun openPermissions()
    fun openLicences()
    fun openDiagnostics()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions, onBack: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SubScreen(stringResource(R.string.nav_settings), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings")) {
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
                options = BackendPreference.entries,
                selected = state.inference.backend,
                label = { if (it == BackendPreference.AUTO) stringResource(R.string.settings_backend_auto) else it.name.let(SettingsText::backendName) },
                onSelect = actions::setBackend,
            )
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

            Heading(R.string.settings_privacy)
            Link(R.string.settings_permissions, R.string.settings_permissions_summary, "settings:permissions", actions::openPermissions)
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
}

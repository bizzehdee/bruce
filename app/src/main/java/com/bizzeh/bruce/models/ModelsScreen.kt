package com.bizzeh.bruce.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.navigation.SubScreen
import com.bizzeh.bruce.settings.InferenceDefaults
import com.bizzeh.bruce.settings.SettingsText
import com.bizzeh.bruce.ui.Format
import java.io.File
import java.util.Locale

interface ModelsActions {
    fun choose(file: File)
    fun importModel()
    fun delete(file: File)
    fun setOverrides(file: File, overrides: ModelOverrides)
}

@Composable
fun ModelsScreen(
    state: ModelsState,
    actions: ModelsActions,
    onBack: () -> Unit,
    cores: Int = 8,
    browse: BrowseState = BrowseState(),
    browseActions: BrowseActions? = null,
    startOnHuggingFace: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(if (startOnHuggingFace && browseActions != null) 1 else 0) }
    SubScreen(stringResource(R.string.nav_models), onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (browseActions != null) {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.models_tab_installed)) }, modifier = Modifier.testTag("tab:installed"))
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.models_tab_huggingface)) }, modifier = Modifier.testTag("tab:huggingface"))
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 1 && browseActions != null) {
                    BrowsePane(browse, browseActions)
                } else {
                    Installed(state, actions, cores, expanded, { expanded = it }, { confirmDelete = it })
                }
            }
        }
    }
    val deleting = state.models.firstOrNull { it.file.name == confirmDelete }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.models_delete_confirm_title, deleting.file.nameWithoutExtension)) },
            text = { Text(stringResource(R.string.models_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; actions.delete(deleting.file) }, modifier = Modifier.testTag("confirmDelete")) {
                    Text(stringResource(R.string.models_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun Installed(
    state: ModelsState,
    actions: ModelsActions,
    cores: Int,
    expanded: String?,
    onExpand: (String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            state.importError?.let { Text("Import failed: $it", color = MaterialTheme.colorScheme.error) }
        }
        OutlinedButton(onClick = actions::importModel, enabled = !state.importing, modifier = Modifier.testTag("import")) {
            Text(stringResource(if (state.importing) R.string.models_loading else R.string.models_import))
        }
    }
    if (state.models.isEmpty()) {
        Text(stringResource(R.string.models_empty), modifier = Modifier.padding(vertical = 32.dp).testTag("modelsEmpty"))
    }
    state.models.forEach { model ->
        ModelCard(
            model = model,
            active = model.file == state.active,
            loading = model.file == state.loading,
            expanded = expanded == model.file.name,
            cores = cores,
            onToggle = { onExpand(if (expanded == model.file.name) null else model.file.name) },
            actions = actions,
            onDelete = { onDelete(model.file.name) },
        )
    }
    state.loadError?.let { Text("Could not load the model: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("loadError")) }
}

@Composable
private fun ModelCard(
    model: InstalledModel,
    active: Boolean,
    loading: Boolean,
    expanded: Boolean,
    cores: Int,
    onToggle: () -> Unit,
    actions: ModelsActions,
    onDelete: () -> Unit,
) {
    val assessment = model.assessment
    Card(
        colors = if (active) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth().testTag("model:${model.file.name}"),
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggle).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.file.nameWithoutExtension, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (active) Text(stringResource(R.string.models_active), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(ModelsText.summary(model), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onToggle, label = { Text(stringResource(ModelsText.fitLabel(assessment))) }, modifier = Modifier.testTag("fit:${model.file.name}"))
                if (assessment.supported) {
                    AssistChip(onClick = onToggle, label = { Text(stringResource(ModelsText.speedLabel(assessment.speed)) + " · " + ModelsText.speed(assessment)) })
                }
            }
            if (expanded) {
                Text(stringResource(R.string.models_details), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ModelsText.details(model).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.models_settings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                OverrideChips(
                    label = stringResource(R.string.settings_backend),
                    options = listOf(null) + BackendPreference.entries,
                    selected = model.overrides.backend,
                    text = { it?.name?.let(SettingsText::backendName) },
                    tag = "backend",
                ) { actions.setOverrides(model.file, model.overrides.copy(backend = it)) }
                Text(stringResource(R.string.settings_backend_note), style = MaterialTheme.typography.bodySmall)
                OverrideChips(
                    label = stringResource(R.string.settings_threads),
                    options = listOf(null) + SettingsText.threadChoices(cores),
                    selected = model.overrides.threads,
                    text = { it?.toString() },
                    tag = "threads",
                ) { actions.setOverrides(model.file, model.overrides.copy(threads = it)) }
                OverrideChips(
                    label = stringResource(R.string.settings_context),
                    options = listOf(null) + InferenceDefaults.CONTEXT_CHOICES,
                    selected = model.overrides.contextLength,
                    text = { it?.let(SettingsText::contextLabel) },
                    tag = "context",
                ) { actions.setOverrides(model.file, model.overrides.copy(contextLength = it)) }
                OverrideChips(
                    label = stringResource(R.string.models_temperature),
                    options = listOf(null) + ModelOverrides.TEMPERATURE_CHOICES,
                    selected = model.overrides.temperature,
                    text = { it?.let { value -> String.format(Locale.ROOT, "%.1f", value) } },
                    tag = "temperature",
                ) { actions.setOverrides(model.file, model.overrides.copy(temperature = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { actions.choose(model.file) }, enabled = !active && !loading && assessment.supported, modifier = Modifier.testTag("use:${model.file.name}")) {
                        Text(stringResource(if (loading) R.string.models_loading else R.string.models_use))
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("delete:${model.file.name}")) {
                        Text(stringResource(R.string.models_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OverrideChips(label: String, options: List<T?>, selected: T?, text: (T?) -> String?, tag: String, onSelect: (T?) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(text(option) ?: stringResource(R.string.models_default)) },
                modifier = Modifier.testTag("$tag:${option ?: "default"}"),
            )
        }
    }
}

internal object ModelsText {
    fun summary(model: InstalledModel): String = listOfNotNull(
        Format.bytes(model.sizeBytes),
        model.assessment.quantisation,
        model.metadata?.parameterCount?.let { Format.count(it) + " parameters" },
    ).joinToString(" · ")

    fun fitLabel(assessment: Assessment): Int = when {
        !assessment.supported -> R.string.models_unsupported
        assessment.fit == Fit.FITS -> R.string.models_fit_fits
        assessment.fit == Fit.TIGHT -> R.string.models_fit_tight
        else -> R.string.models_fit_no
    }

    fun speedLabel(speed: SpeedBand): Int = when (speed) {
        SpeedBand.FAST -> R.string.models_speed_fast
        SpeedBand.USABLE -> R.string.models_speed_usable
        SpeedBand.SLOW -> R.string.models_speed_slow
    }

    /** Above 100 tokens per second the estimate is far outside what was measured, so it is not shown exactly. */
    fun speed(assessment: Assessment): String =
        if (assessment.expectedTokensPerSecond >= 100) "100+ tok/s" else String.format(Locale.ROOT, "~%.0f tok/s", assessment.expectedTokensPerSecond)

    fun details(model: InstalledModel): List<String> {
        val metadata = model.metadata ?: return listOf("Not a readable GGUF file.")
        val estimate = model.assessment.estimate
        return listOf(
            "Architecture: ${metadata.architecture ?: "unknown"}",
            "Trained context: ${metadata.contextLength ?: "unknown"} tokens",
            "Memory: ${Format.bytes(estimate.totalBytes)} at ${estimate.contextLength} tokens",
        )
    }
}

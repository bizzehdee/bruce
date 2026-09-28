package com.bizzeh.bruce.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.settings.ThemeMode
import com.bizzeh.bruce.settings.ThemeSettings
import java.io.File

/** Callbacks from the prototype screen. */
interface PrototypeActions {
    fun importModel()
    fun select(file: File)
    fun setBackend(backend: BackendPreference)
    fun load()
    fun setPrompt(prompt: String)
    fun generate()
    fun stop()
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColour(enabled: Boolean)
}

/** Phase 0 test bench, not the product UI. Text is deliberately not localised. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeScreen(
    state: PrototypeState,
    actions: PrototypeActions,
    theme: ThemeSettings = ThemeSettings(),
    dynamicColourSupported: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_diagnostics)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.nav_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Appearance") {
                ThemeChooser(theme.mode, actions::setThemeMode)
                if (dynamicColourSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = theme.dynamicColour,
                            onCheckedChange = actions::setDynamicColour,
                            modifier = Modifier.testTag("dynamicColour"),
                        )
                        Text("Use wallpaper colours", modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            Section("Hardware") {
                Text(PrototypeText.cpu(state.cpuFeatures))
                state.capabilities?.devices?.forEach { Text(PrototypeText.device(it)) }
                state.capabilities?.let { Text("CPU variant: " + it.cpuBackendFeatures.joinToString(" ")) }
            }
            Section("Models") {
                state.models.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { actions.select(model) }.testTag("model:${model.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == state.selected, onClick = { actions.select(model) })
                        Text(model.name)
                    }
                }
                if (state.models.isEmpty()) Text("No models yet.")
                OutlinedButton(onClick = actions::importModel, enabled = !state.importing) {
                    Text(if (state.importing) "Importing…" else "Import GGUF")
                }
                state.importError?.let { Text("Import failed: $it", color = MaterialTheme.colorScheme.error) }
            }
            state.selected?.let {
                Section("Model") {
                    PrototypeText.metadata(state.metadata, state.metadataError).forEach { line -> Text(line) }
                    state.memory?.let { memory ->
                        Text(PrototypeText.memory(memory))
                        if (!memory.fits) {
                            Text("Warning: this model may not fit in memory.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    BackendChooser(state.backend, actions::setBackend)
                    Button(onClick = actions::load, enabled = state.load != LoadState.Loading) { Text("Load") }
                    Text(PrototypeText.load(state.load), modifier = Modifier.testTag("loadStatus"))
                }
            }
            if (state.load is LoadState.Loaded) {
                Section("Prompt") {
                    OutlinedTextField(
                        value = state.prompt,
                        onValueChange = actions::setPrompt,
                        label = { Text("Prompt") },
                        modifier = Modifier.fillMaxWidth().testTag("prompt"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions::generate, enabled = !state.generating) { Text("Generate") }
                        OutlinedButton(onClick = actions::stop, enabled = state.generating) { Text("Stop") }
                    }
                    Text(state.output, modifier = Modifier.testTag("output"))
                    PrototypeText.result(state)?.let { Text(it, modifier = Modifier.testTag("stats")) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChooser(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackendChooser(selected: BackendPreference, onSelect: (BackendPreference) -> Unit) {
    val options = BackendPreference.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(option.name) }
        }
    }
}

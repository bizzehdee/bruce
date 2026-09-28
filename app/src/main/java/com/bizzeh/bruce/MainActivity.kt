package com.bizzeh.bruce

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.prototype.PrototypeActions
import com.bizzeh.bruce.prototype.PrototypeScreen
import com.bizzeh.bruce.prototype.PrototypeViewModel
import com.bizzeh.bruce.settings.ThemeMode
import com.bizzeh.bruce.settings.ThemeSettings
import com.bizzeh.bruce.ui.theme.dynamicColourSupported
import com.bizzeh.bruce.ui.theme.BruceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: PrototypeViewModel by viewModels { factory() }
    private val themeSettings by lazy { appContainer.themeSettings }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by themeSettings.settings.collectAsState(initial = ThemeSettings())
            BruceTheme(theme) {
                val state by viewModel.state.collectAsState()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(viewModel::import)
                }
                val actions = remember { actions { picker.launch(arrayOf("*/*")) } }
                PrototypeScreen(state, actions, theme, dynamicColourSupported())
            }
        }
    }

    private fun actions(pickModel: () -> Unit) = object : PrototypeActions {
        override fun importModel() = pickModel()
        override fun select(file: File) = viewModel.select(file)
        override fun setBackend(backend: BackendPreference) = viewModel.setBackend(backend)
        override fun load() = viewModel.load()
        override fun setPrompt(prompt: String) = viewModel.setPrompt(prompt)
        override fun generate() = viewModel.generate()
        override fun stop() = viewModel.stop()
        override fun setThemeMode(mode: ThemeMode) {
            lifecycleScope.launch { themeSettings.setMode(mode) }
        }
        override fun setDynamicColour(enabled: Boolean) {
            lifecycleScope.launch { themeSettings.setDynamicColour(enabled) }
        }
    }

    private fun factory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = appContainer
            PrototypeViewModel(
                engine = container.engine,
                modelsDir = container.modelsDir,
                importModel = container.importer::import,
                detectCpuFeatures = container.cpuFeatures,
                memoryInfo = container::memoryInfo,
                ioDispatcher = Dispatchers.IO,
            )
        }
    }

    companion object {
        const val MODELS_DIR = AppContainer.MODELS_DIR
    }
}

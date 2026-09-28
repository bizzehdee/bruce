package com.bizzeh.bruce

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bizzeh.bruce.chat.ChatActions
import com.bizzeh.bruce.chat.ChatViewModel
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.navigation.AppActions
import com.bizzeh.bruce.navigation.BruceApp
import com.bizzeh.bruce.navigation.InterimModels
import com.bizzeh.bruce.navigation.InterimSettings
import com.bizzeh.bruce.prototype.PrototypeActions
import com.bizzeh.bruce.prototype.PrototypeScreen
import com.bizzeh.bruce.prototype.PrototypeViewModel
import com.bizzeh.bruce.settings.ThemeMode
import com.bizzeh.bruce.settings.ThemeSettings
import com.bizzeh.bruce.ui.theme.BruceTheme
import com.bizzeh.bruce.ui.theme.dynamicColourSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val container by lazy { appContainer }
    private val prototype: PrototypeViewModel by viewModels { factory { prototypeViewModel() } }
    private val chat: ChatViewModel by viewModels { factory { ChatViewModel(container.engine, container.activeModel.state) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by container.themeSettings.settings.collectAsState(initial = ThemeSettings())
            BruceTheme(theme) {
                val chatState by chat.state.collectAsState()
                val activeModel by container.activeModel.state.collectAsState()
                LaunchedEffect(Unit) { container.activeModel.refresh() }
                BruceApp(
                    chat = chatState,
                    chatActions = remember { chatActions() },
                    activeModel = activeModel,
                    actions = remember { appActions() },
                    modelsScreen = { onBack -> InterimModels(onBack) },
                    settingsScreen = { onBack, openDiagnostics -> InterimSettings(onBack, openDiagnostics) },
                    diagnosticsScreen = { onBack -> Diagnostics(theme, onBack) },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Diagnostics(theme: ThemeSettings, onBack: () -> Unit) {
        val state by prototype.state.collectAsState()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(prototype::import)
        }
        val actions = remember { prototypeActions { picker.launch(arrayOf("*/*")) } }
        PrototypeScreen(state, actions, theme, dynamicColourSupported(), onBack)
    }

    private fun chatActions() = object : ChatActions {
        override fun setInput(input: String) = chat.setInput(input)
        override fun send() = chat.send()
        override fun stop() = chat.stop()
    }

    private fun appActions() = object : AppActions {
        override fun newChat() = chat.newChat()
        override fun selectModel(file: File) {
            lifecycleScope.launch { container.activeModel.load(file) }
        }
    }

    private fun prototypeActions(pickModel: () -> Unit) = object : PrototypeActions {
        override fun importModel() = pickModel()
        override fun select(file: File) = prototype.select(file)
        override fun setBackend(backend: BackendPreference) = prototype.setBackend(backend)
        override fun load() = prototype.load()
        override fun setPrompt(prompt: String) = prototype.setPrompt(prompt)
        override fun generate() = prototype.generate()
        override fun stop() = prototype.stop()
        override fun setThemeMode(mode: ThemeMode) {
            lifecycleScope.launch { container.themeSettings.setMode(mode) }
        }
        override fun setDynamicColour(enabled: Boolean) {
            lifecycleScope.launch { container.themeSettings.setDynamicColour(enabled) }
        }
    }

    private fun prototypeViewModel() = PrototypeViewModel(
        engine = container.engine,
        activeModel = container.activeModel,
        modelsDir = container.modelsDir,
        importModel = container.importer::import,
        detectCpuFeatures = container.cpuFeatures,
        memoryInfo = container::memoryInfo,
        ioDispatcher = Dispatchers.IO,
    )

    private inline fun <reified T : androidx.lifecycle.ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =
        viewModelFactory { initializer { create() } }

    companion object {
        const val MODELS_DIR = AppContainer.MODELS_DIR
    }
}

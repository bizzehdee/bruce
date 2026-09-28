package com.bizzeh.bruce

import android.content.Intent
import android.net.Uri
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
import com.bizzeh.bruce.models.DeviceProfile
import com.bizzeh.bruce.models.ModelOverrides
import com.bizzeh.bruce.models.ModelsActions
import com.bizzeh.bruce.models.ModelsScreen
import com.bizzeh.bruce.models.ModelsViewModel
import com.bizzeh.bruce.navigation.Destination
import com.bizzeh.bruce.prototype.PrototypeActions
import com.bizzeh.bruce.prototype.PrototypeScreen
import com.bizzeh.bruce.prototype.PrototypeViewModel
import com.bizzeh.bruce.settings.ThemeMode
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.settings.SettingsActions
import com.bizzeh.bruce.settings.SettingsScreen
import com.bizzeh.bruce.settings.SettingsViewModel
import com.bizzeh.bruce.settings.ThemeSettings
import com.bizzeh.bruce.hardware.CpuTopology
import com.bizzeh.bruce.ui.theme.BruceTheme
import com.bizzeh.bruce.ui.theme.dynamicColourSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val container by lazy { appContainer }
    private val prototype: PrototypeViewModel by viewModels { factory { prototypeViewModel() } }
    private val chat: ChatViewModel by viewModels {
        factory { ChatViewModel(container.engine, container.activeModel.state, container.modelSelection::activeTemperature) }
    }
    private val models: ModelsViewModel by viewModels {
        factory {
            ModelsViewModel(
                activeModel = container.activeModel,
                selection = container.modelSelection,
                modelSettings = container.modelSettings,
                inferenceDefaults = container.inferenceSettings.defaults,
                importModel = container.importer::import,
                device = {
                    val memory = container.memoryInfo()
                    DeviceProfile((memory.availMem - memory.threshold).coerceAtLeast(0), container.cpuFeatures())
                },
                ioDispatcher = Dispatchers.IO,
            )
        }
    }
    private val settings: SettingsViewModel by viewModels {
        factory {
            SettingsViewModel(
                theme = container.themeSettings,
                inference = container.inferenceSettings,
                network = container.networkSettings,
                hubAuth = container.hubAuth,
                dataReset = container.dataReset,
                dynamicColourSupported = dynamicColourSupported(),
                performanceCores = CpuTopology.performanceCoreCount(),
                cores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleSignInRedirect(intent)
        enableEdgeToEdge()
        setContent {
            val theme by container.themeSettings.settings.collectAsState(initial = ThemeSettings())
            BruceTheme(theme) {
                val chatState by chat.state.collectAsState()
                val activeModel by container.activeModel.state.collectAsState()
                LaunchedEffect(Unit) { container.modelSelection.restore() }
                BruceApp(
                    chat = chatState,
                    chatActions = remember { chatActions() },
                    activeModel = activeModel,
                    actions = remember { appActions() },
                    modelsScreen = { onBack -> Models(onBack) },
                    settingsScreen = { onBack, open -> Settings(onBack, open) },
                    diagnosticsScreen = { onBack -> Diagnostics(onBack) },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Models(onBack: () -> Unit) {
        val state by models.state.collectAsState()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(models::import) }
        val actions = remember {
            object : ModelsActions {
                override fun choose(file: File) = models.choose(file)
                override fun importModel() = picker.launch(arrayOf("*/*"))
                override fun delete(file: File) = models.delete(file)
                override fun setOverrides(file: File, overrides: ModelOverrides) = models.setOverrides(file, overrides)
            }
        }
        LaunchedEffect(Unit) { models.refresh() }
        ModelsScreen(state, actions, onBack, cores = Runtime.getRuntime().availableProcessors())
    }

    @androidx.compose.runtime.Composable
    private fun Settings(onBack: () -> Unit, open: (Destination) -> Unit) {
        val state by settings.state.collectAsState()
        val actions = remember { settingsActions(open) }
        SettingsScreen(state, actions, onBack)
    }

    @androidx.compose.runtime.Composable
    private fun Diagnostics(onBack: () -> Unit) {
        val state by prototype.state.collectAsState()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(prototype::import)
        }
        val actions = remember { prototypeActions { picker.launch(arrayOf("*/*")) } }
        PrototypeScreen(state, actions, onBack)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSignInRedirect(intent)
    }

    /** The Hugging Face sign-in redirect; HubAuth rejects anything that does not match the sign-in it started. */
    private fun handleSignInRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "com.bizzeh.bruce" || uri.path != "/oauth/huggingface") return
        settings.completeSignIn(uri.queryParameterNames.associateWith(uri::getQueryParameter))
    }

    private fun chatActions() = object : ChatActions {
        override fun setInput(input: String) = chat.setInput(input)
        override fun send() = chat.send()
        override fun stop() = chat.stop()
    }

    private fun appActions() = object : AppActions {
        override fun newChat() = chat.newChat()
        override fun selectModel(file: File) {
            lifecycleScope.launch { container.modelSelection.choose(file) }
        }
    }

    private fun settingsActions(open: (Destination) -> Unit) = object : SettingsActions {
        override fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
        override fun setDynamicColour(enabled: Boolean) = settings.setDynamicColour(enabled)
        override fun setBackend(backend: BackendPreference) = settings.setBackend(backend)
        override fun setThreads(threads: Int?) = settings.setThreads(threads)
        override fun setContextLength(contextLength: Int) = settings.setContextLength(contextLength)
        override fun clearAllData() = settings.clearAllData()
        override fun setNetworkMode(mode: NetworkMode) = settings.setNetworkMode(mode)
        override fun signIn() {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.beginSignIn())))
        }
        override fun signOut() = settings.signOut()
        override fun openPermissions() = open(Destination.PERMISSIONS)
        override fun openLicences() = open(Destination.LICENCES)
        override fun openDiagnostics() = open(Destination.DIAGNOSTICS)
    }

    private fun prototypeActions(pickModel: () -> Unit) = object : PrototypeActions {
        override fun importModel() = pickModel()
        override fun select(file: File) = prototype.select(file)
        override fun setBackend(backend: BackendPreference) = prototype.setBackend(backend)
        override fun load() = prototype.load()
        override fun setPrompt(prompt: String) = prototype.setPrompt(prompt)
        override fun generate() = prototype.generate()
        override fun stop() = prototype.stop()
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

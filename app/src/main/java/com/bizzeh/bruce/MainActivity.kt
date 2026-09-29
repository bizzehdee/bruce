package com.bizzeh.bruce

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bizzeh.bruce.chat.ChatActions
import com.bizzeh.bruce.chat.ChatViewModel
import com.bizzeh.bruce.conversations.ConversationActions
import com.bizzeh.bruce.conversations.ConversationsViewModel
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.BackendSelection
import com.bizzeh.bruce.inference.EngineCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import com.bizzeh.bruce.navigation.AppActions
import com.bizzeh.bruce.navigation.BruceApp
import com.bizzeh.bruce.huggingface.HubModel
import com.bizzeh.bruce.memory.Fact
import com.bizzeh.bruce.memory.MemoryActions
import com.bizzeh.bruce.memory.MemoryMode
import com.bizzeh.bruce.memory.MemoryScreen
import com.bizzeh.bruce.memory.MemoryViewModel
import com.bizzeh.bruce.models.Assessment
import com.bizzeh.bruce.notifications.ReplyNotifications
import com.bizzeh.bruce.models.BrowseActions
import com.bizzeh.bruce.models.BrowseFilters
import com.bizzeh.bruce.models.DeviceProfile
import com.bizzeh.bruce.models.ModelBrowserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.bizzeh.bruce.models.ModelOverrides
import com.bizzeh.bruce.models.ModelsActions
import com.bizzeh.bruce.models.ModelsScreen
import com.bizzeh.bruce.models.ModelsViewModel
import com.bizzeh.bruce.models.TemplateFinder
import com.bizzeh.bruce.navigation.Destination
import com.bizzeh.bruce.prototype.PrototypeActions
import com.bizzeh.bruce.prototype.PrototypeScreen
import com.bizzeh.bruce.prototype.PrototypeViewModel
import com.bizzeh.bruce.settings.ThemeMode
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.settings.Personality
import com.bizzeh.bruce.settings.SettingsActions
import com.bizzeh.bruce.settings.SettingsScreen
import com.bizzeh.bruce.settings.GrantActions
import com.bizzeh.bruce.settings.GrantsViewModel
import com.bizzeh.bruce.settings.PermissionsScreen
import com.bizzeh.bruce.settings.SettingsViewModel
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.SkillsActions
import com.bizzeh.bruce.skills.SkillsScreen
import com.bizzeh.bruce.skills.SkillsViewModel
import com.bizzeh.bruce.settings.ThemeSettings
import com.bizzeh.bruce.setup.SetupActions
import com.bizzeh.bruce.setup.SetupExit
import com.bizzeh.bruce.setup.SetupScreen
import com.bizzeh.bruce.setup.SetupViewModel
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
        factory {
            ChatViewModel(
                container.engine, container.activeModel.state, container.conversations::save, container.conversations::load, container.runtime::respond, container.runtime::answer,
                container.personalitySettings.personality.map { it.displayName },
                container.runtime::measure,
                container.summarySettings.settings,
                container.runtime::summarise,
                container.chatMemory,
                container.turnObserver,
            )
        }
    }
    private val conversations: ConversationsViewModel by viewModels {
        factory { ConversationsViewModel(container.conversations) { ids -> chat.forget(ids) } }
    }
    private val models: ModelsViewModel by viewModels {
        factory {
            ModelsViewModel(
                activeModel = container.activeModel,
                selection = container.modelSelection,
                modelSettings = container.modelSettings,
                inferenceDefaults = container.inferenceSettings.defaults,
                importModel = container.importer::import,
                device = ::deviceProfile,
                ioDispatcher = Dispatchers.IO,
                templateSupportsTools = container.engine::templateSupportsTools,
                findTemplate = TemplateFinder(container.hubClient, container.engine::templateSupportsTools)::find,
            )
        }
    }
    private val browser: ModelBrowserViewModel by viewModels {
        factory {
            ModelBrowserViewModel(
                hub = container.hubClient,
                downloader = container.downloader,
                device = ::deviceProfile,
                contextLength = { container.inferenceSettings.defaults.first().contextLength },
                onDownloaded = { models.refresh() },
                templateSupportsTools = container.engine::templateSupportsTools,
            )
        }
    }
    private val settings: SettingsViewModel by viewModels {
        factory {
            SettingsViewModel(
                theme = container.themeSettings,
                inference = container.inferenceSettings,
                network = container.networkSettings,
                personality = container.personalitySettings,
                summary = container.summarySettings,
                memory = container.memorySettings,
                hubAuth = container.hubAuth,
                dataReset = container.dataReset,
                dynamicColourSupported = dynamicColourSupported(),
                performanceCores = CpuTopology.performanceCoreCount(),
                cores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }
    private val skills: SkillsViewModel by viewModels { factory { SkillsViewModel(container.skills, container.skillStates) } }
    private val grants: GrantsViewModel by viewModels { factory { GrantsViewModel(container.grants, Dispatchers.IO) } }
    private val memory: MemoryViewModel by viewModels { factory { MemoryViewModel(container.memory) } }
    private val setup: SetupViewModel by viewModels {
        factory { SetupViewModel(container.setupSettings, container.networkSettings, askNotifications = needsNotificationPermission()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleSignInRedirect(intent)
            handleOpenChat(intent)
        }
        container.stopTurn = { chat.stop() }
        lifecycleScope.launch(Dispatchers.IO) { capabilities.value = container.engine.getCapabilities() }
        enableEdgeToEdge()
        setContent {
            val theme by container.themeSettings.settings.collectAsState(initial = ThemeSettings())
            BruceTheme(theme) {
                // Null until read, so the wizard does not flash up for a returning user.
                val setupComplete by container.setupSettings.complete.collectAsState(initial = null)
                var exit by rememberSaveable { mutableStateOf(SetupExit.CHAT) }
                when (setupComplete) {
                    null -> Unit
                    false -> Setup { exit = it }
                    true -> Main(exit)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Main(exit: SetupExit) {
        val chatState by chat.state.collectAsState()
        val activeModel by container.activeModel.state.collectAsState()
        LaunchedEffect(Unit) { container.modelSelection.restore() }
        val browse = exit == SetupExit.BROWSE_MODELS
        val active by conversations.active.collectAsState()
        val archived by conversations.archived.collectAsState()
        BruceApp(
            chat = chatState,
            chatActions = remember { chatActions() },
            activeModel = activeModel,
            actions = remember { appActions() },
            modelsScreen = { onBack -> Models(onBack, startOnHuggingFace = browse) },
            settingsScreen = { onBack, open -> Settings(onBack, open) },
            diagnosticsScreen = { onBack -> Diagnostics(onBack) },
            skillsScreen = { onBack, openPermissions -> Skills(onBack, openPermissions) },
            permissionsScreen = { onBack, openNetwork -> Permissions(onBack, openNetwork) },
            memoryScreen = { onBack -> Memory(onBack) },
            startDestination = if (browse) Destination.MODELS else Destination.CHAT,
            conversations = active,
            archived = archived,
            conversationActions = remember { conversationActions() },
        )
    }

    @androidx.compose.runtime.Composable
    private fun Setup(onExit: (SetupExit) -> Unit) {
        val state by setup.state.collectAsState()
        val modelsState by models.state.collectAsState()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(models::import) }
        // Refused or granted, the wizard moves on; replies still complete without notifications.
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { setup.next() }
        val actions = remember {
            object : SetupActions {
                override fun next() = setup.next()
                override fun back() = setup.back()
                override fun setNetworkMode(mode: NetworkMode) = setup.setNetworkMode(mode)
                override fun allowNotifications() = permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                override fun importModel() = picker.launch(arrayOf("*/*"))
                override fun finish(exit: SetupExit) = setup.finish { onExit(exit) }
            }
        }
        SetupScreen(state, modelsState, actions)
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    @androidx.compose.runtime.Composable
    private fun Models(onBack: () -> Unit, startOnHuggingFace: Boolean = false) {
        val state by models.state.collectAsState()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(models::import) }
        val actions = remember {
            object : ModelsActions {
                override fun choose(file: File) = models.choose(file)
                override fun importModel() = picker.launch(arrayOf("*/*"))
                override fun delete(file: File) = models.delete(file)
                override fun setOverrides(file: File, overrides: ModelOverrides) = models.setOverrides(file, overrides)
                override fun findCopies(query: String) {
                    browser.setQuery(query)
                    browser.search()
                }
                override fun getTemplate(file: File) = models.getTemplate(file)
                override fun removeTemplate(file: File) = models.removeTemplate(file)
            }
        }
        val browse by browser.state.collectAsState()
        val browseActions = remember {
            object : BrowseActions {
                override fun setQuery(query: String) = browser.setQuery(query)
                override fun search() = browser.search()
                override fun recommend() = browser.recommend()
                override fun setFilters(filters: BrowseFilters) = browser.setFilters(filters)
                override fun openRepository(model: HubModel) = browser.openRepository(model)
                override fun download(model: HubModel, assessment: Assessment) = browser.download(model, assessment)
                override fun cancel(repositoryId: String, path: String) = browser.cancel(repositoryId, path)
            }
        }
        LaunchedEffect(Unit) { models.refresh() }
        val known by capabilities.collectAsState()
        val fixed = fixedPromptTokens()
        ModelsScreen(state, actions, onBack, Runtime.getRuntime().availableProcessors(), browse, browseActions, startOnHuggingFace, { backendChoices(known, it) }, fixed)
    }

    @androidx.compose.runtime.Composable
    private fun Settings(onBack: () -> Unit, open: (Destination) -> Unit) {
        val state by settings.state.collectAsState()
        val known by capabilities.collectAsState()
        val actions = remember { settingsActions(open) }
        val fixed = fixedPromptTokens()
        SettingsScreen(state.copy(backends = backendChoices(known, state.inference.backend), fixedPromptTokens = fixed), actions, onBack)
    }

    @androidx.compose.runtime.Composable
    private fun Skills(onBack: () -> Unit, toPermissions: () -> Unit) {
        val rows by skills.rows.collectAsState()
        val actions = remember(toPermissions) {
            object : SkillsActions {
                override fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean) = skills.set(skill, state, highRiskWarningAccepted)
                override fun openPermissions() = toPermissions()
            }
        }
        SkillsScreen(rows, actions, onBack)
    }

    @androidx.compose.runtime.Composable
    private fun Permissions(onBack: () -> Unit, openNetwork: () -> Unit) {
        val granted by grants.grants.collectAsState()
        val addFailed by grants.addFailed.collectAsState()
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { grants.add(it, GrantKind.FOLDER) } }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { grants.add(it, GrantKind.FILE) } }
        val actions = remember {
            object : GrantActions {
                override fun addFolder() = folderPicker.launch(null)
                override fun addFile() = filePicker.launch(arrayOf("*/*"))
                override fun revoke(grant: Grant) = grants.revoke(grant)
            }
        }
        PermissionsScreen(granted, addFailed, actions, openNetwork, onBack)
    }

    @androidx.compose.runtime.Composable
    private fun Memory(onBack: () -> Unit) {
        val facts by memory.facts.collectAsState()
        val actions = remember {
            object : MemoryActions {
                override fun delete(fact: Fact) = memory.delete(fact)
                override fun deleteAll() = memory.deleteAll()
            }
        }
        MemoryScreen(facts, actions, onBack)
    }

    /** The loaded model's fixed prompt size, measured again whenever the loaded model changes. */
    @androidx.compose.runtime.Composable
    private fun fixedPromptTokens(): Int? {
        val model by container.activeModel.state.collectAsState()
        val fixed by androidx.compose.runtime.produceState<Int?>(null, model.active, model.loading) {
            value = if (model.loading) null else container.runtime.fixedPromptTokens()
        }
        return fixed
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
        handleOpenChat(intent)
    }

    override fun onStart() {
        super.onStart()
        container.onScreen = true
    }

    override fun onStop() {
        container.onScreen = false
        super.onStop()
    }

    /** A reply notification's tap: shows the chat it belongs to. */
    private fun handleOpenChat(intent: Intent?) {
        if (intent?.action != ReplyNotifications.ACTION_OPEN_CHAT) return
        val id = intent.getLongExtra(ReplyNotifications.EXTRA_CONVERSATION, -1)
        if (id >= 0) chat.open(id)
    }

    /** Asked once, the first time a reply might need it, if Android needs the permission and does not have it (TASK-048). */
    private val replyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askForNotificationsOnce() {
        if (!needsNotificationPermission()) return
        lifecycleScope.launch {
            if (container.setupSettings.notificationsAskedForReply.first()) return@launch
            container.setupSettings.markNotificationsAskedForReply()
            replyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The Hugging Face sign-in redirect; HubAuth rejects anything that does not match the sign-in it started. */
    private fun handleSignInRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "com.bizzeh.bruce" || uri.path != "/oauth/huggingface") return
        settings.completeSignIn(uri.queryParameterNames.associateWith(uri::getQueryParameter))
    }

    /** Read once off the main thread (it probes the GPU drivers); until then only Auto and CPU are offered. */
    private val capabilities = MutableStateFlow<EngineCapabilities?>(null)

    private fun backendChoices(known: EngineCapabilities?, current: BackendPreference?) =
        BackendSelection.choices(known ?: EngineCapabilities(emptyList(), emptyList()), current)

    private fun deviceProfile(): DeviceProfile {
        val memory = container.memoryInfo()
        return DeviceProfile((memory.availMem - memory.threshold).coerceAtLeast(0), container.cpuFeatures())
    }

    private fun chatActions() = object : ChatActions {
        override fun setInput(input: String) = chat.setInput(input)
        override fun send() {
            askForNotificationsOnce()
            chat.send()
        }
        override fun stop() = chat.stop()
        override fun decide(callId: String, approved: Boolean) = chat.decide(callId, approved)
    }

    private fun conversationActions() = object : ConversationActions {
        override fun open(id: Long) = chat.open(id)
        override fun rename(id: Long, title: String) = conversations.rename(id, title)
        override fun archive(ids: Set<Long>) = conversations.archive(ids)
        override fun restore(ids: Set<Long>) = conversations.restore(ids)
        override fun delete(ids: Set<Long>) = conversations.delete(ids)
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
        override fun setContextTrafficLights(enabled: Boolean) = settings.setContextTrafficLights(enabled)
        override fun setBackend(backend: BackendPreference) = settings.setBackend(backend)
        override fun setThreads(threads: Int?) = settings.setThreads(threads)
        override fun setContextLength(contextLength: Int) = settings.setContextLength(contextLength)
        override fun clearAllData() {
            settings.clearAllData()
            chat.newChat()
        }
        override fun deleteAllConversations() = conversations.deleteAll()
        override fun setNetworkMode(mode: NetworkMode) = settings.setNetworkMode(mode)
        override fun setPersonality(personality: Personality) = settings.setPersonality(personality)
        override fun setSummaryEnabled(enabled: Boolean) = settings.setSummaryEnabled(enabled)
        override fun setSummaryThreshold(threshold: Int) = settings.setSummaryThreshold(threshold)
        override fun signIn() {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.beginSignIn())))
        }
        override fun signOut() = settings.signOut()
        override fun openSkills() = open(Destination.SKILLS)
        override fun openMemory() = open(Destination.MEMORY)
        override fun setMemoryMode(mode: MemoryMode) = settings.setMemoryMode(mode)
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

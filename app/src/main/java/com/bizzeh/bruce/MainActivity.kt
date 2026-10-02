package com.bizzeh.bruce

import com.bizzeh.bruce.models.RemoteChoice
import com.bizzeh.bruce.remote.RemoteServer
import com.bizzeh.bruce.remote.ServersActions
import com.bizzeh.bruce.remote.ServersViewModel
import com.bizzeh.bruce.chat.VoiceText
import com.bizzeh.bruce.chat.VoiceState
import com.bizzeh.bruce.chat.VoiceRecogniser
import com.bizzeh.bruce.chat.AndroidVoiceRecogniser
import com.bizzeh.bruce.chat.AndroidReplySpeaker
import com.bizzeh.bruce.chat.ReadAloud
import com.bizzeh.bruce.surfaces.Surfaces
import com.bizzeh.bruce.chat.ShareIntents
import com.bizzeh.bruce.models.ProfileModel
import com.bizzeh.bruce.search.EmbeddingModel
import com.bizzeh.bruce.settings.FileSearchState
import com.bizzeh.bruce.reminders.AndroidReminderScheduler
import com.bizzeh.bruce.reminders.Reminder
import com.bizzeh.bruce.reminders.RemindersActions
import com.bizzeh.bruce.reminders.RemindersScreen
import com.bizzeh.bruce.reminders.RemindersViewModel
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.repeatOnLifecycle
import com.bizzeh.bruce.chat.DocumentError
import com.bizzeh.bruce.chat.DocumentReader
import com.bizzeh.bruce.chat.DocumentResult
import com.bizzeh.bruce.chat.DocumentText
import com.bizzeh.bruce.settings.InferenceDefaults
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.graphics.asImageBitmap
import com.bizzeh.bruce.chat.VoiceConversation
import kotlinx.coroutines.withContext
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.models.AutoContext
import com.bizzeh.bruce.models.Projectors
import android.util.Log
import android.widget.Toast
import android.content.ActivityNotFoundException
import com.bizzeh.bruce.chat.Markdown
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import com.bizzeh.bruce.conversations.ExportFormat
import com.bizzeh.bruce.conversations.ExportOutcome
import com.bizzeh.bruce.conversations.ExportRequest
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
import com.bizzeh.bruce.models.SuggestedModel
import com.bizzeh.bruce.models.SuggestedModels
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
import com.bizzeh.bruce.settings.FolderReview
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
import com.bizzeh.bruce.setup.SetupContext
import com.bizzeh.bruce.setup.SetupSkills
import com.bizzeh.bruce.setup.SkillChoices
import com.bizzeh.bruce.skills.CapabilityClass
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
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
                container::stopModel,
                container.runtime::acceptsImages,
                container.neutralise,
                container.conversations::profile,
                container.conversations::setProfile,
                container::applyProfile,
            )
        }
    }
    private val conversations: ConversationsViewModel by viewModels {
        factory {
            ConversationsViewModel(container.conversations, { ids -> chat.forget(ids); removeUnusedImages() }, { uri -> contentResolver.openOutputStream(Uri.parse(uri), "wt") })
        }
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
                recommendedRepositories = { SuggestedModels.forDevice(deviceProfile()).map { it.repositoryId }.toSet() },
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
                sites = container.sites,
                searchKey = container.searchKey,
                dynamicColourSupported = dynamicColourSupported(),
                defaultThreads = CpuTopology.defaultThreads(),
                cores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }
    private val servers: ServersViewModel by viewModels {
        factory { ServersViewModel(container.remoteServers, container.serverClient, container.activeModel.state.map { it.remote }) { container.modelSelection.chooseRemote(it.first, it.second) } }
    }
    private val skills: SkillsViewModel by viewModels { factory { SkillsViewModel(container.skills, container.skillStates, container.unmetRequirements) } }
    private val grants: GrantsViewModel by viewModels { factory { GrantsViewModel(container.grants, Dispatchers.IO, container.folderInstructions, onChanged = container.searchSetup::updateIndex) } }
    private val memory: MemoryViewModel by viewModels { factory { MemoryViewModel(container.memory) } }
    private val reminders: RemindersViewModel by viewModels { factory { RemindersViewModel(container.reminders, container.scheduledTasks) } }
    private val suggestedModels by lazy { SuggestedModels.forDevice(deviceProfile()) }

    /** The model the wizard's context step is about, once one is installed or downloading (TASK-111). */
    private val setupContext by lazy {
        combine(models.state, browser.state) { installed, browse -> SetupContext.choice(installed.models, suggestedModels, browse, container.memoryForModels()) }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
    }
    private val setup: SetupViewModel by viewModels {
        factory {
            SetupViewModel(
                container.setupSettings,
                container.networkSettings,
                container.inferenceSettings,
                askNotifications = needsNotificationPermission(),
                suggestions = suggestedModels,
                hasModel = setupContext.map { it != null }.stateIn(lifecycleScope, SharingStarted.Eagerly, false),
                skills = object : SkillChoices {
                    override val skills get() = container.skills.skills.toList()
                    override suspend fun state(skill: Skill) = container.skillStates.state(skill)
                    override suspend fun set(skill: Skill, state: SkillState) {
                        container.skillStates.set(skill, state)
                    }
                    override suspend fun promptTokens(enabled: List<Skill>) = SetupSkills.promptTokens(container.personalityRules(), enabled)
                },
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleSignInRedirect(intent)
            handleOpenChat(intent)
            handleAssist(intent)
            handleShare(intent)
        }
        container.stopTurn = { chat.stop() }
        if (savedInstanceState == null) {
            removeUnusedImages()
            // Alarms are cleared when Bruce is force-stopped; setting them again is harmless.
            lifecycleScope.launch(Dispatchers.IO) {
                container.reminders.rescheduleAll()
                container.scheduledTasks.rescheduleAll()
                // Skills needing the file index read its counts; they are known once read.
                container.fileSearch.refreshCounts()
            }
        }
        watchPhotoRequests()
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
        val voice by voiceState.collectAsState()
        val speech by readAloud.state.collectAsState()
        val conversation by voiceConversation.phase.collectAsState()
        val serverModels by servers.state.collectAsState()
        val activeModel by container.activeModel.state.collectAsState()
        LaunchedEffect(Unit) { container.modelSelection.restore() }
        val browse = exit == SetupExit.BROWSE_MODELS
        val active by conversations.active.collectAsState()
        val archived by conversations.archived.collectAsState()
        val exportOutcome by conversations.exportOutcome.collectAsState()
        LaunchedEffect(exportOutcome) {
            val text = when (val outcome = exportOutcome) {
                is ExportOutcome.Exported -> resources.getQuantityString(R.plurals.conversations_exported, outcome.count, outcome.count)
                ExportOutcome.Failed -> getString(R.string.conversations_export_failed)
                null -> return@LaunchedEffect
            }
            Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
            conversations.exportOutcomeShown()
        }
        ExactRemindersOffer()
        val profiles by container.profiles.profiles.collectAsState(initial = emptyList())
        BruceApp(
            chat = chatState.copy(voice = voice, speech = speech, conversation = conversation),
            chatActions = remember { chatActions() },
            activeModel = activeModel,
            profiles = profiles,
            remoteModels = serverModels.choices(),
            actions = remember { appActions() },
            modelsScreen = { onBack -> Models(onBack, startOnHuggingFace = browse) },
            settingsScreen = { onBack, open -> Settings(onBack, open) },
            diagnosticsScreen = { onBack -> Diagnostics(onBack) },
            skillsScreen = { onBack, openPermissions, openSettings -> Skills(onBack, openPermissions, openSettings) },
            permissionsScreen = { onBack, openNetwork -> Permissions(onBack, openNetwork) },
            memoryScreen = { onBack -> Memory(onBack) },
            remindersScreen = { onBack -> Reminders(onBack) },
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
        val browse by browser.state.collectAsState()
        val contextChoice by setupContext.collectAsState()
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
                override fun download(model: SuggestedModel) = browser.downloadFile(model.repositoryId, model.path)
                override fun cancelDownload(model: SuggestedModel) = browser.cancel(model.repositoryId, model.path)
                override fun setContextLength(contextLength: Int?) = setup.setContextLength(contextLength)
                override fun setSkillGroup(kind: CapabilityClass, on: Boolean) = setup.setSkillGroup(kind, on)
                override fun finish(exit: SetupExit) = setup.finish { onExit(exit) }
            }
        }
        SetupScreen(state, modelsState, actions, browse.downloads, contextChoice)
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
        val serverState by servers.state.collectAsState()
        val serverActions = remember {
            object : ServersActions {
                override fun add(name: String, address: String, key: String) = servers.add(name, address, key)
                override fun check(server: RemoteServer) = servers.check(server)
                override fun remove(server: RemoteServer) = servers.remove(server)
                override fun use(server: RemoteServer, model: String) = servers.use(RemoteChoice(server.id, server.name, model))
            }
        }
        ModelsScreen(state, actions, onBack, Runtime.getRuntime().availableProcessors(), browse, browseActions, startOnHuggingFace, { backendChoices(known, it) }, fixed, serverState, serverActions)
    }

    @androidx.compose.runtime.Composable
    private fun Settings(onBack: () -> Unit, open: (Destination) -> Unit) {
        val state by settings.state.collectAsState()
        val known by capabilities.collectAsState()
        val actions = remember { settingsActions(open) }
        val fixed = fixedPromptTokens()
        val auto = autoContext()
        val searchSetup by container.searchSetup.setup.collectAsState()
        // Read again whenever Settings shows, as the user may have just picked Bruce in Android's settings.
        var assistant by remember { mutableStateOf(false) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            assistant = isAssistant()
            onPauseOrDispose { }
        }
        val index by container.fileSearch.status.collectAsState()
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { container.fileSearch.refreshCounts() } }
        SettingsScreen(
            state.copy(backends = backendChoices(known, state.inference.backend), fixedPromptTokens = fixed, autoContext = auto, fileSearch = FileSearchState(searchSetup, index), assistant = assistant),
            actions,
            onBack,
        )
    }

    @androidx.compose.runtime.Composable
    private fun Skills(onBack: () -> Unit, toPermissions: () -> Unit, toSettings: () -> Unit) {
        val rows by skills.rows.collectAsState()
        val actions = remember(toPermissions, toSettings) {
            object : SkillsActions {
                override fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean) = setSkill(skill, state, highRiskWarningAccepted)
                override fun openPermissions() = toPermissions()
                override fun openNetworkSettings() = toSettings()
            }
        }
        SkillsScreen(rows, actions, onBack)
    }

    @androidx.compose.runtime.Composable
    private fun Permissions(onBack: () -> Unit, openNetwork: () -> Unit) {
        val granted by grants.grants.collectAsState()
        val addFailed by grants.addFailed.collectAsState()
        val folders by grants.folders.collectAsState()
        val reviewing by grants.reviewing.collectAsState()
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { grants.add(it, GrantKind.FOLDER) } }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { grants.add(it, GrantKind.FILE) } }
        val actions = remember {
            object : GrantActions {
                override fun addFolder() = folderPicker.launch(null)
                override fun addFile() = filePicker.launch(arrayOf("*/*"))
                override fun revoke(grant: Grant) = grants.revoke(grant)
                override fun review(grant: Grant) = grants.review(grant)
                override fun choose(review: FolderReview, follow: Boolean) = grants.choose(review, follow)
                override fun closeReview() = grants.closeReview()
            }
        }
        PermissionsScreen(granted, addFailed, actions, openNetwork, onBack, folders, reviewing?.let(folders::get))
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

    @androidx.compose.runtime.Composable
    private fun Reminders(onBack: () -> Unit) {
        val upcoming by reminders.reminders.collectAsState()
        val scheduled by reminders.scheduled.collectAsState()
        // Read again each time the screen shows, as the user may have just allowed exact alarms.
        var exact by remember { mutableStateOf(true) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            exact = AndroidReminderScheduler(this@MainActivity).exactAllowed()
            onPauseOrDispose { }
        }
        val actions = remember {
            object : RemindersActions {
                override fun cancel(reminder: Reminder) = reminders.cancel(reminder)
                override fun cancelTask(task: com.bizzeh.bruce.reminders.ScheduledTask) = reminders.cancel(task)
                override fun allowExact() = openExactAlarmSettings()
            }
        }
        RemindersScreen(upcoming, exact, actions, scheduled, onBack)
    }

    /** Android 12+'s page for letting Bruce set exact alarms. */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            Log.w("BruceReminders", "no exact alarm settings page")
        }
    }

    /** Offered once, the first time a reminder had to be inexact (owner, 2026-09-30). */
    @androidx.compose.runtime.Composable
    private fun ExactRemindersOffer() {
        val offer by container.offerExactReminders.collectAsState()
        val asked by container.exactRemindersAsked.collectAsState(initial = true)
        if (!offer || asked) return
        val dismiss = {
            container.offerExactReminders.value = false
            lifecycleScope.launch { container.markExactRemindersAsked() }
            Unit
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismiss,
            title = { androidx.compose.material3.Text(stringResource(R.string.reminders_exact_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.reminders_exact_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { dismiss(); openExactAlarmSettings() }) { androidx.compose.material3.Text(stringResource(R.string.reminders_exact_open)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = dismiss) { androidx.compose.material3.Text(stringResource(R.string.reminders_exact_later)) } },
        )
    }

    /** The size Auto picks for the loaded model, as loading would; null when none is loaded or the model has its own size. */
    @androidx.compose.runtime.Composable
    private fun autoContext(): Int? {
        val model by container.activeModel.state.collectAsState()
        val picked by androidx.compose.runtime.produceState<Int?>(null, model.active, model.loading) {
            val active = model.active
            value = if (model.loading || active == null || container.modelSettings.overridesNow(active.name).contextLength != null) {
                null
            } else {
                val metadata = withContext(Dispatchers.IO) { (GgufReader.read(active) as? GgufReadResult.Read)?.metadata }
                AutoContext.pick(metadata, container.memoryForModels(), withContext(Dispatchers.IO) { Projectors.bytesFor(active) })
            }
        }
        return picked
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
        handleAssist(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        container.onScreen = true
        voiceState.value = if (voice.available) VoiceState.IDLE else VoiceState.UNAVAILABLE
        readAloud.prepare()
    }

    override fun onDestroy() {
        readAloud.release()
        super.onDestroy()
    }

    override fun onStop() {
        container.onScreen = false
        // The microphone is only for while the user is here.
        voiceConversation.stop()
        stopVoice()
        readAloud.stop()
        super.onStop()
    }

    private val exportLaunchers = ExportFormat.entries.associateWith { format ->
        registerForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri -> conversations.exportTo(uri?.toString()) }
    }

    private fun startExport(ids: Set<Long>?, format: ExportFormat) {
        exportLaunchers.getValue(format).launch(conversations.requestExport(ExportRequest(ids, format)))
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val name = withContext(Dispatchers.IO) { container.chatImages.save(uri) }
                if (name != null) chat.attach(name) else Toast.makeText(this@MainActivity, R.string.chat_image_unreadable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val budget = DocumentText.budget(chat.state.value.context?.total ?: InferenceDefaults.FALLBACK_CONTEXT)
            lifecycleScope.launch {
                when (val result = withContext(Dispatchers.IO) { container.documentReader.read(uri, budget) }) {
                    is DocumentResult.Read -> chat.attachDocument(result.document)
                    is DocumentResult.Failed -> Toast.makeText(this@MainActivity, documentError(result.reason), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun documentError(reason: DocumentError) = when (reason) {
        DocumentError.TOO_LARGE -> R.string.chat_document_too_large
        DocumentError.ENCRYPTED -> R.string.chat_document_encrypted
        DocumentError.NOT_TEXT -> R.string.chat_document_not_text
        DocumentError.EMPTY -> R.string.chat_document_empty
        DocumentError.UNREADABLE -> R.string.chat_document_unreadable
    }

    /** The camera's photo for take_photo, written into the cache through the file provider. */
    private var cameraFile: File? = null

    /** The request the camera was opened for, so returning to the screen does not open it again. */
    private var photoRequest: Any? = null
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile
        cameraFile = null
        lifecycleScope.launch {
            val name = if (taken && file != null) withContext(Dispatchers.IO) { container.chatImages.save(Uri.fromFile(file)) } else null
            withContext(Dispatchers.IO) { file?.delete() }
            name?.let(chat::attach)
            container.photoRequests.complete(name)
        }
    }

    /** Opens the camera when the camera skill asks, once per request. */
    private fun watchPhotoRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                container.photoRequests.pending.collect { request ->
                    if (request == null || request === photoRequest) return@collect
                    photoRequest = request
                    val file = File(File(cacheDir, CAMERA_DIR).apply { mkdirs() }, "photo-${System.currentTimeMillis()}.jpg")
                    cameraFile = file
                    try {
                        camera.launch(FileProvider.getUriForFile(this@MainActivity, "$packageName.camera", file))
                    } catch (e: ActivityNotFoundException) {
                        cameraFile = null
                        container.photoRequests.complete(null)
                    }
                }
            }
        }
    }

    /** Deletes stored images no saved chat shows, keeping those waiting to be sent. */
    private fun removeUnusedImages() {
        lifecycleScope.launch(Dispatchers.IO) {
            container.chatImages.keepOnly(container.conversations.imagesInUse() + chat.state.value.attachments)
        }
    }

    private val readAloud by lazy { ReadAloud(AndroidReplySpeaker(applicationContext)) }

    private val voice: VoiceRecogniser by lazy { AndroidVoiceRecogniser(applicationContext) }
    private val voiceState = MutableStateFlow(VoiceState.UNAVAILABLE)
    private val voiceConversation by lazy {
        VoiceConversation(voice, readAloud, chat.state, { words ->
            chat.setInput(VoiceText.append(chat.state.value.input, words))
            chat.send()
        }, lifecycleScope, SystemClock::elapsedRealtime)
    }

    /** A skill being turned on, waiting for the Android permissions it needs. */
    private var pendingSkill: Pair<() -> Unit, List<String>>? = null
    private val skillPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val (turnOn, required) = pendingSkill ?: return@registerForActivityResult
        pendingSkill = null
        if (required.all { results[it] == true || checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) turnOn() else Toast.makeText(this, R.string.skills_permission_refused, Toast.LENGTH_LONG).show()
    }

    /** A skill needing Android permissions asks for them when it is turned on, and stays off if refused. */
    private fun setSkill(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean) {
        val missing = skill.androidPermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (state == SkillState.DECLINED || missing.isEmpty()) return skills.set(skill, state, highRiskWarningAccepted)
        pendingSkill = { skills.set(skill, state, highRiskWarningAccepted) } to missing
        skillPermissions.launch((missing + skill.optionalPermissions).distinct().toTypedArray())
    }

    /** What to start once the microphone permission is granted. */
    private var afterMicrophone: () -> Unit = {}
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) afterMicrophone() }

    private fun withMicrophone(start: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return start()
        afterMicrophone = start
        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() = withMicrophone(::listen)

    private fun listen() {
        voiceState.value = VoiceState.LISTENING
        voice.start { text ->
            voiceState.value = VoiceState.IDLE
            text?.let { chat.setInput(VoiceText.append(chat.state.value.input, it)) }
        }
    }

    private fun stopVoice() {
        voice.stop()
        if (voiceState.value == VoiceState.LISTENING) voiceState.value = VoiceState.IDLE
    }

    /** Something shared from another app (TASK-100): a new chat with it attached, the request still to type. */
    private fun handleShare(intent: Intent?) {
        val shared = ShareIntents.read(intent) ?: return
        chat.newChat()
        shared.text?.let(chat::attachDocument)
        lifecycleScope.launch {
            shared.images.forEach { uri -> withContext(Dispatchers.IO) { container.chatImages.save(uri) }?.let(chat::attach) }
            val budget = DocumentText.budget(chat.state.value.context?.total ?: InferenceDefaults.FALLBACK_CONTEXT)
            shared.files.forEach { uri ->
                when (val result = withContext(Dispatchers.IO) { container.documentReader.read(uri, budget) }) {
                    is DocumentResult.Read -> chat.attachDocument(result.document)
                    is DocumentResult.Failed -> Toast.makeText(this@MainActivity, documentError(result.reason), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** The assist gesture (TASK-099), a shortcut, the tile or the widget (TASK-101): a fresh chat, listening for Voice. */
    private fun handleAssist(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_ASSIST, Surfaces.ACTION_NEW_CHAT -> chat.newChat()
            Surfaces.ACTION_VOICE -> {
                chat.newChat()
                if (voice.available) startVoice()
            }
        }
    }

    /** Whether the user picked Bruce as the digital assistant app. */
    private fun isAssistant(): Boolean =
        getSystemService(android.app.role.RoleManager::class.java).let { it.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT) && it.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) }

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
        return DeviceProfile(container.memoryForModels(), container.cpuFeatures(), container.memoryInfo().totalMem)
    }

    private fun chatActions() = object : ChatActions {
        override fun setInput(input: String) = chat.setInput(input)
        override fun send() {
            askForNotificationsOnce()
            chat.send()
        }
        override fun stop() = chat.stop()
        override fun decide(callId: String, approved: Boolean) = chat.decide(callId, approved)
        override fun alwaysAllowSites(callId: String) = chat.decide(callId, approved = true, alwaysAllowSites = true)
        override fun openLink(url: String) {
            val address = Markdown.openable(url) ?: return
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (e: ActivityNotFoundException) {
                Log.w("BruceLinks", "no app opens web links")
            }
        }
        override suspend fun loadImage(url: String) = container.remoteImages.load(url)
        override fun startVoice() = this@MainActivity.startVoice()
        override fun stopVoice() = this@MainActivity.stopVoice()
        override fun readAloud(index: Int) {
            chat.state.value.entries.getOrNull(index)?.let { readAloud.read(index, it.text) }
        }
        override fun stopReading() = readAloud.stop()
        override fun startConversation() = withMicrophone(voiceConversation::start)
        override fun attachImage() = imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        override fun removeAttachment(name: String) = chat.removeAttachment(name)
        override fun attachDocument() = documentPicker.launch(DocumentReader.PICKER_TYPES)
        override fun removeDocument(index: Int) = chat.removeDocument(index)
        override suspend fun thumbnail(name: String) = withContext(Dispatchers.IO) { container.chatImages.thumbnail(name, THUMBNAIL_SIDE)?.asImageBitmap() }
        override fun endConversation() = voiceConversation.stop()
    }

    private fun conversationActions() = object : ConversationActions {
        override fun open(id: Long) = chat.open(id)
        override fun rename(id: Long, title: String) = conversations.rename(id, title)
        override fun archive(ids: Set<Long>) = conversations.archive(ids)
        override fun restore(ids: Set<Long>) = conversations.restore(ids)
        override fun delete(ids: Set<Long>) = conversations.delete(ids)
        override fun export(ids: Set<Long>, format: ExportFormat) = startExport(ids, format)
    }

    private fun appActions() = object : AppActions {
        override fun newChat() = chat.newChat()
        override fun selectModel(file: File) {
            lifecycleScope.launch { container.modelSelection.choose(file) }
        }
        override fun selectRemote(choice: RemoteChoice) = servers.use(choice)
        override fun chooseProfile(id: String?) = chat.chooseProfile(id)
        override fun saveProfile(name: String) {
            val state = container.activeModel.state.value
            val model = state.remote?.let { ProfileModel.Remote(it.serverId, it.model) } ?: state.active?.let { ProfileModel.Local(it.name) } ?: return
            lifecycleScope.launch { chat.chooseProfile(container.profiles.add(name, model).id) }
        }
        override fun removeProfile(id: String) {
            lifecycleScope.launch {
                container.profiles.remove(id)
                if (chat.state.value.profileId == id) chat.chooseProfile(null)
            }
        }
    }

    private fun settingsActions(open: (Destination) -> Unit) = object : SettingsActions {
        override fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
        override fun setDynamicColour(enabled: Boolean) = settings.setDynamicColour(enabled)
        override fun setShowContextWindow(enabled: Boolean) = settings.setShowContextWindow(enabled)
        override fun setBackend(backend: BackendPreference) = settings.setBackend(backend)
        override fun setThreads(threads: Int?) = settings.setThreads(threads)
        override fun setContextLength(contextLength: Int?) = settings.setContextLength(contextLength)
        override fun setThinking(enabled: Boolean) = settings.setThinking(enabled)
        override fun clearAllData() {
            settings.clearAllData()
            chat.newChat()
        }
        override fun deleteAllConversations() = conversations.deleteAll()
        override fun exportAllConversations(format: ExportFormat) = startExport(null, format)
        override fun setNetworkMode(mode: NetworkMode) = settings.setNetworkMode(mode)
        override fun removeSite(host: String) = settings.removeSite(host)
        override fun saveSearchKey(key: String) = settings.saveSearchKey(key)
        override fun removeSearchKey() = settings.removeSearchKey()
        override fun openSearchSignUp() {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BRAVE_SIGN_UP)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (e: ActivityNotFoundException) {
                Log.w("BruceLinks", "no app opens web links")
            }
        }
        override fun setPersonality(personality: Personality) = settings.setPersonality(personality)
        override fun setSummaryEnabled(enabled: Boolean) = settings.setSummaryEnabled(enabled)
        override fun setSummaryThreshold(threshold: Int) = settings.setSummaryThreshold(threshold)
        override fun signIn() {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.beginSignIn())))
        }
        override fun signInWithToken(token: String) = settings.signInWithToken(token)
        override fun signOut() = settings.signOut()
        override fun openSkills() = open(Destination.SKILLS)
        override fun openMemory() = open(Destination.MEMORY)
        override fun openAssistantSettings() {
            // The role cannot be requested (research/sources/android-assistant-role-2026-09-30); the user picks it here.
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
        override fun downloadSearchModel() {
            lifecycleScope.launch { container.searchSetup.download() }
        }
        override fun updateIndex() = container.searchSetup.updateIndex()
        override fun removeSearch() {
            lifecycleScope.launch { container.searchSetup.remove() }
        }
        override fun openSearchModelTerms() {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EmbeddingModel.TERMS_URL)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (e: ActivityNotFoundException) {
                Log.w("BruceLinks", "no app opens web links")
            }
        }
        override fun openReminders() = open(Destination.REMINDERS)
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

        private const val CAMERA_DIR = "camera"

        /** Pixels a side for chat image previews. */
        private const val THUMBNAIL_SIDE = 480

        /** Where the user gets their own Brave Search key (research/sources/brave-search-api-2026-09-29). */
        private const val BRAVE_SIGN_UP = "https://api-dashboard.search.brave.com/register"
    }
}

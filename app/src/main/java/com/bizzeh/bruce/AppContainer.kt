package com.bizzeh.bruce

import com.bizzeh.bruce.remote.RemoteBackend
import com.bizzeh.bruce.models.RemoteChoice
import com.bizzeh.bruce.remote.ServerClient
import com.bizzeh.bruce.remote.RemoteServerStore
import com.bizzeh.bruce.skills.web.SearchKeyRepository
import com.bizzeh.bruce.skills.web.BraveSearch
import com.bizzeh.bruce.policy.SiteRule
import com.bizzeh.bruce.skills.web.WebPages
import com.bizzeh.bruce.skills.web.WebSkills
import com.bizzeh.bruce.policy.SiteAccess
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.settings.AndroidSettingsWriter
import com.bizzeh.bruce.skills.settings.SettingsSkills
import com.bizzeh.bruce.skills.settings.AndroidSettingsReader
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.chat.RemoteImages
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.bizzeh.bruce.chat.ChatImages
import com.bizzeh.bruce.chat.DocumentReader
import com.bizzeh.bruce.chat.DocumentResult
import com.bizzeh.bruce.inference.LlamaNative
import com.bizzeh.bruce.search.EmbeddingModel
import com.bizzeh.bruce.models.ProfileModel
import com.bizzeh.bruce.models.ProfileStore
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.search.FileSearch
import com.bizzeh.bruce.search.IndexWork
import com.bizzeh.bruce.search.LlamaEmbedder
import com.bizzeh.bruce.search.SearchDatabase
import com.bizzeh.bruce.search.SearchSetup
import com.bizzeh.bruce.search.SearchSkills
import com.bizzeh.bruce.chat.DocumentText
import com.bizzeh.bruce.chat.ChatMemory
import com.bizzeh.bruce.chat.TurnObserver
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LlamaCppEngine
import com.bizzeh.bruce.inference.SavedPrompts
import com.bizzeh.bruce.inference.ToolChatMessage
import android.os.BatteryManager
import com.bizzeh.bruce.chat.ChatEntry
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.RuntimeEvent
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.memory.MemoryDatabase
import com.bizzeh.bruce.memory.MemorySettingsRepository
import com.bizzeh.bruce.memory.MemoryStore
import com.bizzeh.bruce.models.ActiveModel
import com.bizzeh.bruce.models.ModelImporter
import com.bizzeh.bruce.models.ModelSelection
import com.bizzeh.bruce.models.ModelSettingsRepository
import com.bizzeh.bruce.notifications.NoteSkills
import com.bizzeh.bruce.notifications.ReplyNotifications
import com.bizzeh.bruce.notifications.ReplyService
import com.bizzeh.bruce.policy.FolderInstructionsStore
import com.bizzeh.bruce.settings.DataReset
import com.bizzeh.bruce.conversations.ConversationDatabase
import com.bizzeh.bruce.conversations.ConversationStore
import com.bizzeh.bruce.policy.AndroidDocumentAccess
import com.bizzeh.bruce.policy.DocumentAccess
import com.bizzeh.bruce.policy.GrantScope
import com.bizzeh.bruce.policy.GrantStore
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.ToolOutput
import com.bizzeh.bruce.skills.apps.AndroidPhoneActions
import com.bizzeh.bruce.skills.apps.AppSkills
import com.bizzeh.bruce.skills.calendar.AndroidCalendarStore
import com.bizzeh.bruce.reminders.AndroidReminderScheduler
import com.bizzeh.bruce.reminders.ReminderDatabase
import com.bizzeh.bruce.reminders.ReminderSkills
import com.bizzeh.bruce.reminders.ScheduledTasks
import com.bizzeh.bruce.reminders.TaskOutcome
import com.bizzeh.bruce.reminders.WorkManagerTasks
import com.bizzeh.bruce.reminders.ReminderStore
import com.bizzeh.bruce.skills.camera.CameraSkills
import com.bizzeh.bruce.skills.camera.PhotoRequests
import com.bizzeh.bruce.skills.contacts.AndroidContacts
import com.bizzeh.bruce.skills.contacts.ContactSkills
import com.bizzeh.bruce.skills.location.AndroidLocationSource
import com.bizzeh.bruce.skills.location.LocationSkills
import com.bizzeh.bruce.skills.calendar.CalendarSkills
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.runtime.BruceRuntime
import android.content.pm.PackageManager
import com.bizzeh.bruce.skills.automatic.AndroidPhoneReaders
import com.bizzeh.bruce.skills.automatic.AutomaticSkills
import com.bizzeh.bruce.skills.files.FileSkills
import androidx.room.Room
import kotlinx.coroutines.flow.MutableStateFlow
import com.bizzeh.bruce.setup.SetupSettingsRepository
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.HubAuth
import com.bizzeh.bruce.huggingface.HubClient
import com.bizzeh.bruce.huggingface.KeystoreTokenCipher
import com.bizzeh.bruce.huggingface.ModelDownloader
import com.bizzeh.bruce.huggingface.UrlConnectionTransport
import com.bizzeh.bruce.settings.InferenceSettingsRepository
import com.bizzeh.bruce.settings.NetworkSettingsRepository
import com.bizzeh.bruce.settings.PersonalitySettingsRepository
import com.bizzeh.bruce.settings.SummarySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.bizzeh.bruce.settings.ThemeSettingsRepository
import com.bizzeh.bruce.settings.settingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

class BruceApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Services shared by every screen. There is one engine per process because only one model can
 * be loaded at a time, and chat, model management and diagnostics all use it.
 */
class AppContainer(private val context: Context) {
    // llama.cpp contexts are not thread-safe, so the engine gets one thread of its own.
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * Evaluated system prompts (TASK-109), in the cache folder: Clear all data empties it, and Android
     * may too when storage runs low. The build includes the install time, so an update never loads
     * state saved by an older llama.cpp.
     */
    private val savedPrompts: SavedPrompts by lazy {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        SavedPrompts(File(context.cacheDir, "prompt-states"), "${BuildConfig.VERSION_CODE}-$installed")
    }

    val engine: InferenceEngine by lazy { LlamaCppEngine.create(context.applicationInfo.nativeLibraryDir, nativeThread, savedPrompts) }

    val modelsDir: File = File(context.filesDir, MODELS_DIR)

    val importer: ModelImporter by lazy { ModelImporter(context.contentResolver, modelsDir, Dispatchers.IO) }

    val activeModel: ActiveModel by lazy { ActiveModel(engine, modelsDir, Dispatchers.IO) }

    val themeSettings: ThemeSettingsRepository by lazy { ThemeSettingsRepository(context.settingsDataStore) }

    val inferenceSettings: InferenceSettingsRepository by lazy { InferenceSettingsRepository(context.settingsDataStore) }

    val networkSettings: NetworkSettingsRepository by lazy { NetworkSettingsRepository(context.settingsDataStore) }

    val personalitySettings: PersonalitySettingsRepository by lazy { PersonalitySettingsRepository(context.settingsDataStore) }

    val summarySettings: SummarySettingsRepository by lazy { SummarySettingsRepository(context.settingsDataStore) }

    val replyNotifications: ReplyNotifications by lazy { ReplyNotifications(context).also { it.createChannels() } }

    /** Whether a Bruce screen is showing; MainActivity keeps it up to date. */
    @Volatile
    var onScreen: Boolean = false

    /** Stops the turn in progress; set by the chat, used when Android ends the reply service. */
    @Volatile
    var stopTurn: () -> Unit = {}

    /** Keeps a turn alive off screen and announces its reply there (TASK-048). */
    val turnObserver: TurnObserver = object : TurnObserver {
        override fun turnStarted() {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ReplyService::class.java))
            } catch (e: IllegalStateException) {
                // Not allowed from the background (ForegroundServiceStartNotAllowedException): the turn runs without it.
                Log.w("BruceReply", "reply service not started: ${e.javaClass.simpleName}")
            }
        }

        override fun turnFinished(conversationId: Long, reply: String?, completed: Boolean) {
            context.stopService(Intent(context, ReplyService::class.java))
            if (completed && !onScreen && !reply.isNullOrBlank()) replyNotifications.replyReady(conversationId, reply)
        }
    }

    val memorySettings: MemorySettingsRepository by lazy { MemorySettingsRepository(context.settingsDataStore) }

    private val memoryDatabase: MemoryDatabase by lazy { Room.databaseBuilder(context, MemoryDatabase::class.java, MemoryDatabase.NAME).build() }

    val memory: MemoryStore by lazy { MemoryStore(memoryDatabase.facts()) }

    /** Memory as the chat uses it: the scope follows the Memory setting and the loaded model. */
    val chatMemory: ChatMemory = object : ChatMemory {
        override suspend fun recall(message: String): List<String> {
            val scope = memoryScope() ?: return emptyList()
            return memory.recall(scope, message).also { Log.i("BruceMemory", "recalled=${it.size}") }
        }

        override suspend fun learn(history: List<ToolChatMessage>, recalled: List<String>) {
            val scope = memoryScope() ?: return
            val facts = runtime.extractFacts(history, recalled) ?: return
            val added = memory.add(scope, facts)
            // Counts only: facts are the user's own words.
            Log.i("BruceMemory", "facts found=${facts.size} new=$added")
        }

        private suspend fun memoryScope(): String? = MemoryStore.scope(
            memorySettings.mode.first(),
            activeModel.state.value.let { model -> model.active?.name ?: model.remote?.let { "remote:${it.serverId}:${it.model}" } },
        )
    }

    internal suspend fun personalityRules(): String {
        val personality = personalitySettings.personality.first()
        return withContext(Dispatchers.IO) { context.resources.openRawResource(personality.rules).bufferedReader().use { it.readText() } }
    }

    private val transport: HttpTransport by lazy { UrlConnectionTransport() }

    private val userAgent = "Bruce/${BuildConfig.VERSION_NAME}"

    val hubAuth: HubAuth by lazy {
        HubAuth(
            transport, context.settingsDataStore, KeystoreTokenCipher(), networkSettings::huggingFaceAllowed,
            Dispatchers.IO, BuildConfig.HUGGING_FACE_CLIENT_ID,
        )
    }

    val hubClient: HubClient by lazy {
        HubClient(transport, networkSettings::huggingFaceAllowed, Dispatchers.IO, userAgent, token = hubAuth::accessToken)
    }

    val downloader: ModelDownloader by lazy {
        ModelDownloader(transport, modelsDir, networkSettings::huggingFaceAllowed, Dispatchers.IO, userAgent, token = hubAuth::accessToken)
    }

    /** Only Any site allows images: Approved sites has no list of sites yet, so none are approved. */
    val remoteImages: RemoteImages by lazy {
        RemoteImages(transport, { networkSettings.mode.first() == NetworkMode.GENERAL }, Dispatchers.IO, userAgent)
    }

    val setupSettings: SetupSettingsRepository by lazy { SetupSettingsRepository(context.settingsDataStore) }

    val modelSettings: ModelSettingsRepository by lazy { ModelSettingsRepository(context.settingsDataStore) }

    val modelSelection: ModelSelection by lazy {
        ModelSelection(activeModel, modelSettings, inferenceSettings, modelsDir, Dispatchers.IO, ::memoryForModels, forgetSavedPrompt = savedPrompts::forget) {
            remoteServers.servers.first()
        }
    }

    private val conversationDatabase: ConversationDatabase by lazy {
        Room.databaseBuilder(context, ConversationDatabase::class.java, ConversationDatabase.NAME)
            .addMigrations(ConversationDatabase.MIGRATION_1_2, ConversationDatabase.MIGRATION_2_3, ConversationDatabase.MIGRATION_3_4, ConversationDatabase.MIGRATION_4_5)
            .build()
    }

    val conversations: ConversationStore by lazy { ConversationStore(conversationDatabase.conversations()) }

    private val policyDatabase: PolicyDatabase by lazy {
        Room.databaseBuilder(context, PolicyDatabase::class.java, PolicyDatabase.NAME)
            .addMigrations(PolicyDatabase.MIGRATION_1_2, PolicyDatabase.MIGRATION_2_3, PolicyDatabase.MIGRATION_3_4)
            .build()
    }

    val skillStates: SkillStateStore by lazy { SkillStateStore(policyDatabase.policy()) }

    private val documentAccess: DocumentAccess by lazy { AndroidDocumentAccess(context.contentResolver) }

    val grants: GrantStore by lazy { GrantStore(policyDatabase.policy(), documentAccess) }

    private val grantScope: GrantScope by lazy { GrantScope(grants, documentAccess) }

    private val searchDatabase: SearchDatabase by lazy { Room.databaseBuilder(context, SearchDatabase::class.java, SearchDatabase.NAME).build() }

    /** The embedding model gets a thread of its own, apart from the chat model's (TASK-096). */
    private val embeddingThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /** Search over the text of granted files (TASK-096). */
    val fileSearch: FileSearch by lazy {
        // The engine loads llama.cpp's backends, which the embedding model needs too.
        engine
        FileSearch(
            searchDatabase.search(),
            grants = { grants.current() },
            access = documentAccess,
            embedder = LlamaEmbedder(LlamaNative, EmbeddingModel.file(modelsDir), embeddingThread),
            extract = { name, type, bytes -> (documentReader.parse(name, type, bytes, FileSearch.MAX_FILE_CHARS) as? DocumentResult.Read)?.document?.text },
        )
    }

    val searchSetup: SearchSetup by lazy {
        val file = EmbeddingModel.file(modelsDir)
        SearchSetup(
            fetch = { progress -> downloader.download(EmbeddingModel.REPOSITORY, EmbeddingModel.PATH, EmbeddingModel.SIZE_BYTES, EmbeddingModel.SHA256, destination = file, onProgress = progress) },
            modelFile = file,
            search = fileSearch,
            work = IndexWork(context),
        )
    }

    fun searchModelInstalled(): Boolean = EmbeddingModel.file(modelsDir).isFile

    val folderInstructions: FolderInstructionsStore by lazy { FolderInstructionsStore(policyDatabase.policy(), documentAccess) }

    /** The names the model starts file paths with. */
    suspend fun grantNames(): List<String> = grantScope.names()

    private val calendarSkills by lazy { CalendarSkills(AndroidCalendarStore(context.contentResolver), Dispatchers.IO) }

    private val settingsSkills by lazy { SettingsSkills(AndroidSettingsReader(context), AndroidSettingsWriter(context)) }

    val sites: SiteAccess by lazy { SiteAccess(policyDatabase.policy(), { networkSettings.mode.first() }) }

    val skills: SkillRegistry by lazy {
        SkillRegistry(
            AutomaticSkills.create(AndroidPhoneReaders(context)) +
                FileSkills(grantScope, documentAccess, Dispatchers.IO, folderInstructions::guidance).create() +
                settingsSkills.create() +
                calendarSkills.create() +
                AppSkills(AndroidPhoneActions(context), onScreen = { onScreen }).create() +
                NoteSkills(replyNotifications).create() +
                LocationSkills(AndroidLocationSource(context), onScreen = { onScreen }).create() +
                ContactSkills(AndroidContacts(context.contentResolver, context.resources), Dispatchers.IO).create() +
                CameraSkills(photoRequests, onScreen = { onScreen }).create() +
                ReminderSkills(reminders, scheduledTasks, inexact = { offerExactReminders.value = true }).create() +
                SearchSkills(fileSearch).create() +
                WebSkills(
                    WebPages(transport, Dispatchers.IO, userAgent),
                    allowed = { host -> sites.rule(host) == SiteRule.ALLOWED },
                    search = BraveSearch(transport, Dispatchers.IO, userAgent, searchKey::key),
                ).create(),
        )
    }

    /** Whether a skill's requirement holds now; a skill whose requirement does not is locked off. */
    suspend fun requirementMet(requirement: SkillRequirement): Boolean = when (requirement) {
        SkillRequirement.FILE_GRANT -> grantNames().isNotEmpty()
        SkillRequirement.NETWORK_ALLOWED -> networkSettings.mode.first() != NetworkMode.OFFLINE
        SkillRequirement.SEARCH_KEY -> searchKey.saved.first()
        SkillRequirement.FILE_INDEX -> searchModelInstalled() && fileSearch.status.value.passages > 0
    }

    private val reminderDatabase: ReminderDatabase by lazy { Room.databaseBuilder(context, ReminderDatabase::class.java, ReminderDatabase.NAME).addMigrations(ReminderDatabase.MIGRATION_1_2).build() }

    /** Reminders set by the reminders skill (TASK-094). */
    val reminders: ReminderStore by lazy { ReminderStore(reminderDatabase.reminders(), AndroidReminderScheduler(context)) }

    /** Named model choices picked per chat (TASK-098). */
    val profiles: ProfileStore by lazy { ProfileStore(context.settingsDataStore) }

    /** Switches to a profile's model unless it is in use already; false if its model or server has gone. */
    suspend fun applyProfile(id: String): Boolean {
        val profile = profiles.profile(id) ?: return false
        val state = activeModel.state.value
        return when (val model = profile.model) {
            is ProfileModel.Local -> {
                val file = File(modelsDir, model.fileName)
                when {
                    state.active == file && state.remote == null -> true
                    !file.isFile -> false
                    else -> modelSelection.choose(file) is LoadResult.Loaded
                }
            }
            is ProfileModel.Remote -> {
                if (state.remote?.serverId == model.serverId && state.remote.model == model.model) return true
                val server = remoteServers.servers.first().firstOrNull { it.id == model.serverId } ?: return false
                modelSelection.chooseRemote(server, model.model)
                true
            }
        }
    }

    /** Prompts Bruce runs later (TASK-095); a phone model runs them only while charging. */
    val scheduledTasks: ScheduledTasks by lazy {
        ScheduledTasks(
            reminderDatabase.tasks(),
            WorkManagerTasks(context),
            run = ::runScheduled,
            usesPhoneModel = { activeModel.state.value.remote == null },
            charging = { context.getSystemService(BatteryManager::class.java).isCharging },
            announce = { outcome ->
                when (outcome) {
                    is TaskOutcome.Answered -> replyNotifications.replyReady(outcome.conversationId, outcome.reply)
                    is TaskOutcome.Failed -> replyNotifications.post(context.getString(R.string.tasks_failed_title), outcome.reason)
                }
            },
        )
    }

    /** One scheduled prompt as a new chat, loading the chosen model first if nothing is loaded. */
    private suspend fun runScheduled(prompt: String): TaskOutcome {
        val local = { activeModel.state.value.remote == null }
        // The app may be loading the model itself as it starts; a second load would only replace it.
        activeModel.state.first { !it.loading }
        if (local() && engine.getModelInfo() == null) modelSelection.restore()
        if (local() && engine.getModelInfo() == null) {
            Log.w(TASKS_TAG, "scheduled task: no model loaded (${activeModel.state.value.error}, ${activeModel.state.value.installed.size} installed)")
            return TaskOutcome.Failed(context.getString(R.string.tasks_failed_no_model))
        }
        Log.i(TASKS_TAG, "scheduled task: running")
        var reply: String? = null
        var failure: RuntimeError? = null
        runtime.respond(listOf(ToolChatMessage(ChatRole.USER, prompt)), unattended = true).collect { event ->
            when (event) {
                is RuntimeEvent.Step -> if (event.toolCalls.isEmpty()) reply = event.content.trim()
                is RuntimeEvent.Failed -> failure = event.error
                else -> Unit
            }
        }
        val answer = reply?.takeIf { it.isNotEmpty() } ?: return TaskOutcome.Failed(context.getString(R.string.tasks_failed_no_reply, failure?.name ?: "none"))
            .also { Log.w(TASKS_TAG, "scheduled task: no reply (${failure?.name})") }
        val id = conversations.save(null, listOf(ChatEntry(ChatRole.USER, prompt), ChatEntry(ChatRole.ASSISTANT, answer)))
        return TaskOutcome.Answered(id, answer)
    }

    /** Set when a reminder had to be inexact, so the screen can offer exact timing once. */
    val offerExactReminders = MutableStateFlow(false)

    /** Whether the exact timing offer was shown already; it is made once only. */
    val exactRemindersAsked: Flow<Boolean> by lazy { context.settingsDataStore.data.map { it[EXACT_REMINDERS_ASKED] == true } }

    suspend fun markExactRemindersAsked() {
        context.settingsDataStore.edit { it[EXACT_REMINDERS_ASKED] = true }
    }

    /** The camera skill's requests to the screen (TASK-093). */
    val photoRequests = PhotoRequests()

    /** Documents attached to chats (TASK-084); PDF parsing may spill to the cache folder on disk. */
    val documentReader: DocumentReader by lazy { DocumentReader(context, context.contentResolver, context.cacheDir) }

    /** Disarms prompt-format markers, and the document tags around it, in text the user attaches, without cutting it. */
    val neutralise: (String) -> String = ToolOutput(reservedMarkers = RESERVED_MARKERS + DocumentText.TAGS, maxLength = Int.MAX_VALUE)::clean

    /** Images attached to chats (TASK-082). */
    val chatImages: ChatImages by lazy { ChatImages(File(context.filesDir, ChatImages.DIR), context.contentResolver) }

    val remoteServers: RemoteServerStore by lazy { RemoteServerStore(context.settingsDataStore, KeystoreTokenCipher(SERVER_KEY_ALIAS)) }

    /** The chosen server model's backend, made again only when the choice changes; null when the model is on the phone. */
    private var remoteBackend: Pair<RemoteChoice, RemoteBackend>? = null

    suspend fun remoteBackend(): RemoteBackend? {
        val choice = activeModel.state.value.remote ?: return null
        remoteBackend?.takeIf { it.first == choice }?.let { return it.second }
        val server = remoteServers.servers.first().firstOrNull { it.id == choice.serverId } ?: return null
        return RemoteBackend(server, choice.model, remoteServers.key(server.id), transport, Dispatchers.IO, userAgent, chatImages).also { remoteBackend = choice to it }
    }

    /** Stops generation wherever the chosen model runs. */
    fun stopModel() {
        engine.stop()
        remoteBackend?.second?.stop()
    }

    val serverClient: ServerClient by lazy {
        ServerClient(transport, Dispatchers.IO, userAgent) { networkSettings.mode.first() != NetworkMode.OFFLINE }
    }

    val searchKey: SearchKeyRepository by lazy { SearchKeyRepository(context.settingsDataStore, KeystoreTokenCipher(SEARCH_KEY_ALIAS)) }

    /** The requirements not met now, as the Skills screen shows them. */
    val unmetRequirements: Flow<Set<SkillRequirement>> by lazy {
        combine(grants.grants, networkSettings.mode, searchKey.saved, fileSearch.status) { granted, mode, key, index ->
            setOfNotNull(
                SkillRequirement.FILE_GRANT.takeIf { granted.none { it.available } },
                SkillRequirement.NETWORK_ALLOWED.takeIf { mode == NetworkMode.OFFLINE },
                SkillRequirement.SEARCH_KEY.takeIf { !key },
                SkillRequirement.FILE_INDEX.takeIf { index.passages == 0 || !searchModelInstalled() },
            )
        }
    }

    val runtime: BruceRuntime by lazy {
        val policy = PolicyEngine(skills, skillStates, ToolOutput(reservedMarkers = RESERVED_MARKERS), permissionGranted = { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }, scope = { request ->
            when (request.skill.scope) {
                ResourceScope.PHONE_SETTINGS -> settingsSkills.check(request)
                ResourceScope.CALENDAR -> calendarSkills.check(request)
                else -> grantScope.check(request)
            }
        }, requirementMet = ::requirementMet, sites = sites)
        BruceRuntime(engine, skills, skillStates, policy, temperature = { modelSelection.activeTemperature() }, personality = ::personalityRules, grantNames = ::grantNames, requirementMet = ::requirementMet, remote = ::remoteBackend, images = chatImages)
    }

    val dataReset: DataReset by lazy {
        DataReset(activeModel, context.settingsDataStore, modelsDir, context.cacheDir, Dispatchers.IO) {
            conversations.deleteAll()
            chatImages.keepOnly(emptySet())
            reminders.deleteAll()
            scheduledTasks.deleteAll()
            searchSetup.remove()
            skillStates.reset()
            grants.clear()
            sites.clear()
            memory.deleteAll()
        }
    }

    val cpuFeatures: () -> CpuFeatures = CpuFeatures::detect

    fun memoryInfo(): ActivityManager.MemoryInfo =
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }

    /** Memory a model may use: what is free now, plus what the loaded model takes, since loading another replaces it. */
    fun memoryForModels(): Long {
        val memory = memoryInfo()
        return (memory.availMem - memory.threshold).coerceAtLeast(0) + activeModel.state.value.memoryBytes
    }

    companion object {
        const val MODELS_DIR = "models"

        /** Its own Keystore key, apart from the Hugging Face token's. */
        private const val SEARCH_KEY_ALIAS = "bruce.brave_search_key"
        private const val SERVER_KEY_ALIAS = "bruce.server_keys"

        /** Tool-call markup a skill result must not be able to imitate (ADR 0001 formats). */
        private val EXACT_REMINDERS_ASKED = androidx.datastore.preferences.core.booleanPreferencesKey("exact_reminders_asked")
        private const val TASKS_TAG = "BruceTasks"
        private val RESERVED_MARKERS = listOf("<tool_call>", "</tool_call>", "<tool_response>", "</tool_response>", "<|tool_call_start|>", "<|tool_call_end|>")
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as BruceApplication).container

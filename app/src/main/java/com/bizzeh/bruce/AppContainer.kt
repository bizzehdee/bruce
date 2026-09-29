package com.bizzeh.bruce

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
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
import com.bizzeh.bruce.chat.ChatMemory
import com.bizzeh.bruce.chat.TurnObserver
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LlamaCppEngine
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.memory.MemoryDatabase
import com.bizzeh.bruce.memory.MemorySettingsRepository
import com.bizzeh.bruce.memory.MemoryStore
import com.bizzeh.bruce.models.ActiveModel
import com.bizzeh.bruce.models.ModelImporter
import com.bizzeh.bruce.models.ModelSelection
import com.bizzeh.bruce.models.ModelSettingsRepository
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
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.runtime.BruceRuntime
import android.content.pm.PackageManager
import com.bizzeh.bruce.skills.automatic.AndroidPhoneReaders
import com.bizzeh.bruce.skills.automatic.AutomaticSkills
import com.bizzeh.bruce.skills.files.FileSkills
import androidx.room.Room
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

    val engine: InferenceEngine by lazy { LlamaCppEngine.create(context.applicationInfo.nativeLibraryDir, nativeThread) }

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

        private suspend fun memoryScope(): String? = MemoryStore.scope(memorySettings.mode.first(), activeModel.state.value.active?.name)
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
        ModelSelection(activeModel, modelSettings, inferenceSettings, modelsDir, Dispatchers.IO, ::memoryForModels)
    }

    private val conversationDatabase: ConversationDatabase by lazy {
        Room.databaseBuilder(context, ConversationDatabase::class.java, ConversationDatabase.NAME)
            .addMigrations(ConversationDatabase.MIGRATION_1_2)
            .build()
    }

    val conversations: ConversationStore by lazy { ConversationStore(conversationDatabase.conversations()) }

    private val policyDatabase: PolicyDatabase by lazy {
        Room.databaseBuilder(context, PolicyDatabase::class.java, PolicyDatabase.NAME)
            .addMigrations(PolicyDatabase.MIGRATION_1_2, PolicyDatabase.MIGRATION_2_3)
            .build()
    }

    val skillStates: SkillStateStore by lazy { SkillStateStore(policyDatabase.policy()) }

    private val documentAccess: DocumentAccess by lazy { AndroidDocumentAccess(context.contentResolver) }

    val grants: GrantStore by lazy { GrantStore(policyDatabase.policy(), documentAccess) }

    private val grantScope: GrantScope by lazy { GrantScope(grants, documentAccess) }

    val folderInstructions: FolderInstructionsStore by lazy { FolderInstructionsStore(policyDatabase.policy(), documentAccess) }

    /** The names the model starts file paths with. */
    suspend fun grantNames(): List<String> = grantScope.names()

    private val settingsSkills by lazy { SettingsSkills(AndroidSettingsReader(context), AndroidSettingsWriter(context)) }

    val skills: SkillRegistry by lazy {
        SkillRegistry(
            AutomaticSkills.create(AndroidPhoneReaders(context)) +
                FileSkills(grantScope, documentAccess, Dispatchers.IO, folderInstructions::guidance).create() +
                settingsSkills.create(),
        )
    }

    /** Whether a skill's requirement holds now; a skill whose requirement does not is locked off. */
    suspend fun requirementMet(requirement: SkillRequirement): Boolean = when (requirement) {
        SkillRequirement.FILE_GRANT -> grantNames().isNotEmpty()
        SkillRequirement.NETWORK_ALLOWED -> networkSettings.mode.first() != NetworkMode.OFFLINE
    }

    /** The requirements not met now, as the Skills screen shows them. */
    val unmetRequirements: Flow<Set<SkillRequirement>> by lazy {
        combine(grants.grants, networkSettings.mode) { granted, mode ->
            setOfNotNull(
                SkillRequirement.FILE_GRANT.takeIf { granted.none { it.available } },
                SkillRequirement.NETWORK_ALLOWED.takeIf { mode == NetworkMode.OFFLINE },
            )
        }
    }

    val runtime: BruceRuntime by lazy {
        val policy = PolicyEngine(skills, skillStates, ToolOutput(reservedMarkers = RESERVED_MARKERS), permissionGranted = { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }, scope = { request ->
            when (request.skill.scope) {
                ResourceScope.PHONE_SETTINGS -> settingsSkills.check(request)
                else -> grantScope.check(request)
            }
        }, requirementMet = ::requirementMet)
        BruceRuntime(engine, skills, skillStates, policy, temperature = { modelSelection.activeTemperature() }, personality = ::personalityRules, grantNames = ::grantNames, networkAllowed = { requirementMet(SkillRequirement.NETWORK_ALLOWED) })
    }

    val dataReset: DataReset by lazy {
        DataReset(activeModel, context.settingsDataStore, modelsDir, context.cacheDir, Dispatchers.IO) {
            conversations.deleteAll()
            skillStates.reset()
            grants.clear()
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

        /** Tool-call markup a skill result must not be able to imitate (ADR 0001 formats). */
        private val RESERVED_MARKERS = listOf("<tool_call>", "</tool_call>", "<tool_response>", "</tool_response>", "<|tool_call_start|>", "<|tool_call_end|>")
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as BruceApplication).container

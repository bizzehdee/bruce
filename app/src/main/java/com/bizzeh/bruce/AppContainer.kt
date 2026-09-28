package com.bizzeh.bruce

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LlamaCppEngine
import com.bizzeh.bruce.models.ActiveModel
import com.bizzeh.bruce.models.ModelImporter
import com.bizzeh.bruce.models.ModelSelection
import com.bizzeh.bruce.models.ModelSettingsRepository
import com.bizzeh.bruce.settings.DataReset
import com.bizzeh.bruce.conversations.ConversationDatabase
import com.bizzeh.bruce.conversations.ConversationStore
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.ToolOutput
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.runtime.BruceRuntime
import android.content.pm.PackageManager
import com.bizzeh.bruce.skills.automatic.AndroidPhoneReaders
import com.bizzeh.bruce.skills.automatic.AutomaticSkills
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

    val setupSettings: SetupSettingsRepository by lazy { SetupSettingsRepository(context.settingsDataStore) }

    val modelSettings: ModelSettingsRepository by lazy { ModelSettingsRepository(context.settingsDataStore) }

    val modelSelection: ModelSelection by lazy {
        ModelSelection(activeModel, modelSettings, inferenceSettings, modelsDir, Dispatchers.IO)
    }

    private val conversationDatabase: ConversationDatabase by lazy {
        Room.databaseBuilder(context, ConversationDatabase::class.java, ConversationDatabase.NAME)
            .addMigrations(ConversationDatabase.MIGRATION_1_2)
            .build()
    }

    val conversations: ConversationStore by lazy { ConversationStore(conversationDatabase.conversations()) }

    private val policyDatabase: PolicyDatabase by lazy {
        Room.databaseBuilder(context, PolicyDatabase::class.java, PolicyDatabase.NAME).build()
    }

    val skillStates: SkillStateStore by lazy { SkillStateStore(policyDatabase.policy()) }

    val skills: SkillRegistry by lazy { SkillRegistry(AutomaticSkills.create(AndroidPhoneReaders(context))) }

    val runtime: BruceRuntime by lazy {
        val policy = PolicyEngine(skills, skillStates, ToolOutput(reservedMarkers = RESERVED_MARKERS), permissionGranted = { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        })
        BruceRuntime(engine, skills, skillStates, policy, temperature = { modelSelection.activeTemperature() })
    }

    val dataReset: DataReset by lazy {
        DataReset(activeModel, context.settingsDataStore, modelsDir, context.cacheDir, Dispatchers.IO) {
            conversations.deleteAll()
            skillStates.reset()
        }
    }

    val cpuFeatures: () -> CpuFeatures = CpuFeatures::detect

    fun memoryInfo(): ActivityManager.MemoryInfo =
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }

    companion object {
        const val MODELS_DIR = "models"

        /** Tool-call markup a skill result must not be able to imitate (ADR 0001 formats). */
        private val RESERVED_MARKERS = listOf("<tool_call>", "</tool_call>", "<tool_response>", "</tool_response>", "<|tool_call_start|>", "<|tool_call_end|>")
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as BruceApplication).container

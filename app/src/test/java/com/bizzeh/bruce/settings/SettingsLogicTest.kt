package com.bizzeh.bruce.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.HubAuth
import com.bizzeh.bruce.huggingface.SignInError
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.huggingface.TokenCipher
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.models.ActiveModel
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLogicTest {
    @TempDir
    lateinit var dir: File

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private inner class Fixture(scope: TestScope) {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(dir, "s.preferences_pb") }
        val modelsDir = File(dir, "models").apply { mkdirs() }
        val cacheDir = File(dir, "cache").apply { mkdirs() }
        val activeModel = ActiveModel(engine, modelsDir, dispatcher)
        val theme = ThemeSettingsRepository(dataStore)
        val inference = InferenceSettingsRepository(dataStore)
        var databasesCleared = 0
        val reset = DataReset(activeModel, dataStore, modelsDir, cacheDir, dispatcher) { databasesCleared++ }
        val network = NetworkSettingsRepository(dataStore)
        val hubAuth = HubAuth(NoNetwork, dataStore, PlainCipher, { true }, dispatcher, "client")
        val personality = PersonalitySettingsRepository(dataStore)
        val summary = SummarySettingsRepository(dataStore)
        val memory = com.bizzeh.bruce.memory.MemorySettingsRepository(dataStore)
        val viewModel = SettingsViewModel(theme, inference, network, personality, summary, memory, hubAuth, reset, dynamicColourSupported = true, performanceCores = 4, cores = 8)
    }

    @Test
    fun viewModelReflectsAndChangesSettings() = runTest(dispatcher) {
        val f = Fixture(this)

        f.viewModel.setThemeMode(ThemeMode.DARK)
        f.viewModel.setDynamicColour(true)
        f.viewModel.setBackend(BackendPreference.CPU)
        f.viewModel.setThreads(2)
        f.viewModel.setContextLength(16384)
        f.viewModel.setNetworkMode(NetworkMode.HUGGING_FACE)
        f.viewModel.setPersonality(Personality.MILO)
        f.viewModel.setMemoryMode(com.bizzeh.bruce.memory.MemoryMode.PER_MODEL)
        f.viewModel.setSummaryEnabled(true)
        f.viewModel.setSummaryThreshold(95)

        // DataStore writes on real I/O threads, so wait for the state rather than for the scheduler.
        val state = f.viewModel.state.first {
            it.inference.contextLength == 16384 && it.theme.dynamicColour && it.network == NetworkMode.HUGGING_FACE && it.personality == Personality.MILO &&
                it.summary == SummarySettings(enabled = true, threshold = 95) && it.memory == com.bizzeh.bruce.memory.MemoryMode.PER_MODEL
        }
        assertNull(state.account)
        assertNull(state.signInError)
        assertEquals(ThemeSettings(ThemeMode.DARK, dynamicColour = true), state.theme)
        assertEquals(InferenceDefaults(BackendPreference.CPU, 2, 16384), state.inference)
        assertTrue(state.dynamicColourSupported)
        assertEquals(4, state.performanceCores)
        assertEquals(8, state.cores)
    }

    @Test
    fun clearAllDataRemovesModelsChatsCacheAndSettingsAndUnloads() = runTest(dispatcher) {
        val f = Fixture(this)
        File(f.modelsDir, "m.gguf").writeText("x")
        File(f.modelsDir, ".download-abc.part").writeText("x")
        File(f.cacheDir, "tmp").mkdirs()
        engine.loadResult = LoadResult.Loaded(ModelInfo("m", 1, 1, 1), Backend.CPU)
        f.activeModel.load(File(f.modelsDir, "m.gguf"))
        f.theme.setMode(ThemeMode.DARK)

        f.viewModel.clearAllData()
        f.theme.settings.first { it == ThemeSettings() }
        advanceUntilIdle()

        assertTrue(f.modelsDir.listFiles()!!.isEmpty())
        assertTrue(f.cacheDir.listFiles()!!.isEmpty())
        assertEquals(1, engine.unloads)
        assertEquals(1, f.databasesCleared)
        assertNull(f.activeModel.state.value.active)
        assertEquals(ThemeSettings(), f.theme.settings.first())
    }

    @Test
    fun signOutAndRejectedRedirectReachTheState() = runTest(dispatcher) {
        val f = Fixture(this)

        f.viewModel.completeSignIn(mapOf("state" to "forged", "code" to "c"))
        f.viewModel.signOut()

        assertEquals(SignInError.UNEXPECTED_CALLBACK, f.viewModel.state.first { it.signInError != null }.signInError)
    }

    @Test
    fun threadChoicesAndLabels() {
        assertEquals(listOf(1, 2, 4, 8), SettingsText.threadChoices(8))
        assertEquals(listOf(1, 2, 4, 6), SettingsText.threadChoices(6))
        assertEquals(listOf(1), SettingsText.threadChoices(1))
        assertEquals(listOf(1, 2, 4, 8, 16), SettingsText.threadChoices(64))
        assertEquals("4K", SettingsText.contextLabel(4096))
        assertEquals("Vulkan", SettingsText.backendName("VULKAN"))
        assertEquals("OpenCL", SettingsText.backendName("OPENCL"))
        assertEquals("CPU", SettingsText.backendName("CPU"))
    }

    private object NoNetwork : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("offline test")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("offline test")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("offline test")
    }

    private object PlainCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(sealed: ByteArray) = sealed
    }
}

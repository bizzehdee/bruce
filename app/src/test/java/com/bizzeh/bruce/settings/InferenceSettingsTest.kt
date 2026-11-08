package com.bizzeh.bruce.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.LoadConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InferenceSettingsTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "s.preferences_pb") }

    @Test
    fun defaults() = runTest {
        assertEquals(InferenceDefaults(BackendPreference.AUTO, null, null), InferenceSettingsRepository(store()).defaults.first())
    }

    @Test
    fun storesEachSetting() = runTest {
        val repository = InferenceSettingsRepository(store())

        repository.setBackend(BackendPreference.CPU)
        repository.setThreads(2)
        repository.setContextLength(8192)
        assertEquals(InferenceDefaults(BackendPreference.CPU, 2, 8192), repository.defaults.first())

        repository.setThreads(null)
        assertEquals(null, repository.defaults.first().threads)

        repository.setContextLength(65536)
        assertEquals(65536, repository.defaults.first().contextLength)
        repository.setContextLength(null)
        assertEquals(null, repository.defaults.first().contextLength)

        assertEquals(false, repository.defaults.first().thinking)
        repository.setThinking(true)
        assertEquals(true, repository.defaults.first().thinking)
    }

    @Test
    fun maxWaitDefaultsToFiveMinutesAndStoresOneToTen() = runTest {
        val repository = InferenceSettingsRepository(store())
        assertEquals(5, repository.defaults.first().maxWaitMinutes)

        repository.setMaxWaitMinutes(10)
        assertEquals(10, repository.defaults.first().maxWaitMinutes)
        repository.setMaxWaitMinutes(1)
        assertEquals(1, repository.defaults.first().maxWaitMinutes)

        assertThrows<IllegalArgumentException> { repository.setMaxWaitMinutes(0) }
        assertThrows<IllegalArgumentException> { repository.setMaxWaitMinutes(11) }
    }

    @Test
    fun aStoredMaxWaitOutsideOneToTenFallsBack() = runTest {
        val dataStore = store()
        dataStore.edit { it[intPreferencesKey("max_wait_minutes")] = 99 }

        assertEquals(5, InferenceSettingsRepository(dataStore).defaults.first().maxWaitMinutes)
    }

    @Test
    fun contextChoicesRunFrom2kTo64k() {
        assertEquals(listOf("2K", "4K", "8K", "16K", "24K", "32K", "48K", "64K"), InferenceDefaults.CONTEXT_CHOICES.map(SettingsText::contextLabel))
    }

    @Test
    fun storedValuesOutsideTheAllowedRangeFallBack() = runTest {
        val dataStore = store()
        dataStore.edit {
            it[stringPreferencesKey("default_backend")] = "TPU"
            it[intPreferencesKey("default_threads")] = 999
            it[intPreferencesKey("default_context")] = 3000
        }

        assertEquals(InferenceDefaults(), InferenceSettingsRepository(dataStore).defaults.first())
    }

    @Test
    fun invalidValuesAreCallerBugs() = runTest {
        val repository = InferenceSettingsRepository(store())
        assertThrows<IllegalArgumentException> { repository.setThreads(0) }
        assertThrows<IllegalArgumentException> { repository.setContextLength(1000) }
    }

    @Test
    fun storesTemperatureTopKTopPAndRepeatPenalty() = runTest {
        val repository = InferenceSettingsRepository(store())
        assertEquals(null, repository.defaults.first().temperature)
        assertEquals(null, repository.defaults.first().topK)
        assertEquals(null, repository.defaults.first().topP)
        assertEquals(null, repository.defaults.first().repeatPenalty)

        repository.setTemperature(0.3f)
        repository.setTopK(80)
        repository.setTopP(0.9f)
        repository.setRepeatPenalty(1.2f)
        assertEquals(0.3f, repository.defaults.first().temperature)
        assertEquals(80, repository.defaults.first().topK)
        assertEquals(0.9f, repository.defaults.first().topP)
        assertEquals(1.2f, repository.defaults.first().repeatPenalty)

        repository.setTemperature(null)
        repository.setTopK(null)
        repository.setTopP(null)
        repository.setRepeatPenalty(null)
        assertEquals(null, repository.defaults.first().temperature)
        assertEquals(null, repository.defaults.first().topK)
        assertEquals(null, repository.defaults.first().topP)
        assertEquals(null, repository.defaults.first().repeatPenalty)

        assertThrows<IllegalArgumentException> { repository.setTemperature(-0.1f) }
        assertThrows<IllegalArgumentException> { repository.setTemperature(2.1f) }
        assertThrows<IllegalArgumentException> { repository.setTopK(-1) }
        assertThrows<IllegalArgumentException> { repository.setTopK(101) }
        assertThrows<IllegalArgumentException> { repository.setTopP(-0.1f) }
        assertThrows<IllegalArgumentException> { repository.setTopP(1.1f) }
        assertThrows<IllegalArgumentException> { repository.setRepeatPenalty(0.4f) }
        assertThrows<IllegalArgumentException> { repository.setRepeatPenalty(2.1f) }
    }

    @Test
    fun aStoredSamplingValueOutsideTheRangeFallsBack() = runTest {
        val dataStore = store()
        dataStore.edit {
            it[androidx.datastore.preferences.core.floatPreferencesKey("default_temperature")] = -0.5f
            it[intPreferencesKey("default_top_k")] = -10
            it[androidx.datastore.preferences.core.floatPreferencesKey("default_top_p")] = 1.5f
            it[androidx.datastore.preferences.core.floatPreferencesKey("default_repeat_penalty")] = 5f
        }

        val defaults = InferenceSettingsRepository(dataStore).defaults.first()
        assertEquals(null, defaults.temperature)
        assertEquals(null, defaults.topK)
        assertEquals(null, defaults.topP)
        assertEquals(null, defaults.repeatPenalty)
    }

    @Test
    fun sharedSamplingStepperBoundsComeFromGenerationRequest() {
        assertEquals(0f, GenerationRequest.MIN_TEMPERATURE)
        assertEquals(2f, GenerationRequest.MAX_TEMPERATURE)
        assertEquals(40, GenerationRequest.DEFAULT_TOP_K)
    }

    @Test
    fun loadConfigUsesAutomaticThreadsWhenUnset() {
        assertEquals(LoadConfig(contextLength = 8192, backend = BackendPreference.VULKAN), InferenceDefaults(BackendPreference.VULKAN, null, 8192).toLoadConfig(8192))
        assertEquals(LoadConfig(contextLength = 2048, threads = 3), InferenceDefaults(threads = 3, contextLength = 2048).toLoadConfig(2048))
    }
}

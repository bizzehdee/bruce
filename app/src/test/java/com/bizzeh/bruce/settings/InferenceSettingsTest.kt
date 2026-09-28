package com.bizzeh.bruce.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.inference.BackendPreference
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
        assertEquals(InferenceDefaults(BackendPreference.AUTO, null, 4096), InferenceSettingsRepository(store()).defaults.first())
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
    fun loadConfigUsesAutomaticThreadsWhenUnset() {
        assertEquals(LoadConfig(contextLength = 8192, backend = BackendPreference.VULKAN), InferenceDefaults(BackendPreference.VULKAN, null, 8192).toLoadConfig())
        assertEquals(LoadConfig(contextLength = 2048, threads = 3), InferenceDefaults(threads = 3, contextLength = 2048).toLoadConfig())
    }
}

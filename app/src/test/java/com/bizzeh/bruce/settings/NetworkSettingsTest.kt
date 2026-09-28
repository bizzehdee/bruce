package com.bizzeh.bruce.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NetworkSettingsTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "n.preferences_pb") }

    @Test
    fun freshInstallIsOffline() = runTest {
        val repository = NetworkSettingsRepository(store())

        assertEquals(NetworkMode.OFFLINE, repository.mode.first())
        assertFalse(repository.huggingFaceAllowed())
    }

    @Test
    fun everyModeButOfflineAllowsHuggingFace() = runTest {
        val repository = NetworkSettingsRepository(store())
        for (mode in NetworkMode.entries) {
            repository.setMode(mode)
            assertEquals(mode != NetworkMode.OFFLINE, repository.huggingFaceAllowed(), mode.name)
        }
    }

    @Test
    fun unknownStoredModeFallsBackToOffline() = runTest {
        val dataStore = store()
        dataStore.edit { it[stringPreferencesKey("network_mode")] = "EVERYWHERE" }

        assertTrue(NetworkSettingsRepository(dataStore).mode.first() == NetworkMode.OFFLINE)
    }
}

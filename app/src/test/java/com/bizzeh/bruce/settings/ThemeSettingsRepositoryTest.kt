package com.bizzeh.bruce.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ThemeSettingsRepositoryTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(dir, "settings.preferences_pb")
    }

    @Test
    fun defaultsToSystemWithoutDynamicColour() = runTest {
        assertEquals(ThemeSettings(ThemeMode.SYSTEM, dynamicColour = false), ThemeSettingsRepository(store()).settings.first())
    }

    @Test
    fun storesModeAndDynamicColour() = runTest {
        val repository = ThemeSettingsRepository(store())

        repository.setMode(ThemeMode.DARK)
        repository.setDynamicColour(true)

        assertEquals(ThemeSettings(ThemeMode.DARK, dynamicColour = true), repository.settings.first())
    }

    @Test
    fun contextTrafficLightsAreOnUntilTurnedOff() = runTest {
        val repository = ThemeSettingsRepository(store())
        assertEquals(true, repository.settings.first().contextTrafficLights)

        repository.setContextTrafficLights(false)

        assertEquals(false, repository.settings.first().contextTrafficLights)
    }

    @Test
    fun unknownStoredModeFallsBackToSystem() = runTest {
        val dataStore = store()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }

        assertEquals(ThemeMode.SYSTEM, ThemeSettingsRepository(dataStore).settings.first().mode)
    }
}

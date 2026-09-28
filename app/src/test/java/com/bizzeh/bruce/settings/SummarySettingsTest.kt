package com.bizzeh.bruce.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SummarySettingsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dataStore by lazy { PreferenceDataStoreFactory.create { temp.newFile("settings.preferences_pb").also { it.delete() } } }
    private val repository by lazy { SummarySettingsRepository(dataStore) }

    @Test
    fun offAtNinetyPercentUntilChanged() = runBlocking {
        assertEquals(SummarySettings(enabled = false, threshold = 90), repository.settings.first())

        repository.setEnabled(true)
        repository.setThreshold(85)

        assertEquals(SummarySettings(enabled = true, threshold = 85), repository.settings.first())
    }

    @Test
    fun onlyTheOfferedThresholdsAreKept() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setThreshold(50) } }
        dataStore.edit { it[intPreferencesKey("summarise_threshold")] = 42 }

        assertEquals(90, repository.settings.first().threshold)
    }
}

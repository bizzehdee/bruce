package com.bizzeh.bruce.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Auto-summarise (plan.md, Context): off by default. When on, a chat whose prompt reaches
 * [threshold] percent of what fits is summarised: the oldest messages are replaced, in what the
 * model sees, by a summary shown in the chat.
 */
data class SummarySettings(val enabled: Boolean = false, val threshold: Int = DEFAULT_THRESHOLD) {
    companion object {
        val THRESHOLDS = listOf(85, 90, 95, 100)
        const val DEFAULT_THRESHOLD = 90
    }
}

class SummarySettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<SummarySettings> = dataStore.data.map { preferences ->
        SummarySettings(
            enabled = preferences[ENABLED] ?: false,
            threshold = preferences[THRESHOLD]?.takeIf { it in SummarySettings.THRESHOLDS } ?: SummarySettings.DEFAULT_THRESHOLD,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setThreshold(threshold: Int) {
        require(threshold in SummarySettings.THRESHOLDS) { "unsupported threshold" }
        dataStore.edit { it[THRESHOLD] = threshold }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("summarise_enabled")
        val THRESHOLD = intPreferencesKey("summarise_threshold")
    }
}

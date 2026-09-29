package com.bizzeh.bruce.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Wallpaper-based colour; only takes effect on Android 12 and later. */
    val dynamicColour: Boolean = false,
    /** The chat's context bar is green, amber or red by how full the chat is (TASK-064). */
    val contextTrafficLights: Boolean = true,
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemeSettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            mode = preferences[MODE]?.let(::parseMode) ?: ThemeMode.SYSTEM,
            dynamicColour = preferences[DYNAMIC_COLOUR] ?: false,
            contextTrafficLights = preferences[CONTEXT_TRAFFIC_LIGHTS] ?: true,
        )
    }

    suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    suspend fun setDynamicColour(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOUR] = enabled }
    }

    suspend fun setContextTrafficLights(enabled: Boolean) {
        dataStore.edit { it[CONTEXT_TRAFFIC_LIGHTS] = enabled }
    }

    /** Stored text may come from an older or newer app version; unknown values fall back to SYSTEM. */
    private fun parseMode(stored: String): ThemeMode? = ThemeMode.entries.firstOrNull { it.name == stored }

    private companion object {
        val MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
        val CONTEXT_TRAFFIC_LIGHTS = booleanPreferencesKey("context_traffic_lights")
    }
}

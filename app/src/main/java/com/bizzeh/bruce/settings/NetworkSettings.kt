package com.bizzeh.bruce.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How much of the network Bruce may use. Every network request asks this first. */
enum class NetworkMode {
    /** Nothing leaves the phone. The default on a fresh install. */
    OFFLINE,
    /** Model search, sign-in and downloads from Hugging Face only. */
    HUGGING_FACE,
    /** Hugging Face plus domains the user approves, once skills that use the web exist. */
    APPROVED_DOMAINS,
    /** Any site, once skills that use the web exist. */
    GENERAL;

    val allowsHuggingFace: Boolean get() = this != OFFLINE
}

class NetworkSettingsRepository(private val dataStore: DataStore<Preferences>) {
    /** An unknown stored value falls back to OFFLINE, the safest mode. */
    val mode: Flow<NetworkMode> = dataStore.data.map { preferences ->
        NetworkMode.entries.firstOrNull { it.name == preferences[MODE] } ?: NetworkMode.OFFLINE
    }

    suspend fun huggingFaceAllowed(): Boolean = mode.first().allowsHuggingFace

    suspend fun setMode(mode: NetworkMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    private companion object {
        val MODE = stringPreferencesKey("network_mode")
    }
}

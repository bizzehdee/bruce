package com.bizzeh.bruce.settings

import androidx.annotation.RawRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The sidekick's personality (plan.md, Personalities). The rules are the owner's text, kept as raw
 * resources so they are edited as prose, not code. [displayName] is how the chat names the sidekick.
 */
enum class Personality(val displayName: String, @RawRes val rules: Int) {
    BRUCE("Bruce", R.raw.personality_bruce),
    MILO("Milo", R.raw.personality_milo),
}

class PersonalitySettingsRepository(private val dataStore: DataStore<Preferences>) {
    /** Bruce unless the user chose otherwise; a value from another version falls back to Bruce. */
    val personality: Flow<Personality> = dataStore.data.map { preferences ->
        Personality.entries.firstOrNull { it.name == preferences[PERSONALITY] } ?: Personality.BRUCE
    }

    suspend fun set(personality: Personality) {
        dataStore.edit { it[PERSONALITY] = personality.name }
    }

    private companion object {
        val PERSONALITY = stringPreferencesKey("personality")
    }
}

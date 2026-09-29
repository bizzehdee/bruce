package com.bizzeh.bruce.skills.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.huggingface.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

/**
 * The user's own Brave Search key (TASK-070), sealed with the Android Keystore as the Hugging Face
 * token is. Bruce ships no key; this one is never logged, shown again or sent anywhere but Brave.
 */
class SearchKeyRepository(private val dataStore: DataStore<Preferences>, private val cipher: TokenCipher) {
    val saved: Flow<Boolean> = dataStore.data.map { it[KEY] != null }

    suspend fun key(): String? {
        val sealed = dataStore.data.first()[KEY]?.let { Base64.getDecoder().decode(it) } ?: return null
        return cipher.decrypt(sealed)?.toString(Charsets.UTF_8)
    }

    /** False when [key] does not look like an API key (printable, no spaces, 8 to 256 characters). */
    suspend fun save(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.length !in 8..256 || trimmed.any { it.code !in 0x21..0x7E }) return false
        val sealed = Base64.getEncoder().encodeToString(cipher.encrypt(trimmed.toByteArray(Charsets.UTF_8)))
        dataStore.edit { it[KEY] = sealed }
        return true
    }

    suspend fun remove() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("brave_search_key_sealed")
    }
}

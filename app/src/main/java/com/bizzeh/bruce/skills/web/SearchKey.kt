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

/** The services the web search skill can use (TASK-113): LangSearch is free with a free key; Brave needs a payment card. */
enum class WebSearchProvider(internal val keyName: String) {
    LANGSEARCH("langsearch_search_key_sealed"),
    BRAVE("brave_search_key_sealed"),
}

/**
 * The web search provider and the user's own key for each (TASK-070, TASK-113), sealed with the
 * Android Keystore as the Hugging Face token is. Bruce ships no key; a key is never logged, shown
 * again or sent anywhere but its own provider. [saved], [key], [save] and [remove] act on the chosen
 * provider's key.
 */
class SearchKeyRepository(private val dataStore: DataStore<Preferences>, private val cipher: TokenCipher) {
    val provider: Flow<WebSearchProvider> = dataStore.data.map { preferences ->
        WebSearchProvider.entries.firstOrNull { it.name == preferences[PROVIDER] } ?: WebSearchProvider.LANGSEARCH
    }

    val saved: Flow<Boolean> = dataStore.data.map { preferences ->
        val chosen = WebSearchProvider.entries.firstOrNull { it.name == preferences[PROVIDER] } ?: WebSearchProvider.LANGSEARCH
        preferences[keyOf(chosen)] != null
    }

    suspend fun setProvider(provider: WebSearchProvider) {
        dataStore.edit { it[PROVIDER] = provider.name }
    }

    suspend fun key(): String? = key(provider.first())

    suspend fun key(provider: WebSearchProvider): String? {
        val sealed = dataStore.data.first()[keyOf(provider)]?.let { Base64.getDecoder().decode(it) } ?: return null
        return cipher.decrypt(sealed)?.toString(Charsets.UTF_8)
    }

    /** False when [key] does not look like an API key (printable, no spaces, 8 to 256 characters). */
    suspend fun save(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.length !in 8..256 || trimmed.any { it.code !in 0x21..0x7E }) return false
        val sealed = Base64.getEncoder().encodeToString(cipher.encrypt(trimmed.toByteArray(Charsets.UTF_8)))
        val chosen = provider.first()
        dataStore.edit { it[keyOf(chosen)] = sealed }
        return true
    }

    suspend fun remove() {
        val chosen = provider.first()
        dataStore.edit { it.remove(keyOf(chosen)) }
    }

    private companion object {
        val PROVIDER = stringPreferencesKey("web_search_provider")

        fun keyOf(provider: WebSearchProvider) = stringPreferencesKey(provider.keyName)
    }
}

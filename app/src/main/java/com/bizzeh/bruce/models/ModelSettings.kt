package com.bizzeh.bruce.models

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.settings.InferenceDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Per-model choices; null means "use the default from Settings". */
data class ModelOverrides(
    val backend: BackendPreference? = null,
    val threads: Int? = null,
    val contextLength: Int? = null,
    val temperature: Float? = null,
) {
    /** [automatic] is the size to use when neither this model nor the defaults name one. */
    fun loadConfig(defaults: InferenceDefaults, automatic: () -> Int): LoadConfig = defaults.copy(
        backend = backend ?: defaults.backend,
        threads = threads ?: defaults.threads,
    ).toLoadConfig(contextLength ?: defaults.contextLength ?: automatic())

    fun temperature(): Float = temperature ?: DEFAULT_TEMPERATURE

    companion object {
        const val DEFAULT_TEMPERATURE = com.bizzeh.bruce.inference.GenerationRequest.DEFAULT_TEMPERATURE
        val TEMPERATURE_CHOICES = listOf(0f, 0.3f, 0.7f, 1.0f)
    }
}

/** A tool-capable chat template fetched from another Hub copy of the same model (TASK-063); [source] is that repository. */
data class FetchedTemplate(val text: String, val source: String)

/**
 * Per-model settings and which model was last chosen, keyed by model file name. Stored values
 * are validated on read; anything unknown or out of range means "use the default".
 */
class ModelSettingsRepository(private val dataStore: DataStore<Preferences>) {
    val activeModelName: Flow<String?> = dataStore.data.map { it[ACTIVE] }

    fun overrides(fileName: String): Flow<ModelOverrides> = dataStore.data.map { preferences ->
        ModelOverrides(
            backend = BackendPreference.entries.firstOrNull { it.name == preferences[backendKey(fileName)] },
            threads = preferences[threadsKey(fileName)]?.takeIf { it in 1..InferenceDefaults.MAX_THREADS },
            contextLength = preferences[contextKey(fileName)]?.takeIf { it in InferenceDefaults.CONTEXT_CHOICES },
            temperature = preferences[temperatureKey(fileName)]?.takeIf { it in ModelOverrides.TEMPERATURE_CHOICES },
        )
    }

    suspend fun overridesNow(fileName: String): ModelOverrides = overrides(fileName).first()

    /** Kept apart from [ModelOverrides], which the settings chips rewrite whole. */
    fun template(fileName: String): Flow<FetchedTemplate?> = dataStore.data.map { preferences ->
        val text = preferences[templateKey(fileName)]
        val source = preferences[templateSourceKey(fileName)]
        if (text != null && source != null) FetchedTemplate(text, source) else null
    }

    suspend fun setTemplate(fileName: String, template: FetchedTemplate?) {
        dataStore.edit {
            it.setOrRemove(templateKey(fileName), template?.text)
            it.setOrRemove(templateSourceKey(fileName), template?.source)
        }
    }

    suspend fun setActiveModel(fileName: String?) {
        dataStore.edit { if (fileName == null) it.remove(ACTIVE) else it[ACTIVE] = fileName }
    }

    suspend fun setOverrides(fileName: String, overrides: ModelOverrides) {
        require(overrides.threads == null || overrides.threads in 1..InferenceDefaults.MAX_THREADS) { "threads out of range" }
        require(overrides.contextLength == null || overrides.contextLength in InferenceDefaults.CONTEXT_CHOICES) { "unsupported context" }
        require(overrides.temperature == null || overrides.temperature in ModelOverrides.TEMPERATURE_CHOICES) { "unsupported temperature" }
        dataStore.edit {
            it.setOrRemove(backendKey(fileName), overrides.backend?.name)
            it.setOrRemove(threadsKey(fileName), overrides.threads)
            it.setOrRemove(contextKey(fileName), overrides.contextLength)
            it.setOrRemove(temperatureKey(fileName), overrides.temperature)
        }
    }

    /** Forgets a deleted model: its overrides, and the active choice if it was this model. */
    suspend fun forget(fileName: String) {
        dataStore.edit {
            it.remove(backendKey(fileName))
            it.remove(threadsKey(fileName))
            it.remove(contextKey(fileName))
            it.remove(temperatureKey(fileName))
            it.remove(templateKey(fileName))
            it.remove(templateSourceKey(fileName))
            if (it[ACTIVE] == fileName) it.remove(ACTIVE)
        }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val ACTIVE = stringPreferencesKey("active_model")
        fun backendKey(name: String) = stringPreferencesKey("model.$name.backend")
        fun threadsKey(name: String) = intPreferencesKey("model.$name.threads")
        fun contextKey(name: String) = intPreferencesKey("model.$name.context")
        fun temperatureKey(name: String) = floatPreferencesKey("model.$name.temperature")
        fun templateKey(name: String) = stringPreferencesKey("model.$name.chat_template")
        fun templateSourceKey(name: String) = stringPreferencesKey("model.$name.chat_template_source")
    }
}

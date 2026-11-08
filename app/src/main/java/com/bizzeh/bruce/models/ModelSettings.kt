package com.bizzeh.bruce.models

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.GenerationRequest
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
    val topK: Int? = null,
    val topP: Float? = null,
    /** Meaningless for a LiteRT-LM model, which has no repeat-penalty sampler (TASK-124). */
    val repeatPenalty: Float? = null,
    /** Null follows the inference default (TASK-110). */
    val thinking: Boolean? = null,
    /**
     * LiteRT-LM's backend choice (TASK-123 follow-up); meaningless for GGUF. Null is automatic
     * (GPU only for a device-verified file, TASK-119's allowlist, CPU otherwise); true forces a
     * GPU attempt (only offered for a verified file); false forces CPU.
     */
    val liteRtPreferGpu: Boolean? = null,
) {
    /** [automatic] is the size to use when neither this model nor the defaults name one. */
    fun loadConfig(defaults: InferenceDefaults, automatic: () -> Int): LoadConfig = defaults.copy(
        backend = backend ?: defaults.backend,
        threads = threads ?: defaults.threads,
    ).toLoadConfig(contextLength ?: defaults.contextLength ?: automatic())

    /**
     * Bug found and fixed 2026-11-07 (TASK-124): this used to ignore [defaults] entirely, so no
     * global temperature setting could ever take effect — only this model's own override or the
     * hardcoded fallback. Same bug applied to topK/topP/repeatPenalty below before they existed.
     */
    fun temperature(defaults: InferenceDefaults): Float = temperature ?: defaults.temperature ?: GenerationRequest.DEFAULT_TEMPERATURE

    fun topK(defaults: InferenceDefaults): Int = topK ?: defaults.topK ?: GenerationRequest.DEFAULT_TOP_K

    fun topP(defaults: InferenceDefaults): Float = topP ?: defaults.topP ?: GenerationRequest.DEFAULT_TOP_P

    fun repeatPenalty(defaults: InferenceDefaults): Float = repeatPenalty ?: defaults.repeatPenalty ?: GenerationRequest.DEFAULT_REPEAT_PENALTY

    fun thinking(defaults: InferenceDefaults): Boolean = thinking ?: defaults.thinking

    companion object {
        const val DEFAULT_TEMPERATURE = GenerationRequest.DEFAULT_TEMPERATURE
        val TEMPERATURE_CHOICES = GenerationRequest.TEMPERATURE_CHOICES
        val TOP_K_CHOICES = GenerationRequest.TOP_K_CHOICES
        val TOP_P_CHOICES = GenerationRequest.TOP_P_CHOICES
        val REPEAT_PENALTY_CHOICES = GenerationRequest.REPEAT_PENALTY_CHOICES
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
            topK = preferences[topKKey(fileName)]?.takeIf { it in ModelOverrides.TOP_K_CHOICES },
            topP = preferences[topPKey(fileName)]?.takeIf { it in ModelOverrides.TOP_P_CHOICES },
            repeatPenalty = preferences[repeatPenaltyKey(fileName)]?.takeIf { it in ModelOverrides.REPEAT_PENALTY_CHOICES },
            thinking = preferences[thinkingKey(fileName)],
            liteRtPreferGpu = preferences[liteRtPreferGpuKey(fileName)],
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
        dataStore.edit {
            if (fileName == null) it.remove(ACTIVE) else it[ACTIVE] = fileName
            it.remove(ACTIVE_REMOTE)
        }
    }

    /** The server model chosen last (TASK-077), which wins over [activeModelName] while it is set. */
    val activeRemote: Flow<Pair<String, String>?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_REMOTE]?.split('\n', limit = 2)?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }

    suspend fun setActiveRemote(serverId: String, model: String) {
        dataStore.edit { it[ACTIVE_REMOTE] = "$serverId\n$model" }
    }

    suspend fun setOverrides(fileName: String, overrides: ModelOverrides) {
        require(overrides.threads == null || overrides.threads in 1..InferenceDefaults.MAX_THREADS) { "threads out of range" }
        require(overrides.contextLength == null || overrides.contextLength in InferenceDefaults.CONTEXT_CHOICES) { "unsupported context" }
        require(overrides.temperature == null || overrides.temperature in ModelOverrides.TEMPERATURE_CHOICES) { "unsupported temperature" }
        require(overrides.topK == null || overrides.topK in ModelOverrides.TOP_K_CHOICES) { "unsupported topK" }
        require(overrides.topP == null || overrides.topP in ModelOverrides.TOP_P_CHOICES) { "unsupported topP" }
        require(overrides.repeatPenalty == null || overrides.repeatPenalty in ModelOverrides.REPEAT_PENALTY_CHOICES) { "unsupported repeatPenalty" }
        dataStore.edit {
            it.setOrRemove(backendKey(fileName), overrides.backend?.name)
            it.setOrRemove(threadsKey(fileName), overrides.threads)
            it.setOrRemove(contextKey(fileName), overrides.contextLength)
            it.setOrRemove(temperatureKey(fileName), overrides.temperature)
            it.setOrRemove(topKKey(fileName), overrides.topK)
            it.setOrRemove(topPKey(fileName), overrides.topP)
            it.setOrRemove(repeatPenaltyKey(fileName), overrides.repeatPenalty)
            it.setOrRemove(thinkingKey(fileName), overrides.thinking)
            it.setOrRemove(liteRtPreferGpuKey(fileName), overrides.liteRtPreferGpu)
        }
    }

    /** Forgets a deleted model: its overrides, and the active choice if it was this model. */
    suspend fun forget(fileName: String) {
        dataStore.edit {
            it.remove(backendKey(fileName))
            it.remove(threadsKey(fileName))
            it.remove(contextKey(fileName))
            it.remove(temperatureKey(fileName))
            it.remove(topKKey(fileName))
            it.remove(topPKey(fileName))
            it.remove(repeatPenaltyKey(fileName))
            it.remove(thinkingKey(fileName))
            it.remove(liteRtPreferGpuKey(fileName))
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
        val ACTIVE_REMOTE = stringPreferencesKey("active_remote_model")
        fun backendKey(name: String) = stringPreferencesKey("model.$name.backend")
        fun threadsKey(name: String) = intPreferencesKey("model.$name.threads")
        fun contextKey(name: String) = intPreferencesKey("model.$name.context")
        fun temperatureKey(name: String) = floatPreferencesKey("model.$name.temperature")
        fun topKKey(name: String) = intPreferencesKey("model.$name.top_k")
        fun topPKey(name: String) = floatPreferencesKey("model.$name.top_p")
        fun repeatPenaltyKey(name: String) = floatPreferencesKey("model.$name.repeat_penalty")
        fun thinkingKey(name: String) = booleanPreferencesKey("model.$name.thinking")
        fun liteRtPreferGpuKey(name: String) = booleanPreferencesKey("model.$name.litert_prefer_gpu")
        fun templateKey(name: String) = stringPreferencesKey("model.$name.chat_template")
        fun templateSourceKey(name: String) = stringPreferencesKey("model.$name.chat_template_source")
    }
}

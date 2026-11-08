package com.bizzeh.bruce.settings

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Defaults applied whenever a model is loaded. [threads] null means CpuTopology.defaultThreads;
 * [contextLength] null means automatic, picked for each model and phone (AutoContext).
 */
data class InferenceDefaults(
    val backend: BackendPreference = BackendPreference.AUTO,
    val threads: Int? = null,
    val contextLength: Int? = null,
    /** Models that can reason before answering do so (TASK-110); off by default, as it costs seconds per reply. */
    val thinking: Boolean = false,
    /** The longest a turn may take, in minutes (TASK-115). */
    val maxWaitMinutes: Int = DEFAULT_WAIT_MINUTES,
    /** Null means [com.bizzeh.bruce.inference.GenerationRequest.DEFAULT_TEMPERATURE]; a model's own override wins over this. */
    val temperature: Float? = null,
    /** Null means [com.bizzeh.bruce.inference.GenerationRequest.DEFAULT_TOP_K]; a model's own override wins over this. */
    val topK: Int? = null,
    /** Null means [com.bizzeh.bruce.inference.GenerationRequest.DEFAULT_TOP_P]; a model's own override wins over this. */
    val topP: Float? = null,
    /** Null means [com.bizzeh.bruce.inference.GenerationRequest.DEFAULT_REPEAT_PENALTY]; a model's own override wins over this. Meaningless for a LiteRT-LM model, which has no repeat-penalty sampler. */
    val repeatPenalty: Float? = null,
) {
    /** [contextLength] is the size to load with: this default's, a model's own, or the automatic one. */
    fun toLoadConfig(contextLength: Int): LoadConfig = if (threads == null) {
        LoadConfig(contextLength = contextLength, backend = backend)
    } else {
        LoadConfig(contextLength = contextLength, threads = threads, backend = backend)
    }

    companion object {
        /**
         * Automatic's size when a file does not declare enough to estimate its context's memory, and
         * the size the model browser judges downloads at: room for a chat, about 900 MB in all for
         * Qwen3 0.6B, which fits a 4 GB phone.
         */
        const val FALLBACK_CONTEXT = 4096
        val CONTEXT_CHOICES = listOf(2048, 4096, 8192, 16384, 24576, 32768, 49152, 65536)
        const val MAX_THREADS = 16
        val WAIT_MINUTES_CHOICES = 1..10
        const val DEFAULT_WAIT_MINUTES = 5
    }
}

class InferenceSettingsRepository(private val dataStore: DataStore<Preferences>) {
    /** Stored values are validated: anything unknown or out of range falls back to the default. */
    val defaults: Flow<InferenceDefaults> = dataStore.data.map { preferences ->
        InferenceDefaults(
            backend = BackendPreference.entries.firstOrNull { it.name == preferences[BACKEND] } ?: BackendPreference.AUTO,
            threads = preferences[THREADS]?.takeIf { it in 1..InferenceDefaults.MAX_THREADS },
            contextLength = preferences[CONTEXT]?.takeIf { it in InferenceDefaults.CONTEXT_CHOICES },
            thinking = preferences[THINKING] ?: false,
            maxWaitMinutes = preferences[MAX_WAIT]?.takeIf { it in InferenceDefaults.WAIT_MINUTES_CHOICES } ?: InferenceDefaults.DEFAULT_WAIT_MINUTES,
            temperature = preferences[TEMPERATURE]?.takeIf { it in GenerationRequest.TEMPERATURE_CHOICES },
            topK = preferences[TOP_K]?.takeIf { it in GenerationRequest.TOP_K_CHOICES },
            topP = preferences[TOP_P]?.takeIf { it in GenerationRequest.TOP_P_CHOICES },
            repeatPenalty = preferences[REPEAT_PENALTY]?.takeIf { it in GenerationRequest.REPEAT_PENALTY_CHOICES },
        )
    }

    suspend fun setBackend(backend: BackendPreference) {
        dataStore.edit { it[BACKEND] = backend.name }
    }

    /** Null restores automatic (CpuTopology.defaultThreads). */
    suspend fun setThreads(threads: Int?) {
        require(threads == null || threads in 1..InferenceDefaults.MAX_THREADS) { "threads out of range: $threads" }
        dataStore.edit { if (threads == null) it.remove(THREADS) else it[THREADS] = threads }
    }

    /** Null restores automatic. */
    suspend fun setContextLength(contextLength: Int?) {
        require(contextLength == null || contextLength in InferenceDefaults.CONTEXT_CHOICES) { "unsupported context length: $contextLength" }
        dataStore.edit { if (contextLength == null) it.remove(CONTEXT) else it[CONTEXT] = contextLength }
    }

    suspend fun setThinking(enabled: Boolean) {
        dataStore.edit { it[THINKING] = enabled }
    }

    suspend fun setMaxWaitMinutes(minutes: Int) {
        require(minutes in InferenceDefaults.WAIT_MINUTES_CHOICES) { "wait out of range: $minutes" }
        dataStore.edit { it[MAX_WAIT] = minutes }
    }

    /** Null restores [GenerationRequest.DEFAULT_TEMPERATURE]. */
    suspend fun setTemperature(temperature: Float?) {
        require(temperature == null || temperature in GenerationRequest.TEMPERATURE_CHOICES) { "unsupported temperature: $temperature" }
        dataStore.edit { if (temperature == null) it.remove(TEMPERATURE) else it[TEMPERATURE] = temperature }
    }

    /** Null restores [GenerationRequest.DEFAULT_TOP_K]. */
    suspend fun setTopK(topK: Int?) {
        require(topK == null || topK in GenerationRequest.TOP_K_CHOICES) { "unsupported topK: $topK" }
        dataStore.edit { if (topK == null) it.remove(TOP_K) else it[TOP_K] = topK }
    }

    /** Null restores [GenerationRequest.DEFAULT_TOP_P]. */
    suspend fun setTopP(topP: Float?) {
        require(topP == null || topP in GenerationRequest.TOP_P_CHOICES) { "unsupported topP: $topP" }
        dataStore.edit { if (topP == null) it.remove(TOP_P) else it[TOP_P] = topP }
    }

    /** Null restores [GenerationRequest.DEFAULT_REPEAT_PENALTY]. Meaningless for a LiteRT-LM model. */
    suspend fun setRepeatPenalty(repeatPenalty: Float?) {
        require(repeatPenalty == null || repeatPenalty in GenerationRequest.REPEAT_PENALTY_CHOICES) { "unsupported repeatPenalty: $repeatPenalty" }
        dataStore.edit { if (repeatPenalty == null) it.remove(REPEAT_PENALTY) else it[REPEAT_PENALTY] = repeatPenalty }
    }

    private companion object {
        val MAX_WAIT = intPreferencesKey("max_wait_minutes")
        val TEMPERATURE = floatPreferencesKey("default_temperature")
        val TOP_K = intPreferencesKey("default_top_k")
        val TOP_P = floatPreferencesKey("default_top_p")
        val REPEAT_PENALTY = floatPreferencesKey("default_repeat_penalty")
        val THINKING = booleanPreferencesKey("default_thinking")
        val BACKEND = stringPreferencesKey("default_backend")
        val THREADS = intPreferencesKey("default_threads")
        val CONTEXT = intPreferencesKey("default_context")
    }
}

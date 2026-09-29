package com.bizzeh.bruce.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.huggingface.HubAuth
import com.bizzeh.bruce.huggingface.SignInError
import com.bizzeh.bruce.huggingface.SignInResult
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.memory.MemoryMode
import com.bizzeh.bruce.memory.MemorySettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val theme: ThemeSettingsRepository,
    private val inference: InferenceSettingsRepository,
    private val network: NetworkSettingsRepository,
    private val personality: PersonalitySettingsRepository,
    private val summary: SummarySettingsRepository,
    private val memory: MemorySettingsRepository,
    private val hubAuth: HubAuth,
    private val dataReset: DataReset,
    dynamicColourSupported: Boolean,
    performanceCores: Int,
    cores: Int,
) : ViewModel() {
    private val initial = SettingsState(dynamicColourSupported = dynamicColourSupported, performanceCores = performanceCores, cores = cores)

    private val signInError = MutableStateFlow<SignInError?>(null)

    val state: StateFlow<SettingsState> = combine(
        combine(theme.settings, inference.defaults, network.mode, ::Triple),
        combine(hubAuth.account, signInError, ::Pair), personality.personality, summary.settings, memory.mode,
    ) { (themeSettings, defaults, mode), (account, error), chosen, summarising, remembering ->
        initial.copy(
            theme = themeSettings, inference = defaults, network = mode, account = account, signInError = error,
            personality = chosen, summary = summarising, memory = remembering,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    fun setPersonality(chosen: Personality) = launch { personality.set(chosen) }

    fun setMemoryMode(mode: MemoryMode) = launch { memory.setMode(mode) }

    fun setSummaryEnabled(enabled: Boolean) = launch { summary.setEnabled(enabled) }

    fun setSummaryThreshold(threshold: Int) = launch { summary.setThreshold(threshold) }

    fun setNetworkMode(mode: NetworkMode) = launch { network.setMode(mode) }

    /** Starts sign-in; the caller opens the returned URL in the browser. */
    fun beginSignIn(): String {
        signInError.value = null
        return hubAuth.begin().authorizeUrl
    }

    /** The browser redirect back into the app. */
    fun completeSignIn(parameters: Map<String, String?>) = launch {
        signInError.value = (hubAuth.complete(parameters) as? SignInResult.Failed)?.error
    }

    fun signOut() = launch { hubAuth.signOut() }

    fun setThemeMode(mode: ThemeMode) = launch { theme.setMode(mode) }

    fun setDynamicColour(enabled: Boolean) = launch { theme.setDynamicColour(enabled) }

    fun setContextTrafficLights(enabled: Boolean) = launch { theme.setContextTrafficLights(enabled) }

    fun setBackend(backend: BackendPreference) = launch { inference.setBackend(backend) }

    fun setThreads(threads: Int?) = launch { inference.setThreads(threads) }

    fun setContextLength(contextLength: Int) = launch { inference.setContextLength(contextLength) }

    fun clearAllData() = launch { dataReset.clearAll() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

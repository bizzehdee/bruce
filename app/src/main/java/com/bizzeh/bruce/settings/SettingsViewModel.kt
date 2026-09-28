package com.bizzeh.bruce.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.inference.BackendPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val theme: ThemeSettingsRepository,
    private val inference: InferenceSettingsRepository,
    private val dataReset: DataReset,
    dynamicColourSupported: Boolean,
    performanceCores: Int,
    cores: Int,
) : ViewModel() {
    private val initial = SettingsState(dynamicColourSupported = dynamicColourSupported, performanceCores = performanceCores, cores = cores)

    val state: StateFlow<SettingsState> = combine(theme.settings, inference.defaults) { themeSettings, defaults ->
        initial.copy(theme = themeSettings, inference = defaults)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    fun setThemeMode(mode: ThemeMode) = launch { theme.setMode(mode) }

    fun setDynamicColour(enabled: Boolean) = launch { theme.setDynamicColour(enabled) }

    fun setBackend(backend: BackendPreference) = launch { inference.setBackend(backend) }

    fun setThreads(threads: Int?) = launch { inference.setThreads(threads) }

    fun setContextLength(contextLength: Int) = launch { inference.setContextLength(contextLength) }

    fun clearAllData() = launch { dataReset.clearAll() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

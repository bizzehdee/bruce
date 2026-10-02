package com.bizzeh.bruce.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.models.SuggestedModel
import com.bizzeh.bruce.settings.InferenceSettingsRepository
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.settings.NetworkSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the first-launch wizard has run. Clearing all data clears this too, so the wizard runs again. */
class SetupSettingsRepository(private val dataStore: DataStore<Preferences>) {
    val complete: Flow<Boolean> = dataStore.data.map { it[COMPLETE] ?: false }

    suspend fun markComplete() {
        dataStore.edit { it[COMPLETE] = true }
    }

    /** Whether the notification permission was asked for when a reply first needed it (TASK-048). */
    val notificationsAskedForReply: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ASKED] ?: false }

    suspend fun markNotificationsAskedForReply() {
        dataStore.edit { it[NOTIFICATIONS_ASKED] = true }
    }

    private companion object {
        val COMPLETE = booleanPreferencesKey("setup_complete")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked_for_reply")
    }
}

enum class SetupStep { WELCOME, NETWORK, NOTIFICATIONS, MODEL, CONTEXT }

/**
 * The notifications step is only shown where Android needs a runtime permission for notifications
 * and it is not yet granted; the context step only once a model is installed or downloading (TASK-111).
 */
fun setupSteps(askNotifications: Boolean, hasModel: Boolean = false): List<SetupStep> =
    SetupStep.entries.filter { (it != SetupStep.NOTIFICATIONS || askNotifications) && (it != SetupStep.CONTEXT || hasModel) }

/** Where the app goes when the wizard ends. */
enum class SetupExit { CHAT, BROWSE_MODELS }

data class SetupState(
    val step: SetupStep = SetupStep.WELCOME,
    val network: NetworkMode = NetworkMode.OFFLINE,
    val isFirst: Boolean = true,
    val isLast: Boolean = false,
    /** Models the evaluation found good on phones like this one (TASK-107); empty where none was measured. */
    val suggestions: List<SuggestedModel> = emptyList(),
    /** The default context size; null is Auto (TASK-111). */
    val contextLength: Int? = null,
)

/** The first-launch wizard. It writes the same settings as the Settings screen. */
class SetupViewModel(
    private val setup: SetupSettingsRepository,
    private val network: NetworkSettingsRepository,
    private val inference: InferenceSettingsRepository,
    private val askNotifications: Boolean,
    private val suggestions: List<SuggestedModel> = emptyList(),
    /** Whether a model is installed or downloading, so the context step applies (TASK-111). */
    private val hasModel: StateFlow<Boolean> = MutableStateFlow(false),
) : ViewModel() {
    private val step = MutableStateFlow(setupSteps(askNotifications).first())

    private fun steps() = setupSteps(askNotifications, hasModel.value)

    val state: StateFlow<SetupState> = combine(step, network.mode, hasModel, inference.defaults.map { it.contextLength }) { current, mode, model, context ->
        val steps = setupSteps(askNotifications, model)
        SetupState(current, mode, isFirst = current == steps.first(), isLast = current == steps.last(), suggestions = suggestions, contextLength = context)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SetupState(step.value, isLast = steps().size == 1, suggestions = suggestions))

    fun next() {
        val steps = steps()
        step.value = steps.getOrNull(steps.indexOf(step.value) + 1) ?: step.value
    }

    fun back() {
        val steps = steps()
        step.value = steps.getOrNull(steps.indexOf(step.value) - 1) ?: step.value
    }

    /** Null is Auto. */
    fun setContextLength(contextLength: Int?) {
        viewModelScope.launch { inference.setContextLength(contextLength) }
    }

    fun setNetworkMode(mode: NetworkMode) {
        viewModelScope.launch { network.setMode(mode) }
    }

    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            setup.markComplete()
            // Clearing all data shows the wizard again in the same process; it starts from the beginning.
            step.value = steps().first()
            onFinished()
        }
    }
}

package com.bizzeh.bruce.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class SetupStep { WELCOME, NETWORK, NOTIFICATIONS, MODEL }

/** The notifications step is only shown where Android needs a runtime permission for notifications and it is not yet granted. */
fun setupSteps(askNotifications: Boolean): List<SetupStep> =
    SetupStep.entries.filter { it != SetupStep.NOTIFICATIONS || askNotifications }

/** Where the app goes when the wizard ends. */
enum class SetupExit { CHAT, BROWSE_MODELS }

data class SetupState(
    val step: SetupStep = SetupStep.WELCOME,
    val network: NetworkMode = NetworkMode.OFFLINE,
    val isFirst: Boolean = true,
    val isLast: Boolean = false,
)

/** The first-launch wizard. It writes the same settings as the Settings screen. */
class SetupViewModel(
    private val setup: SetupSettingsRepository,
    private val network: NetworkSettingsRepository,
    askNotifications: Boolean,
) : ViewModel() {
    private val steps = setupSteps(askNotifications)
    private val index = MutableStateFlow(0)

    val state: StateFlow<SetupState> = combine(index, network.mode) { i, mode ->
        SetupState(steps[i], mode, isFirst = i == 0, isLast = i == steps.lastIndex)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SetupState(steps.first(), isLast = steps.size == 1))

    fun next() {
        index.value = (index.value + 1).coerceAtMost(steps.lastIndex)
    }

    fun back() {
        index.value = (index.value - 1).coerceAtLeast(0)
    }

    fun setNetworkMode(mode: NetworkMode) {
        viewModelScope.launch { network.setMode(mode) }
    }

    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            setup.markComplete()
            // Clearing all data shows the wizard again in the same process; it starts from the beginning.
            index.value = 0
            onFinished()
        }
    }
}

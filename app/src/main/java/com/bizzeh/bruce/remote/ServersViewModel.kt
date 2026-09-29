package com.bizzeh.bruce.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import com.bizzeh.bruce.models.RemoteChoice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ServerStatus {
    data object Checking : ServerStatus

    data class Ready(val models: List<String>) : ServerStatus

    data class Failed(val reason: String) : ServerStatus
}

data class ServersState(
    val servers: List<RemoteServer> = emptyList(),
    /** By server id; a server not checked since Bruce started has none. */
    val statuses: Map<String, ServerStatus> = emptyMap(),
    /** Why the address being added was refused. */
    val addError: String? = null,
    /** The server model in use, if the chosen model runs on a server. */
    val active: RemoteChoice? = null,
) {
    /** Every model the checked servers offer, for the model switcher. */
    fun choices(): List<RemoteChoice> = servers.flatMap { server ->
        (statuses[server.id] as? ServerStatus.Ready)?.models.orEmpty().map { RemoteChoice(server.id, server.name, it) }
    }
}

/** The Servers tab (TASK-075): add, check and remove model servers. */
class ServersViewModel(
    private val store: RemoteServerStore,
    private val client: ServerClient,
    active: Flow<RemoteChoice?> = flowOf(null),
    /** ModelSelection.chooseRemote. */
    private val choose: suspend (Pair<RemoteServer, String>) -> Unit = {},
) : ViewModel() {
    private val statuses = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    private val addError = MutableStateFlow<String?>(null)

    val state: StateFlow<ServersState> = combine(store.servers, statuses, addError, active) { servers, checked, error, using -> ServersState(servers, checked, error, using) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ServersState())

    init {
        // Each server's models are listed when Bruce starts, so the model switcher can offer them.
        viewModelScope.launch { store.servers.first().forEach(::check) }
    }

    fun use(choice: RemoteChoice) {
        viewModelScope.launch {
            val server = store.servers.first().firstOrNull { it.id == choice.serverId } ?: return@launch
            choose(server to choice.model)
        }
    }

    /** Adds the server if its address is allowed, and checks it at once. */
    fun add(name: String, address: String, key: String) {
        when (val checked = ServerAddress.check(address)) {
            is AddressCheck.Refused -> addError.value = checked.reason
            is AddressCheck.Allowed -> {
                addError.value = null
                viewModelScope.launch { check(store.add(name, checked, key.ifBlank { null })) }
            }
        }
    }

    fun check(server: RemoteServer) {
        viewModelScope.launch {
            statuses.update { it + (server.id to ServerStatus.Checking) }
            val status = when (val result = client.models(server, store.key(server.id))) {
                is ServerCheck.Models -> ServerStatus.Ready(result.ids)
                is ServerCheck.Failed -> ServerStatus.Failed(result.reason)
            }
            statuses.update { it + (server.id to status) }
        }
    }

    fun remove(server: RemoteServer) {
        viewModelScope.launch {
            store.remove(server.id)
            statuses.update { it - server.id }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

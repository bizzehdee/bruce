package com.bizzeh.bruce.remote

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
)

/** The Servers tab (TASK-075): add, check and remove model servers. */
class ServersViewModel(private val store: RemoteServerStore, private val client: ServerClient) : ViewModel() {
    private val statuses = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    private val addError = MutableStateFlow<String?>(null)

    val state: StateFlow<ServersState> = combine(store.servers, statuses, addError) { servers, checked, error -> ServersState(servers, checked, error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ServersState())

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

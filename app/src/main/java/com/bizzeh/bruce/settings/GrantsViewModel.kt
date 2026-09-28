package com.bizzeh.bruce.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.policy.GrantStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Permissions screen's files and folders. */
class GrantsViewModel(private val store: GrantStore, private val ioDispatcher: CoroutineDispatcher) : ViewModel() {
    val grants: StateFlow<List<Grant>> = store.grants.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val mutableAddFailed = MutableStateFlow(false)

    /** Android refused to let Bruce keep access to what the user picked. */
    val addFailed: StateFlow<Boolean> = mutableAddFailed.asStateFlow()

    fun add(uri: Uri, kind: GrantKind) {
        viewModelScope.launch {
            mutableAddFailed.value = try {
                withContext(ioDispatcher) { store.add(uri, kind) }
                false
            } catch (e: SecurityException) {
                true
            }
        }
    }

    fun revoke(grant: Grant) {
        viewModelScope.launch { withContext(ioDispatcher) { store.revoke(grant) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package com.bizzeh.bruce.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.policy.FolderInstructions
import com.bizzeh.bruce.policy.FolderInstructionsStore
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.policy.GrantStore
import com.bizzeh.bruce.policy.InstructionsChoice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A folder's instructions and what the user chose about them (TASK-049). */
data class FolderReview(val instructions: FolderInstructions, val choice: InstructionsChoice)

/** The Permissions screen's files and folders. */
class GrantsViewModel(
    private val store: GrantStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val instructions: FolderInstructionsStore? = null,
) : ViewModel() {
    val grants: StateFlow<List<Grant>> = store.grants.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Folders with instructions, by grant id; read again whenever the grants or the choices change. */
    val folders: StateFlow<Map<Long, FolderReview>> = (instructions?.let { found ->
        combine(store.grants, found.choices) { granted, _ -> granted }.map { granted ->
            withContext(ioDispatcher) {
                granted.mapNotNull { grant -> found.read(grant)?.let { grant.id to FolderReview(it, found.choice(it)) } }.toMap()
            }
        }
    } ?: flowOf(emptyMap())).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private val mutableReviewing = MutableStateFlow<Long?>(null)

    /** The folder whose instructions are shown for a decision. */
    val reviewing: StateFlow<Long?> = mutableReviewing.asStateFlow()

    fun review(grant: Grant) {
        mutableReviewing.value = grant.id
    }

    fun closeReview() {
        mutableReviewing.value = null
    }

    fun choose(review: FolderReview, follow: Boolean) {
        mutableReviewing.value = null
        viewModelScope.launch { instructions?.choose(review.instructions, follow) }
    }

    private val mutableAddFailed = MutableStateFlow(false)

    /** Android refused to let Bruce keep access to what the user picked. */
    val addFailed: StateFlow<Boolean> = mutableAddFailed.asStateFlow()

    fun add(uri: Uri, kind: GrantKind) {
        viewModelScope.launch {
            mutableAddFailed.value = try {
                val grant = withContext(ioDispatcher) { store.add(uri, kind) }
                // A newly granted folder with instructions asks at once (TASK-049).
                val found = instructions?.let { withContext(ioDispatcher) { it.read(grant) } }
                if (found != null && instructions.choice(found) == InstructionsChoice.UNDECIDED) mutableReviewing.value = grant.id
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

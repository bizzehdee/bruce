package com.bizzeh.bruce.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The drawer's and Archived screen's lists. [onRemoved] hears which conversations left the main list, so the chat can let go of one. */
class ConversationsViewModel(
    private val store: ConversationStore,
    private val onRemoved: (Set<Long>) -> Unit,
) : ViewModel() {
    val active: StateFlow<List<Conversation>> = store.active.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archived: StateFlow<List<Conversation>> = store.archived.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun rename(id: Long, title: String) = launch { store.rename(id, title) }

    fun archive(ids: Set<Long>) = launch {
        store.archive(ids)
        onRemoved(ids)
    }

    fun restore(ids: Set<Long>) = launch { store.restore(ids) }

    fun delete(ids: Set<Long>) = launch {
        store.delete(ids)
        onRemoved(ids)
    }

    fun deleteAll() = launch {
        val all = (active.value + archived.value).map { it.id }.toSet()
        store.deleteAll()
        onRemoved(all)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

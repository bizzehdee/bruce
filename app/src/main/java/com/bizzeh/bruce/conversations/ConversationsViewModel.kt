package com.bizzeh.bruce.conversations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneId

/** Chats to export, kept while the user picks where the file goes. [ids] null means every chat. */
data class ExportRequest(val ids: Set<Long>?, val format: ExportFormat)

sealed interface ExportOutcome {
    data class Exported(val count: Int) : ExportOutcome

    data object Failed : ExportOutcome
}

/**
 * The drawer's and Archived screen's lists. [onRemoved] hears which conversations left the main
 * list, so the chat can let go of one. [openOutput] opens the document the user picked for export,
 * or returns null when it cannot.
 */
class ConversationsViewModel(
    private val store: ConversationStore,
    private val onRemoved: (Set<Long>) -> Unit,
    private val openOutput: (String) -> OutputStream? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val export: ChatExport = ChatExport(ZoneId.systemDefault()),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val active: StateFlow<List<Conversation>> = store.active.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archived: StateFlow<List<Conversation>> = store.archived.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pendingExport: ExportRequest? = null
    private val _exportOutcome = MutableStateFlow<ExportOutcome?>(null)
    val exportOutcome: StateFlow<ExportOutcome?> = _exportOutcome.asStateFlow()

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

    /** Remembers what to export and returns the file name to suggest when the user picks where it goes. */
    fun requestExport(request: ExportRequest): String {
        pendingExport = request
        val single = request.ids?.singleOrNull()?.let { id -> (active.value + archived.value).firstOrNull { it.id == id }?.title }
        return export.fileName(request.format, single, clock())
    }

    /** Writes the pending export to the document the user picked; null means they cancelled. */
    fun exportTo(uri: String?) {
        val request = pendingExport ?: return
        pendingExport = null
        if (uri == null) return
        launch {
            _exportOutcome.value = try {
                val count = withContext(io) {
                    val stream = openOutput(uri) ?: throw IOException("no stream for the picked document")
                    stream.bufferedWriter().use { export.write(request.format, store.exported(request.ids), it, clock()) }
                }
                ExportOutcome.Exported(count)
            } catch (e: IOException) {
                Log.w(TAG, "export failed: ${e.javaClass.simpleName}")
                ExportOutcome.Failed
            } catch (e: SecurityException) {
                Log.w(TAG, "export refused by the provider")
                ExportOutcome.Failed
            }
        }
    }

    fun exportOutcomeShown() {
        _exportOutcome.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val TAG = "BruceExport"
    }
}

package com.bizzeh.bruce.search

import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.huggingface.DownloadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Where setting up file search stands; [progress] is 0 to 1 while downloading. */
data class SetupState(val installed: Boolean, val progress: Float? = null, val error: DownloadError? = null)

/**
 * Turning file search on and off (TASK-096): downloading the embedding model (after the user has
 * seen its terms), then indexing; removing it deletes the model and the index.
 */
class SearchSetup(
    /** Fetches the model to [modelFile], verified (ModelDownloader.download for EmbeddingModel). */
    private val fetch: suspend (onProgress: (Long, Long) -> Unit) -> DownloadResult,
    private val modelFile: File,
    private val search: FileSearch,
    private val work: IndexWork,
) {
    private val state = MutableStateFlow(SetupState(modelFile.isFile))
    val setup: StateFlow<SetupState> = state.asStateFlow()

    suspend fun download() {
        state.value = SetupState(installed = false, progress = 0f)
        val result = fetch { done, total -> state.value = SetupState(installed = false, progress = if (total > 0) done.toFloat() / total else 0f) }
        state.value = when (result) {
            is DownloadResult.Downloaded -> SetupState(installed = true).also {
                work.now()
                work.daily()
            }
            is DownloadResult.Failed -> SetupState(installed = false, error = result.error)
        }
    }

    /** Asks for an update now, if search is set up; after grants change or when the user asks. */
    fun updateIndex() {
        if (modelFile.isFile) work.now()
    }

    suspend fun remove() {
        work.stop()
        search.clear()
        modelFile.delete()
        state.value = SetupState(installed = false)
    }
}

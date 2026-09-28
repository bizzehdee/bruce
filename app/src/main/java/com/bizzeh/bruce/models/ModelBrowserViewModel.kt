package com.bizzeh.bruce.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.huggingface.DownloadResult
import com.bizzeh.bruce.huggingface.HubClient
import com.bizzeh.bruce.huggingface.HubError
import com.bizzeh.bruce.huggingface.HubFile
import com.bizzeh.bruce.huggingface.HubModel
import com.bizzeh.bruce.huggingface.HubResult
import com.bizzeh.bruce.huggingface.ModelDownloader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A download in progress or finished badly; keyed by "repository/path". */
data class DownloadState(val downloadedBytes: Long, val totalBytes: Long, val error: DownloadError? = null) {
    val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

data class RepositoryFiles(val loading: Boolean = true, val ranked: List<Assessment> = emptyList(), val error: HubError? = null)

data class BrowseState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<HubModel> = emptyList(),
    val searched: Boolean = false,
    val error: HubError? = null,
    val files: Map<String, RepositoryFiles> = emptyMap(),
    val downloads: Map<String, DownloadState> = emptyMap(),
)

/**
 * Hugging Face search and download for the Models screen. Files are ranked for this phone on
 * the phone (ModelFit); only the search text and file requests reach Hugging Face.
 */
class ModelBrowserViewModel(
    private val hub: HubClient,
    private val downloader: ModelDownloader,
    private val device: () -> DeviceProfile,
    private val contextLength: suspend () -> Int,
    private val onDownloaded: suspend () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = mutableState.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun search() {
        val query = mutableState.value.query.trim()
        if (query.isEmpty() || mutableState.value.searching) return
        mutableState.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            val result = hub.search(query, limit = SEARCH_LIMIT)
            mutableState.update {
                when (result) {
                    is HubResult.Success -> it.copy(searching = false, searched = true, results = result.value, files = emptyMap())
                    is HubResult.Failure -> it.copy(searching = false, searched = true, error = result.error)
                }
            }
        }
    }

    /** Lists and ranks a repository's GGUF files the first time it is opened. */
    fun openRepository(model: HubModel) {
        if (mutableState.value.files[model.id]?.let { !it.loading && it.error == null } == true) return
        mutableState.update { it.copy(files = it.files + (model.id to RepositoryFiles())) }
        viewModelScope.launch {
            val files = when (val result = hub.ggufFiles(model.id)) {
                is HubResult.Failure -> RepositoryFiles(loading = false, error = result.error)
                is HubResult.Success -> RepositoryFiles(
                    loading = false,
                    ranked = ModelFit.rank(result.value.map { candidate(model, it) }, device(), contextLength()),
                )
            }
            mutableState.update { it.copy(files = it.files + (model.id to files)) }
        }
    }

    fun download(model: HubModel, assessment: Assessment) {
        val key = key(model.id, assessment.candidate.path)
        if (downloadJobs[key]?.isActive == true) return
        val size = assessment.candidate.sizeBytes
        mutableState.update { it.copy(downloads = it.downloads + (key to DownloadState(0, size))) }
        downloadJobs[key] = viewModelScope.launch {
            val result = downloader.download(model.id, assessment.candidate.path, size, assessment.candidate.sha256) { done, total ->
                mutableState.update { it.copy(downloads = it.downloads + (key to DownloadState(done, total))) }
            }
            when (result) {
                is DownloadResult.Downloaded -> {
                    mutableState.update { it.copy(downloads = it.downloads - key) }
                    onDownloaded()
                }
                is DownloadResult.Failed -> mutableState.update {
                    it.copy(downloads = it.downloads + (key to (it.downloads[key] ?: DownloadState(0, size)).copy(error = result.error)))
                }
            }
        }
    }

    /** Stops a download; the partial file is kept, so downloading again resumes it. */
    fun cancel(repositoryId: String, path: String) {
        val key = key(repositoryId, path)
        downloadJobs.remove(key)?.cancel()
        mutableState.update { it.copy(downloads = it.downloads - key) }
    }

    private fun candidate(model: HubModel, file: HubFile) = Candidate(
        repositoryId = model.id,
        path = file.path,
        sizeBytes = file.sizeBytes,
        gated = model.gated,
        architecture = model.architecture,
        parameterCount = model.parameterCount,
        sha256 = file.sha256,
    )

    companion object {
        const val SEARCH_LIMIT = 30

        fun key(repositoryId: String, path: String) = "$repositoryId/$path"
    }
}

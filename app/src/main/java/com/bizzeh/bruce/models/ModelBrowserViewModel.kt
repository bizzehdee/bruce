package com.bizzeh.bruce.models

import com.bizzeh.bruce.settings.InferenceDefaults
import com.bizzeh.bruce.gguf.GgufReadResult
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A download in progress or finished badly; keyed by "repository/path". */
/** [neededBytes] and [freeBytes] say why a download was refused as [DownloadError.TOO_LARGE]. */
data class DownloadState(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val error: DownloadError? = null,
    val neededBytes: Long? = null,
    val freeBytes: Long? = null,
    /** The model is installed and [error] is its vision projector's (TASK-083). */
    val modelInstalled: Boolean = false,
) {
    val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

/** [projector] is the vision projector downloaded with any of the repository's models (TASK-083). */
data class RepositoryFiles(val loading: Boolean = true, val ranked: List<Assessment> = emptyList(), val error: HubError? = null, val projector: HubFile? = null)

data class BrowseState(
    val query: String = "",
    val searching: Boolean = false,
    /** What the Hub returned, before the phone-side filters. */
    val results: List<HubModel> = emptyList(),
    /** [results] after the phone-side filters, with each repository's best file for this phone. */
    val listings: List<Listing> = emptyList(),
    val filters: BrowseFilters = BrowseFilters(),
    /** Showing recommendations for this phone rather than a name search. */
    val recommended: Boolean = true,
    val searched: Boolean = false,
    val error: HubError? = null,
    /** The searched-for repository that has no GGUF file; [results] are the GGUF copies made from it (TASK-102). */
    val copiesOf: String? = null,
    val files: Map<String, RepositoryFiles> = emptyMap(),
    val downloads: Map<String, DownloadState> = emptyMap(),
)

/**
 * Hugging Face recommendations, search and download for the Models screen. Files are ranked for
 * this phone on the phone (ModelFit); only the search text, the task and parameter filters (and,
 * for recommendations, a parameter ceiling derived from free memory) reach Hugging Face.
 */
class ModelBrowserViewModel(
    private val hub: HubClient,
    private val downloader: ModelDownloader,
    private val device: () -> DeviceProfile,
    /** The context size setting; null is Auto. Rough judgements use InferenceDefaults.FALLBACK_CONTEXT for Auto. */
    private val contextLength: suspend () -> Int?,
    private val onDownloaded: suspend () -> Unit,
    /** Whether a chat template can express tool calls (InferenceEngine.templateSupportsTools); null if unknown. */
    private val templateSupportsTools: (template: String, bosToken: String?, eosToken: String?) -> Boolean? = { _, _, _ -> null },
    /** Judging templates parses them, off the main thread. */
    private val checkDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Repositories Bruce's evaluation recommends for this kind of phone (TASK-107). */
    private val recommendedRepositories: () -> Set<String> = { emptySet() },
    /** Whether this build flavour can load a `.litertlm` file at all (TASK-123); false on `foss`, where `AppContainer.liteRtPipeline` is always null (TASK-120). Offering the LiteRT-LM format filter there would let the user download a file this build can never run. */
    val liteRtLmAvailable: Boolean = false,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = mutableState.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()

    /** Many repositories share a template, and judging one parses it. */
    private val skillSupport = mutableMapOf<Triple<String, String?, String?>, Boolean?>()

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    /** Models suited to this phone, with no name typed. Runs once unless asked again by [setFilters]. */
    fun recommend() {
        if (mutableState.value.searched || mutableState.value.searching) return
        fetch(query = "")
    }

    /** A name search; a blank query goes back to recommendations. */
    fun search() {
        if (mutableState.value.searching) return
        fetch(mutableState.value.query.trim())
    }

    /** Task and parameter filters are applied by the Hub, so changing them asks again; the others filter what is here. */
    fun setFilters(filters: BrowseFilters) {
        val before = mutableState.value.filters
        mutableState.update { it.copy(filters = filters) }
        if (filters.task != before.task || filters.parameters != before.parameters) {
            if (!mutableState.value.searching) fetch(if (mutableState.value.recommended) "" else mutableState.value.query.trim())
        } else {
            viewModelScope.launch { relist() }
        }
    }

    private fun fetch(query: String) {
        val recommended = query.isEmpty()
        mutableState.update { it.copy(searching = true, error = null, recommended = recommended) }
        viewModelScope.launch {
            val filter = Recommendations.hubFilter(mutableState.value.filters, device())
            // A lookup that fails (the Hub answers an unknown name as unauthorised) leaves a name search.
            val named = HubClient.repositoryIn(query)?.let { (hub.model(it, filter.format) as? HubResult.Success)?.value }
            val copiesOf = named?.id?.takeIf { named.files.isEmpty() }
            val result = when {
                named == null -> hub.search(query, limit = if (recommended) RECOMMENDATION_LIMIT else SEARCH_LIMIT, filter = filter)
                copiesOf == null -> HubResult.Success(listOf(named))
                // The user named the model, so its copies are listed whatever task they are tagged with.
                else -> hub.search("", limit = SEARCH_LIMIT, filter = filter.copy(task = null, quantisedFrom = copiesOf))
            }
            mutableState.update {
                when (result) {
                    is HubResult.Success -> it.copy(searching = false, searched = true, results = result.value, files = emptyMap(), copiesOf = copiesOf)
                    is HubResult.Failure -> it.copy(searching = false, searched = true, results = emptyList(), error = result.error, copiesOf = null)
                }
            }
            relist()
        }
    }

    private suspend fun relist() {
        val state = mutableState.value
        val judged = withContext(checkDispatcher) {
            state.results.associate { model -> model.id to model.chatTemplate?.let { skills(it, model.bosToken, model.eosToken) } }
        }
        val listings = Recommendations.listings(state.results, state.filters, device(), roughContext(), state.recommended, recommendedRepositories()) { judged[it.id] }
        mutableState.update { it.copy(listings = listings) }
    }

    private fun skills(template: String, bos: String?, eos: String?): Boolean? =
        synchronized(skillSupport) { skillSupport.getOrPut(Triple(template, bos, eos)) { templateSupportsTools(template, bos, eos) } }

    /** Lists and ranks a repository's GGUF files the first time it is opened. */
    fun openRepository(model: HubModel) {
        if (mutableState.value.files[model.id]?.let { !it.loading && it.error == null } == true) return
        mutableState.update { it.copy(files = it.files + (model.id to RepositoryFiles())) }
        viewModelScope.launch { listFiles(model) }
    }

    private suspend fun listFiles(model: HubModel): RepositoryFiles {
        val format = mutableState.value.filters.format
        val files = when (format) {
            ModelFormat.GGUF -> when (val result = hub.ggufFiles(model.id)) {
                is HubResult.Failure -> RepositoryFiles(loading = false, error = result.error)
                is HubResult.Success -> {
                    val projector = Projectors.choose(result.value)
                    RepositoryFiles(
                        loading = false,
                        ranked = ModelFit.rank(result.value.filterNot { Projectors.isProjector(it.path) }.map { candidate(model, it, projector, format) }, device(), roughContext()),
                        projector = projector,
                    )
                }
            }
            // No vision projector concept exists yet for LiteRT-LM (TASK-120 research): every file
            // is ranked on its own, with no sibling projector lookup.
            ModelFormat.LITERT_LM -> when (val result = hub.liteRtLmFiles(model.id)) {
                is HubResult.Failure -> RepositoryFiles(loading = false, error = result.error)
                is HubResult.Success -> RepositoryFiles(
                    loading = false,
                    ranked = ModelFit.rank(result.value.map { candidate(model, it, null, format) }, device(), roughContext()),
                )
            }
        }
        mutableState.update { it.copy(files = it.files + (model.id to files)) }
        return files
    }

    /**
     * Downloads one named file, as the setup wizard's suggested models do (TASK-107): the
     * repository and its files are looked up first, so the download is judged and verified as one
     * chosen in the browser is.
     */
    fun downloadFile(repositoryId: String, path: String) {
        val key = key(repositoryId, path)
        if (downloadJobs[key]?.isActive == true) return
        mutableState.update { it.copy(downloads = it.downloads + (key to DownloadState(0, 0))) }
        downloadJobs[key] = viewModelScope.launch {
            val failed = { error: DownloadError -> mutableState.update { it.copy(downloads = it.downloads + (key to DownloadState(0, 0, error))) } }
            val model = when (val result = hub.model(repositoryId)) {
                is HubResult.Failure -> return@launch failed(downloadError(result.error))
                is HubResult.Success -> result.value
            }
            val files = listFiles(model)
            files.error?.let { return@launch failed(downloadError(it)) }
            val assessment = files.ranked.firstOrNull { it.candidate.path == path } ?: return@launch failed(DownloadError.NOT_FOUND)
            runDownload(model, assessment, key)
        }
    }

    fun download(model: HubModel, assessment: Assessment) {
        val key = key(model.id, assessment.candidate.path)
        if (downloadJobs[key]?.isActive == true) return
        downloadJobs[key] = viewModelScope.launch { runDownload(model, assessment, key) }
    }

    private suspend fun runDownload(model: HubModel, assessment: Assessment, key: String) {
        val size = assessment.candidate.sizeBytes
        val projector = mutableState.value.files[model.id]?.projector
        val total = size + (projector?.sizeBytes ?: 0)
        mutableState.update { it.copy(downloads = it.downloads + (key to DownloadState(0, total))) }
        // The list judged the file by its size alone; its header shows what it really needs (owner, 2026-09-29).
        val exact = exactAssessment(model, assessment)
        if (exact != null) {
            replaceAssessment(model.id, exact)
            if (exact.fit == Fit.DOES_NOT_FIT) {
                val refused = DownloadState(0, total, DownloadError.TOO_LARGE, exact.estimate.totalBytes, device().usableMemoryBytes)
                mutableState.update { it.copy(downloads = it.downloads + (key to refused)) }
                return
            }
        }
        val progress = { done: Long -> mutableState.update { it.copy(downloads = it.downloads + (key to DownloadState(done, total))) } }
        val result = downloader.download(model.id, assessment.candidate.path, size, assessment.candidate.sha256) { done, _ -> progress(done) }
        val failed = { error: DownloadError, modelInstalled: Boolean ->
            mutableState.update {
                it.copy(downloads = it.downloads + (key to (it.downloads[key] ?: DownloadState(0, total)).copy(error = error, modelInstalled = modelInstalled)))
            }
        }
        if (result is DownloadResult.Failed) return failed(result.error, false)
        val file = (result as DownloadResult.Downloaded).file
        if (projector != null) {
            // After the model, so a model is never left waiting on its projector; downloading again adds only the projector.
            val paired = downloader.download(model.id, projector.path, projector.sizeBytes, projector.sha256, destination = Projectors.fileFor(file)) { done, _ -> progress(size + done) }
            if (paired is DownloadResult.Failed) {
                onDownloaded()
                return failed(paired.error, true)
            }
        }
        mutableState.update { it.copy(downloads = it.downloads - key) }
        onDownloaded()
    }

    private suspend fun roughContext(): Int = contextLength() ?: InferenceDefaults.FALLBACK_CONTEXT

    /**
     * The file judged from its GGUF header, at the context size it would load with; null when the
     * header could not be read, and the download goes ahead as before. Always null for a
     * LiteRT-LM candidate (TASK-123): the format has no header to read, by design (TASK-120).
     */
    private suspend fun exactAssessment(model: HubModel, assessment: Assessment): Assessment? {
        if (assessment.candidate.format == ModelFormat.LITERT_LM) return null
        val projectorBytes = assessment.candidate.projectorBytes
        val header = (hub.ggufHeader(model.id, assessment.candidate.path) as? HubResult.Success)?.value as? GgufReadResult.Read ?: return null
        val profile = device()
        val context = contextLength() ?: AutoContext.pick(header.metadata, profile.totalMemoryBytes, projectorBytes)
        return ModelFit.assess(assessment.candidate.copy(header = header.metadata), profile, context)
    }

    private fun replaceAssessment(repositoryId: String, exact: Assessment) = mutableState.update { state ->
        val files = state.files[repositoryId] ?: return@update state
        val ranked = files.ranked.map { if (it.candidate.path == exact.candidate.path) exact else it }
        state.copy(files = state.files + (repositoryId to files.copy(ranked = ranked)))
    }

    /** Stops a download; the partial file is kept, so downloading again resumes it. */
    fun cancel(repositoryId: String, path: String) {
        val key = key(repositoryId, path)
        downloadJobs.remove(key)?.cancel()
        mutableState.update { it.copy(downloads = it.downloads - key) }
    }

    private fun candidate(model: HubModel, file: HubFile, projector: HubFile?, format: ModelFormat) = Candidate(
        repositoryId = model.id,
        path = file.path,
        sizeBytes = file.sizeBytes,
        gated = model.gated,
        architecture = model.architecture,
        parameterCount = model.parameterCount,
        sha256 = file.sha256,
        projectorBytes = projector?.sizeBytes ?: 0,
        format = format,
    )

    private fun downloadError(error: HubError): DownloadError = when (error) {
        HubError.NETWORK_DISABLED -> DownloadError.NETWORK_DISABLED
        HubError.OFFLINE -> DownloadError.INTERRUPTED
        HubError.RATE_LIMITED -> DownloadError.RATE_LIMITED
        HubError.NOT_FOUND -> DownloadError.NOT_FOUND
        HubError.UNAUTHORISED -> DownloadError.UNAUTHORISED
        HubError.SERVER_ERROR -> DownloadError.SERVER_ERROR
        HubError.MALFORMED_RESPONSE, HubError.RESPONSE_TOO_LARGE -> DownloadError.UNEXPECTED_RESPONSE
    }

    companion object {
        const val SEARCH_LIMIT = 50

        /** One request; the phone-side filters then drop what does not fit. */
        const val RECOMMENDATION_LIMIT = 100

        fun key(repositoryId: String, path: String) = "$repositoryId/$path"
    }
}

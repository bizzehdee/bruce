package com.bizzeh.bruce.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.huggingface.HubError
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.settings.InferenceDefaults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** An installed model file with everything the Models screen shows about it. */
data class InstalledModel(
    val file: File,
    val sizeBytes: Long,
    val metadata: GgufMetadata?,
    val assessment: Assessment,
    val overrides: ModelOverrides,
    /** False when the file's chat template cannot express tool calls, null when unknown (TASK-062). */
    val skills: Boolean? = null,
    /** A tool-capable template fetched for this model, used instead of the file's (TASK-063). */
    val template: FetchedTemplate? = null,
)

/** Where fetching a tool template for a model stands; absent once one is saved. */
sealed interface TemplateStatus {
    data object Searching : TemplateStatus

    data object NotFound : TemplateStatus

    data class Failed(val error: HubError) : TemplateStatus
}

data class ModelsState(
    val models: List<InstalledModel> = emptyList(),
    val active: File? = null,
    val loading: File? = null,
    val loadError: LoadError? = null,
    val importing: Boolean = false,
    val importError: ImportError? = null,
    /** By model file name. */
    val templates: Map<String, TemplateStatus> = emptyMap(),
    /** The phone's RAM, which each model's expected use is shown against; 0 until read. */
    val totalMemoryBytes: Long = 0,
)

class ModelsViewModel(
    private val activeModel: ActiveModel,
    private val selection: ModelSelection,
    private val modelSettings: ModelSettingsRepository,
    private val inferenceDefaults: Flow<InferenceDefaults>,
    private val importModel: suspend (Uri) -> ImportResult,
    private val device: () -> DeviceProfile,
    private val ioDispatcher: CoroutineDispatcher,
    /** InferenceEngine.templateSupportsTools; a file holds no BOS/EOS text, only token ids, so those are not given. */
    private val templateSupportsTools: (template: String, bosToken: String?, eosToken: String?) -> Boolean? = { _, _, _ -> null },
    /** TemplateFinder.find. */
    private val findTemplate: suspend (InstalledModel) -> TemplateSearch = { TemplateSearch.NotFound },
) : ViewModel() {
    private val mutableState = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            activeModel.state.collect { model -> mutableState.update { it.copy(active = model.active) } }
        }
        viewModelScope.launch { refresh() }
    }

    fun choose(file: File) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = file, loadError = null) }
            val result = selection.choose(file)
            mutableState.update { it.copy(loading = null, loadError = (result as? LoadResult.Failed)?.error) }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(importing = true, importError = null) }
            val result = importModel(uri)
            mutableState.update { it.copy(importing = false, importError = (result as? ImportResult.Failed)?.error) }
            refresh()
        }
    }

    fun delete(file: File) {
        viewModelScope.launch {
            selection.delete(file)
            refresh()
        }
    }

    /** Fetches a tool template from another copy of the model, and reloads the model if it is loaded. */
    fun getTemplate(file: File) {
        val model = mutableState.value.models.firstOrNull { it.file == file } ?: return
        viewModelScope.launch {
            setTemplateStatus(file, TemplateStatus.Searching)
            when (val search = findTemplate(model)) {
                is TemplateSearch.Found -> {
                    modelSettings.setTemplate(file.name, search.template)
                    setTemplateStatus(file, null)
                    reloadIfActive(file)
                }
                TemplateSearch.NotFound -> setTemplateStatus(file, TemplateStatus.NotFound)
                is TemplateSearch.Failed -> setTemplateStatus(file, TemplateStatus.Failed(search.error))
            }
            refresh()
        }
    }

    fun removeTemplate(file: File) {
        viewModelScope.launch {
            modelSettings.setTemplate(file.name, null)
            reloadIfActive(file)
            refresh()
        }
    }

    private suspend fun reloadIfActive(file: File) {
        if (activeModel.state.value.active == file) selection.choose(file)
    }

    private fun setTemplateStatus(file: File, status: TemplateStatus?) {
        mutableState.update { it.copy(templates = if (status == null) it.templates - file.name else it.templates + (file.name to status)) }
    }

    fun setOverrides(file: File, overrides: ModelOverrides) {
        viewModelScope.launch {
            modelSettings.setOverrides(file.name, overrides)
            refresh()
        }
    }

    suspend fun refresh() {
        activeModel.refresh()
        val defaults = inferenceDefaults.first()
        val profile = device()
        val models = activeModel.state.value.installed.map { file ->
            val metadata = withContext(ioDispatcher) { (GgufReader.read(file) as? GgufReadResult.Read)?.metadata }
            val overrides = modelSettings.overridesNow(file.name)
            val candidate = Candidate(
                repositoryId = "local/model",
                path = file.name,
                sizeBytes = metadata?.fileSizeBytes ?: withContext(ioDispatcher) { file.length() },
                gated = false,
                architecture = metadata?.architecture,
                parameterCount = metadata?.parameterCount,
                header = metadata,
            )
            // The same cap ActiveModel applies when loading, so the estimate matches reality.
            val requested = overrides.loadConfig(defaults) { AutoContext.pick(metadata, profile.usableMemoryBytes) }.contextLength
            val contextLength = metadata?.contextLength?.takeIf { it in 1 until requested }?.toInt() ?: requested
            val fetched = modelSettings.template(file.name).first()
            // A fetched template was judged able to call tools before it was saved.
            val skills = if (fetched != null) true else metadata?.chatTemplate?.let { template -> withContext(ioDispatcher) { templateSupportsTools(template, null, null) } }
            InstalledModel(file, candidate.sizeBytes, metadata, ModelFit.assess(candidate, profile, contextLength), overrides, skills, fetched)
        }
        mutableState.update { it.copy(models = models, totalMemoryBytes = profile.totalMemoryBytes) }
    }
}

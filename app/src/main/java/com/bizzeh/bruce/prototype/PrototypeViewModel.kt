package com.bizzeh.bruce.prototype

import android.app.ActivityManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.models.ActiveModel
import com.bizzeh.bruce.models.ImportResult
import com.bizzeh.bruce.models.ModelMemory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PrototypeViewModel(
    private val engine: InferenceEngine,
    private val activeModel: ActiveModel,
    private val modelsDir: File,
    private val importModel: suspend (Uri) -> ImportResult,
    private val detectCpuFeatures: () -> CpuFeatures,
    private val memoryInfo: () -> ActivityManager.MemoryInfo,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PrototypeState())
    val state: StateFlow<PrototypeState> = mutableState.asStateFlow()

    private val loadConfig get() = LoadConfig(backend = mutableState.value.backend)

    private var generation: Job? = null

    init {
        viewModelScope.launch {
            val (cpu, capabilities) = withContext(ioDispatcher) { detectCpuFeatures() to engine.getCapabilities() }
            mutableState.update { it.copy(cpuFeatures = cpu, capabilities = capabilities) }
            refreshModels()
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(importing = true, importError = null) }
            val result = importModel(uri)
            mutableState.update {
                it.copy(importing = false, importError = (result as? ImportResult.Failed)?.error)
            }
            refreshModels()
            if (result is ImportResult.Imported) select(result.file)
        }
    }

    fun select(file: File) {
        viewModelScope.launch {
            val read = withContext(ioDispatcher) { GgufReader.read(file) }
            val metadata = (read as? GgufReadResult.Read)?.metadata
            val memory = metadata?.let {
                ModelMemory.check(ModelMemory.estimate(it, loadConfig.contextLength), memoryInfo())
            }
            mutableState.update {
                it.copy(
                    selected = file,
                    metadata = metadata,
                    metadataError = (read as? GgufReadResult.Failed)?.error,
                    memory = memory,
                )
            }
        }
    }

    fun setBackend(backend: BackendPreference) {
        mutableState.update { it.copy(backend = backend) }
    }

    fun load() {
        val file = mutableState.value.selected ?: return
        generation?.cancel()
        viewModelScope.launch {
            mutableState.update { it.copy(load = LoadState.Loading) }
            val load = when (val result = activeModel.load(file, loadConfig)) {
                is LoadResult.Loaded -> LoadState.Loaded(result.info, result.backend, result.failedBackends)
                is LoadResult.Failed -> LoadState.Failed(result.error)
            }
            mutableState.update { it.copy(load = load) }
        }
    }

    fun setPrompt(prompt: String) {
        mutableState.update { it.copy(prompt = prompt) }
    }

    fun generate() {
        val current = mutableState.value
        if (current.generating || current.load !is LoadState.Loaded) return
        mutableState.update {
            it.copy(output = "", generating = true, stats = null, stopReason = null, generationError = null)
        }
        generation = viewModelScope.launch {
            engine.generate(GenerationRequest(current.prompt)).collect { event -> apply(event) }
            mutableState.update { it.copy(generating = false) }
        }
    }

    fun stop() {
        engine.stop()
    }

    private fun apply(event: GenerationEvent) = mutableState.update {
        when (event) {
            is GenerationEvent.Token -> it.copy(output = it.output + event.text)
            is GenerationEvent.Completed -> it.copy(stats = event.stats, stopReason = event.reason)
            is GenerationEvent.Failed -> it.copy(generationError = event.error)
        }
    }

    private suspend fun refreshModels() {
        val models = withContext(ioDispatcher) {
            modelsDir.listFiles { file -> file.isFile && file.name.endsWith(".gguf") }.orEmpty().sortedBy { it.name }
        }
        mutableState.update { it.copy(models = models) }
    }
}

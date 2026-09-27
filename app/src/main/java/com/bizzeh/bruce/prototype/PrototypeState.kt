package com.bizzeh.bruce.prototype

import com.bizzeh.bruce.gguf.GgufError
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.EngineCapabilities
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.StopReason
import com.bizzeh.bruce.models.ImportError
import com.bizzeh.bruce.models.MemoryCheck
import java.io.File

data class PrototypeState(
    val cpuFeatures: CpuFeatures? = null,
    val capabilities: EngineCapabilities? = null,
    val models: List<File> = emptyList(),
    val importing: Boolean = false,
    val importError: ImportError? = null,
    val selected: File? = null,
    val metadata: GgufMetadata? = null,
    val metadataError: GgufError? = null,
    val memory: MemoryCheck? = null,
    val backend: BackendPreference = BackendPreference.AUTO,
    val load: LoadState = LoadState.Unloaded,
    val prompt: String = "",
    val output: String = "",
    val generating: Boolean = false,
    val stats: GenerationStats? = null,
    val stopReason: StopReason? = null,
    val generationError: GenerationError? = null,
)

sealed interface LoadState {
    data object Unloaded : LoadState

    data object Loading : LoadState

    data class Loaded(val info: ModelInfo, val backend: Backend, val failedBackends: List<Backend>) : LoadState

    data class Failed(val error: LoadError) : LoadState
}

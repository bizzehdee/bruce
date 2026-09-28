package com.bizzeh.bruce.models

import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

data class ActiveModelState(
    val installed: List<File> = emptyList(),
    val active: File? = null,
    val info: ModelInfo? = null,
    val backend: Backend? = null,
    val loading: Boolean = false,
    val error: LoadError? = null,
)

/**
 * Which model is loaded, shared by every screen: the chat title, the quick model switcher,
 * model management and diagnostics all read and change it here.
 */
class ActiveModel(
    private val engine: InferenceEngine,
    private val modelsDir: File,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow(ActiveModelState())
    val state: StateFlow<ActiveModelState> = mutableState.asStateFlow()

    suspend fun refresh() {
        val installed = withContext(ioDispatcher) {
            modelsDir.listFiles { file -> file.isFile && file.name.endsWith(".gguf") }.orEmpty().sortedBy { it.name.lowercase() }
        }
        mutableState.update { it.copy(installed = installed) }
    }

    suspend fun unload() {
        engine.unloadModel()
        mutableState.update { it.copy(active = null, info = null, backend = null, error = null) }
    }

    suspend fun load(file: File, config: LoadConfig = LoadConfig()): LoadResult {
        mutableState.update { it.copy(loading = true, error = null) }
        val result = engine.loadModel(file, config)
        mutableState.update {
            when (result) {
                is LoadResult.Loaded -> it.copy(active = file, info = result.info, backend = result.backend, loading = false)
                // Some failures (missing file, not GGUF) keep the previous model loaded; others
                // release it first. Ask the engine rather than assume.
                is LoadResult.Failed -> if (engine.getModelInfo() == null) {
                    it.copy(active = null, info = null, backend = null, loading = false, error = result.error)
                } else {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
        return result
    }
}

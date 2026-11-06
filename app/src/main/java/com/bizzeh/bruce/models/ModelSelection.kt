package com.bizzeh.bruce.models

import com.bizzeh.bruce.remote.RemoteServer
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.settings.InferenceSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** Choosing, restoring and removing models, with each model's settings applied. */
class ModelSelection(
    private val activeModel: ActiveModel,
    private val modelSettings: ModelSettingsRepository,
    private val inferenceSettings: InferenceSettingsRepository,
    private val modelsDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    /** The phone's RAM, which Auto sizes the context from (AutoContext.pick). */
    private val totalMemory: () -> Long = { 0 },
    /** Deletes a model's evaluated system prompt (SavedPrompts.forget) with the model. */
    private val forgetSavedPrompt: (File) -> Unit = {},
    /** The user's model servers, to restore a chosen server model only while its server is still there. */
    private val servers: suspend () -> List<RemoteServer> = { emptyList() },
) {
    /** Uses a server's model instead of one on the phone (TASK-077), and remembers it for next launch. */
    suspend fun chooseRemote(server: RemoteServer, model: String) {
        activeModel.useRemote(RemoteChoice(server.id, server.name, model))
        modelSettings.setActiveRemote(server.id, model)
    }

    /**
     * Loads [file] with its settings over the defaults, and remembers it for next launch.
     * GGUF sizes its context with [AutoContext] (TASK-120: no equivalent exists for LiteRT-LM yet,
     * which loads at the model's own default context instead).
     */
    suspend fun choose(file: File): ModelLoadResult {
        val result = when (ModelFiles.formatOf(file)) {
            ModelFormat.GGUF -> {
                val template = modelSettings.template(file.name).first()
                val metadata = withContext(ioDispatcher) { (GgufReader.read(file) as? GgufReadResult.Read)?.metadata }
                val projectorBytes = withContext(ioDispatcher) { Projectors.bytesFor(file) }
                val config = modelSettings.overridesNow(file.name).loadConfig(inferenceSettings.defaults.first()) { AutoContext.pick(metadata, totalMemory(), projectorBytes) }
                    .copy(chatTemplate = template?.text)
                activeModel.loadAny(file, config)
            }
            ModelFormat.LITERT_LM, null -> activeModel.loadAny(file)
        }
        if (result is ModelLoadResult.Loaded) modelSettings.setActiveModel(file.name)
        return result
    }

    /** Loads the model chosen last time if it is still installed, otherwise the only installed model. */
    suspend fun restore() {
        activeModel.refresh()
        val remote = modelSettings.activeRemote.first()
        val server = remote?.let { (id, _) -> servers().firstOrNull { it.id == id } }
        if (remote != null && server != null) return activeModel.useRemote(RemoteChoice(server.id, server.name, remote.second))
        val remembered = modelSettings.activeModelName.first()?.let { File(modelsDir, it) }
        val file = remembered?.takeIf { withContext(ioDispatcher) { it.isFile } }
            ?: activeModel.state.value.installed.singleOrNull()
            ?: return
        choose(file)
    }

    /** Deletes a model file and its projector, unloading it first if it is loaded, and forgets its settings. */
    suspend fun delete(file: File) {
        if (activeModel.state.value.active == file) activeModel.unload()
        withContext(ioDispatcher) {
            file.delete()
            Projectors.fileFor(file).delete()
            forgetSavedPrompt(file)
        }
        modelSettings.forget(file.name)
        activeModel.refresh()
    }

    /** Whether the chosen model thinks before answering (TASK-110); a server model follows the default. */
    suspend fun activeThinking(): Boolean {
        val defaults = inferenceSettings.defaults.first()
        return activeModel.state.value.active?.let { modelSettings.overridesNow(it.name).thinking(defaults) } ?: defaults.thinking
    }

    suspend fun activeTemperature(): Float =
        activeModel.state.value.active?.let { modelSettings.overridesNow(it.name).temperature() } ?: ModelOverrides.DEFAULT_TEMPERATURE
}

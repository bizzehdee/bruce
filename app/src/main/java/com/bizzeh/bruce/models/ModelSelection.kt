package com.bizzeh.bruce.models

import com.bizzeh.bruce.inference.LoadResult
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
) {
    /** Loads [file] with its settings over the defaults, and remembers it for next launch. */
    suspend fun choose(file: File): LoadResult {
        val config = modelSettings.overridesNow(file.name).loadConfig(inferenceSettings.defaults.first())
        val result = activeModel.load(file, config)
        if (result is LoadResult.Loaded) modelSettings.setActiveModel(file.name)
        return result
    }

    /** Loads the model chosen last time if it is still installed, otherwise the only installed model. */
    suspend fun restore() {
        activeModel.refresh()
        val remembered = modelSettings.activeModelName.first()?.let { File(modelsDir, it) }
        val file = remembered?.takeIf { withContext(ioDispatcher) { it.isFile } }
            ?: activeModel.state.value.installed.singleOrNull()
            ?: return
        choose(file)
    }

    /** Deletes a model file, unloading it first if it is loaded, and forgets its settings. */
    suspend fun delete(file: File) {
        if (activeModel.state.value.active == file) activeModel.unload()
        withContext(ioDispatcher) { file.delete() }
        modelSettings.forget(file.name)
        activeModel.refresh()
    }

    suspend fun activeTemperature(): Float =
        activeModel.state.value.active?.let { modelSettings.overridesNow(it.name).temperature() } ?: ModelOverrides.DEFAULT_TEMPERATURE
}

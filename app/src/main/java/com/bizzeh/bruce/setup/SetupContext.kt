package com.bizzeh.bruce.setup

import com.bizzeh.bruce.models.AutoContext
import com.bizzeh.bruce.models.BrowseState
import com.bizzeh.bruce.models.ContextOption
import com.bizzeh.bruce.models.InstalledModel
import com.bizzeh.bruce.models.ModelBrowserViewModel
import com.bizzeh.bruce.models.SuggestedModel

/** What the wizard's context step offers for the model the user chose (TASK-111). */
data class ContextChoice(val modelName: String, val auto: Int, val options: List<ContextOption>)

object SetupContext {
    /**
     * The model the context step is about: a suggested model being downloaded in the wizard (its
     * header read from the Hub when the download began), otherwise the first installed model; null
     * when there is neither, and the step is skipped. Auto is sized from [totalMemoryBytes]; the sizes
     * offered are those that fit [usableMemoryBytes] now.
     */
    fun choice(installed: List<InstalledModel>, suggestions: List<SuggestedModel>, browse: BrowseState, usableMemoryBytes: Long, totalMemoryBytes: Long): ContextChoice? {
        val downloading = suggestions.firstNotNullOfOrNull { model ->
            val download = browse.downloads[ModelBrowserViewModel.key(model.repositoryId, model.path)] ?: return@firstNotNullOfOrNull null
            if (download.error != null) return@firstNotNullOfOrNull null
            val candidate = browse.files[model.repositoryId]?.ranked?.firstOrNull { it.candidate.path == model.path }?.candidate ?: return@firstNotNullOfOrNull null
            val header = candidate.header ?: return@firstNotNullOfOrNull null
            ContextChoice(model.name, AutoContext.pick(header, totalMemoryBytes, candidate.projectorBytes), AutoContext.options(header, usableMemoryBytes, candidate.projectorBytes))
        }
        if (downloading != null) return downloading
        val model = installed.firstOrNull() ?: return null
        val projector = model.assessment.estimate.projectorBytes
        return ContextChoice(
            model.file.nameWithoutExtension,
            AutoContext.pick(model.metadata, totalMemoryBytes, projector),
            AutoContext.options(model.metadata, usableMemoryBytes, projector),
        )
    }
}

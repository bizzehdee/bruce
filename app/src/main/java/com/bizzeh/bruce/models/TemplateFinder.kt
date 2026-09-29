package com.bizzeh.bruce.models

import com.bizzeh.bruce.huggingface.HubClient
import com.bizzeh.bruce.huggingface.HubError
import com.bizzeh.bruce.huggingface.HubResult

sealed interface TemplateSearch {
    data class Found(val template: FetchedTemplate) : TemplateSearch

    data object NotFound : TemplateSearch

    data class Failed(val error: HubError) : TemplateSearch
}

/**
 * Finds a tool-capable chat template for an installed model whose own has none (TASK-063), from
 * another Hub copy of the same model: same architecture and parameter count, a template that
 * llama.cpp judges able to express tool calls, most downloaded first. The user fetches it as they
 * fetched the model; Bruce ships no template of its own.
 */
class TemplateFinder(
    private val hub: HubClient,
    private val templateSupportsTools: (template: String, bosToken: String?, eosToken: String?) -> Boolean?,
) {
    suspend fun find(model: InstalledModel): TemplateSearch {
        val metadata = model.metadata ?: return TemplateSearch.NotFound
        val results = when (val result = hub.search(searchFor(model), limit = SEARCH_LIMIT)) {
            is HubResult.Success -> result.value
            is HubResult.Failure -> return TemplateSearch.Failed(result.error)
        }
        val copy = results.firstOrNull { candidate ->
            val template = candidate.chatTemplate
            template != null &&
                candidate.architecture == metadata.architecture &&
                candidate.parameterCount == metadata.parameterCount &&
                templateSupportsTools(template, candidate.bosToken, candidate.eosToken) == true
        } ?: return TemplateSearch.NotFound
        return TemplateSearch.Found(FetchedTemplate(copy.chatTemplate!!, copy.id))
    }

    companion object {
        private const val SEARCH_LIMIT = 50

        /** A Hub search for other copies of [model]: its declared name, spaced as repository names are. */
        fun searchFor(model: InstalledModel): String =
            (model.metadata?.name?.takeIf { it.isNotBlank() } ?: model.file.nameWithoutExtension).trim().replace(Regex("\\s+"), "-")
    }
}

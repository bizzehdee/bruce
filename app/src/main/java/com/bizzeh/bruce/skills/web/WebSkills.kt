package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.policy.WebAddress
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState

/**
 * The web page skill (TASK-069): Declined until the user turns it on, locked while the network is
 * Offline, and each site checked by the policy engine (TASK-068) before it runs. [allowed] is
 * asked again for every redirect.
 */
class WebSkills(
    private val pages: WebPages,
    private val allowed: suspend (host: String) -> Boolean,
    /** The chosen provider's search (LangSearch or Brave), which needs the user's key. */
    private val search: SearchEngine? = null,
    /** Wikipedia's search, which needs no key (TASK-113). */
    private val wikipedia: SearchEngine? = null,
) {
    fun create(): List<Skill> = listOfNotNull(fetchPage(), search?.let(::webSearch), wikipedia?.let(::wikipediaSearch))

    /** The web search (TASK-070, TASK-113): Declined, and locked until the chosen provider's key is saved and while the network is Offline. */
    private fun webSearch(engine: SearchEngine) = Skill(
        id = "web_search",
        version = 1,
        description = "Search the web and return text from the best matching pages, with their addresses.",
        input = InputSchema(listOf(Parameter(QUERY, ParameterType.STRING, "What to search for", maxLength = MAX_QUERY))),
        capabilities = setOf(Capability.WEB_SEARCH),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.WEB,
        requires = setOf(SkillRequirement.NETWORK_ALLOWED, SkillRequirement.SEARCH_KEY),
        site = { engine.host },
    ) { arguments -> answer(engine.search(arguments.string(QUERY).orEmpty())) }

    /** Wikipedia's search (TASK-113): Declined, and locked only while the network is Offline. */
    private fun wikipediaSearch(engine: SearchEngine) = Skill(
        id = "search_wikipedia",
        version = 1,
        description = "Search Wikipedia, and only Wikipedia, for encyclopedia articles; returns titles, addresses and matching excerpts. Use fetch_page to read an article.",
        input = InputSchema(listOf(Parameter(QUERY, ParameterType.STRING, "What to look up", maxLength = MAX_QUERY))),
        capabilities = setOf(Capability.WEB_SEARCH),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.WEB,
        requires = setOf(SkillRequirement.NETWORK_ALLOWED),
        site = { engine.host },
    ) { arguments -> answer(engine.search(arguments.string(QUERY).orEmpty())) }

    private fun answer(result: SearchResult): SkillOutcome =
        when (val found = result) {
            is SearchResult.Failed -> SkillOutcome.Failed(found.code, found.reason, retryable = found.code == DenialCode.RATE_LIMITED)
            is SearchResult.Found -> SkillOutcome.Done(
                if (found.sources.isEmpty()) {
                    "No results."
                } else {
                    found.sources.mapIndexed { i, source ->
                        "${i + 1}. ${source.title.ifBlank { source.url }}\n${source.url}\n${source.snippets.joinToString("\n")}"
                    }.joinToString("\n\n")
                },
            )
        }

    private fun fetchPage() = Skill(
        id = "fetch_page",
        version = 1,
        description = "Read a web page and return its text. Only for http or https addresses.",
        input = InputSchema(listOf(Parameter(URL, ParameterType.STRING, "The page's full address, starting https://", maxLength = MAX_URL))),
        capabilities = setOf(Capability.HTTP_READ),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.WEB,
        requires = setOf(SkillRequirement.NETWORK_ALLOWED),
        site = { WebAddress.host(it.string(URL).orEmpty()) },
    ) { arguments ->
        val address = arguments.string(URL).orEmpty()
        val first = WebAddress.host(address)
        // The site the user just approved, once or always, is allowed for this call's own request.
        when (val page = pages.read(address) { host -> host == first || allowed(host) }) {
            is PageResult.Failed -> SkillOutcome.Failed(DenialCode.TOOL_FAILED, page.reason, retryable = false)
            is PageResult.Read -> SkillOutcome.Done(
                buildString {
                    append("Address: ").append(page.address)
                    page.title?.let { append("\nTitle: ").append(it) }
                    append("\n\n").append(page.text.ifBlank { "(The page has no readable text.)" })
                    if (page.cut) append("\n[Only the start of this page was read.]")
                },
            )
        }
    }

    private companion object {
        const val URL = "url"
        const val MAX_URL = 2000
        const val QUERY = "query"

        /** Brave's limit for a query, and within LangSearch's and Wikipedia's. */
        const val MAX_QUERY = 600
    }
}

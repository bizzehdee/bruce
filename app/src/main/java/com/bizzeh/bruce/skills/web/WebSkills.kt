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
    // Searches before fetch_page: with fetch_page listed first, MiniCPM5 2B searched for "what happened
    // today in history" 3 times in 30; with the searches first, 27 (2026-10-02).
    fun create(): List<Skill> = listOfNotNull(search?.let(::webSearch), wikipedia?.let(::wikipediaSearch), fetchPage())

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
    ) { arguments -> answer(engine.search(arguments.string(QUERY).orEmpty()), READ_HINT) }

    /** Wikipedia's search (TASK-113): Declined, and locked only while the network is Offline. */
    private fun wikipediaSearch(engine: SearchEngine) = Skill(
        id = "search_wikipedia",
        version = 1,
        description = "Search Wikipedia, and only Wikipedia, for encyclopedia articles; returns titles, addresses and matching excerpts. " +
            "Search for an article's title or a few key words, such as \"October 2\" or \"Julius Caesar\", not a question. " +
            "Then read the best article with fetch_page to answer.",
        input = InputSchema(listOf(Parameter(QUERY, ParameterType.STRING, "An article title or a few key words, not a sentence", maxLength = MAX_QUERY))),
        capabilities = setOf(Capability.WEB_SEARCH),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.WEB,
        requires = setOf(SkillRequirement.NETWORK_ALLOWED),
        site = { engine.host },
        // Search gives excerpts; reading the article answers the question (owner, 2026-10-02).
        companions = setOf("fetch_page"),
    ) { arguments -> answer(engine.search(arguments.string(QUERY).orEmpty()), READ_HINT) }

    /** [hint] follows the results, to point a small model at its next step. */
    private fun answer(result: SearchResult, hint: String? = null): SkillOutcome =
        when (val found = result) {
            is SearchResult.Failed -> SkillOutcome.Failed(found.code, found.reason, retryable = found.code == DenialCode.RATE_LIMITED)
            is SearchResult.Found -> SkillOutcome.Done(
                if (found.sources.isEmpty()) {
                    "No results."
                } else {
                    found.sources.mapIndexed { i, source ->
                        "${i + 1}. ${source.title.ifBlank { source.url }}\n${source.url}\n${source.snippets.joinToString("\n")}"
                    }.joinToString("\n\n") + hint?.let { "\n\n$it" }.orEmpty()
                },
            )
        }

    private fun fetchPage() = Skill(
        id = "fetch_page",
        version = 1,
        description = "Read a web page and return its text: the start of the page, or the whole page with full set to true. Only for http or https addresses.",
        input = InputSchema(
            listOf(
                Parameter(URL, ParameterType.STRING, "The page's full address, starting https://", maxLength = MAX_URL),
                Parameter(FULL, ParameterType.BOOLEAN, "True to read the whole page, when its start was not enough", required = false),
            ),
        ),
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
            is PageResult.Read -> {
                // The start of a page by default keeps small contexts usable; the model asks for the rest (owner, 2026-10-02).
                val full = arguments.boolean(FULL) == true
                val limit = if (full) FULL_PAGE else PAGE_START
                val text = page.text.ifBlank { "(The page has no readable text.)" }
                SkillOutcome.Done(
                    buildString {
                        append("Address: ").append(page.address)
                        page.title?.let { append("\nTitle: ").append(it) }
                        append("\n\n").append(text.take(limit))
                        when {
                            text.length > limit && !full -> append("\n[This is the start of the page: ${text.length - limit} more characters. Call fetch_page again with full set to true to read the whole page.]")
                            text.length > limit || page.cut -> append("\n[Only the first ${minOf(limit, text.length)} characters of this page could be read.]")
                        }
                    },
                    maxLength = limit + PAGE_FRAME,
                )
            }
        }
    }

    private companion object {
        const val URL = "url"
        const val FULL = "full"

        /** About 500 tokens: a page's start, enough for most answers. */
        const val PAGE_START = 2_000

        /** About 6,000 tokens: "the whole page", within what a phone's context can take. */
        const val FULL_PAGE = 24_000

        /** Room for the address, title and the note after the text. */
        const val PAGE_FRAME = 600
        const val MAX_URL = 2000
        const val QUERY = "query"

        const val READ_HINT = "These are only excerpts. To answer from a page, read it with fetch_page and its address."

        /** Brave's limit for a query, and within LangSearch's and Wikipedia's. */
        const val MAX_QUERY = 600
    }
}

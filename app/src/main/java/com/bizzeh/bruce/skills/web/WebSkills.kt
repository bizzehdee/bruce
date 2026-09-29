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
class WebSkills(private val pages: WebPages, private val allowed: suspend (host: String) -> Boolean) {
    fun create(): List<Skill> = listOf(fetchPage())

    private fun fetchPage() = Skill(
        id = "fetch_page",
        version = 1,
        description = "Read a web page and return its text. Only for http or https addresses.",
        input = InputSchema(listOf(Parameter(URL, ParameterType.STRING, "The page's full address, starting https://", maxLength = MAX_URL))),
        capabilities = setOf(Capability.HTTP_READ),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.WEB,
        requires = SkillRequirement.NETWORK_ALLOWED,
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
    }
}

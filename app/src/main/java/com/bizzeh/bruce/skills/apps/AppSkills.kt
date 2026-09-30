package com.bizzeh.bruce.skills.apps

import com.bizzeh.bruce.policy.WebAddress
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState

/**
 * Skills that hand something to another app (TASK-087): the share sheet, the browser, an app. Each
 * shows the user what happens, so nothing leaves without them seeing it. Off by default: every
 * skill offered adds to each prompt. They work only while Bruce is on screen, as Android allows.
 */
class AppSkills(private val actions: PhoneActions, private val onScreen: () -> Boolean) {
    fun create(): List<Skill> = listOf(share(), openUrl(), openApp())

    private fun share() = Skill(
        id = "share_text",
        version = 1,
        description = "Open the share sheet with some text, so the user can send it to an app or person they pick.",
        input = InputSchema(
            listOf(
                Parameter(TEXT, ParameterType.STRING, "The text to share", maxLength = MAX_SHARE),
                Parameter(SUBJECT, ParameterType.STRING, "A subject, for email", required = false, maxLength = MAX_SUBJECT),
            ),
        ),
        capabilities = setOf(Capability.SHARE),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        whenOnScreen {
            if (actions.share(arguments.string(TEXT).orEmpty(), arguments.string(SUBJECT))) SkillOutcome.Done("The share sheet is open; the user chooses where it goes.") else nothing("share text")
        }
    }

    private fun openUrl() = Skill(
        id = "open_url",
        version = 1,
        description = "Open a web page in the user's browser, for them to read.",
        input = InputSchema(listOf(Parameter(URL, ParameterType.STRING, "The http or https address", maxLength = MAX_URL))),
        capabilities = setOf(Capability.APP_LAUNCH),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.WEB,
        requires = setOf(SkillRequirement.NETWORK_ALLOWED),
        site = { WebAddress.host(it.string(URL).orEmpty()) },
    ) { arguments ->
        whenOnScreen {
            val url = arguments.string(URL).orEmpty().trim()
            if (WebAddress.host(url) == null) return@whenOnScreen SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "Only http and https addresses can be opened.", retryable = true)
            if (actions.openUrl(url)) SkillOutcome.Done("Opened the page in the browser.") else nothing("open web pages")
        }
    }

    private fun openApp() = Skill(
        id = "open_app",
        version = 1,
        description = "Open an app on the phone by its name, such as Camera or Maps.",
        input = InputSchema(listOf(Parameter(NAME, ParameterType.STRING, "The app's name as the user sees it", maxLength = MAX_NAME))),
        capabilities = setOf(Capability.APP_LAUNCH),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        whenOnScreen {
            val wanted = arguments.string(NAME).orEmpty().trim()
            val apps = actions.launchable()
            val matches = apps.filter { it.label.equals(wanted, ignoreCase = true) }.ifEmpty { apps.filter { it.label.contains(wanted, ignoreCase = true) } }
            when {
                wanted.isEmpty() || matches.isEmpty() -> SkillOutcome.Failed(DenialCode.RESOURCE_NOT_FOUND, "No app on the phone has that name.", retryable = true)
                matches.size > 1 -> SkillOutcome.Failed(
                    DenialCode.INVALID_ARGUMENTS,
                    "More than one app matches: ${matches.take(MAX_SUGGESTED).joinToString(", ") { it.label }}. Use the full name.",
                    retryable = true,
                )
                actions.launch(matches.single().packageName) -> SkillOutcome.Done("Opened ${matches.single().label}.")
                else -> nothing("open that app")
            }
        }
    }

    private inline fun whenOnScreen(act: () -> SkillOutcome): SkillOutcome =
        if (onScreen()) act() else SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Bruce is not on screen, and Android only lets it open things while it is. Tell the user instead.")

    private fun nothing(what: String) = SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Nothing on this phone can $what.")

    private companion object {
        const val TEXT = "text"
        const val SUBJECT = "subject"
        const val URL = "url"
        const val NAME = "name"
        const val MAX_SHARE = 4000
        const val MAX_SUBJECT = 200
        const val MAX_URL = 2000
        const val MAX_NAME = 100
        const val MAX_SUGGESTED = 5
    }
}

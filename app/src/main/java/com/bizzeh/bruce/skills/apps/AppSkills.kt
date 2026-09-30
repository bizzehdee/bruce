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
    fun create(): List<Skill> = listOf(share(), openUrl(), openApp(), copy(), message(), dial())

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

    /** Writing only: reading the clipboard would expose what other apps put there (TASK-088). */
    private fun copy() = Skill(
        id = "copy_to_clipboard",
        version = 1,
        description = "Copy text to the phone's clipboard, for the user to paste somewhere.",
        input = InputSchema(listOf(Parameter(TEXT, ParameterType.STRING, "The text to copy", maxLength = MAX_SHARE))),
        // The spec's capability list has no clipboard; copying hands text to other apps, as sharing does.
        capabilities = setOf(Capability.SHARE),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        actions.copy(arguments.string(TEXT).orEmpty())
        SkillOutcome.Done("Copied to the clipboard.")
    }

    /**
     * Texts and calls are never sent or placed by Bruce (owner, 2026-09-30): the phone's own app
     * opens with them filled in, and the user sends or calls. No SMS or call permission is needed.
     */
    private fun message() = Skill(
        id = "write_text_message",
        version = 1,
        description = "Open the messaging app with a text message written, for the user to check and send.",
        input = InputSchema(
            listOf(
                Parameter(TEXT, ParameterType.STRING, "The message", maxLength = MAX_MESSAGE),
                Parameter(NUMBER, ParameterType.STRING, "The phone number, if known", required = false, maxLength = MAX_NUMBER),
            ),
        ),
        capabilities = setOf(Capability.SMS),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        whenOnScreen {
            val number = arguments.string(NUMBER)?.let { phoneNumber(it) ?: return@whenOnScreen badNumber() }
            if (actions.compose(number, arguments.string(TEXT).orEmpty())) SkillOutcome.Done("The message is written in the messaging app; the user sends it.") else nothing("send text messages")
        }
    }

    private fun dial() = Skill(
        id = "dial_number",
        version = 1,
        description = "Open the phone app with a number ready to call; the user places the call.",
        input = InputSchema(listOf(Parameter(NUMBER, ParameterType.STRING, "The phone number", maxLength = MAX_NUMBER))),
        capabilities = setOf(Capability.PHONE),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        whenOnScreen {
            val number = phoneNumber(arguments.string(NUMBER).orEmpty()) ?: return@whenOnScreen badNumber()
            if (actions.dial(number)) SkillOutcome.Done("The number is ready in the phone app; the user places the call.") else nothing("make calls")
        }
    }

    private fun badNumber() = SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "That is not a phone number: use digits, with + and spaces or dashes if needed.", retryable = true)

    private inline fun whenOnScreen(act: () -> SkillOutcome): SkillOutcome =
        if (onScreen()) act() else SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Bruce is not on screen, and Android only lets it open things while it is. Tell the user instead.")

    private fun nothing(what: String) = SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Nothing on this phone can $what.")

    private companion object {
        /** Digits with the usual separators; codes such as *#06# are refused, as they act on the phone itself. */
        val PHONE_NUMBER = Regex("\\+?[0-9(][0-9 ()-]{2,24}")

        fun phoneNumber(text: String): String? = text.trim().takeIf(PHONE_NUMBER::matches)

        const val TEXT = "text"
        const val NUMBER = "number"
        const val MAX_MESSAGE = 2000
        const val MAX_NUMBER = 30
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

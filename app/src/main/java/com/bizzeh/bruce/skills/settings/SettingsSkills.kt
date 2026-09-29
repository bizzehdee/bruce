package com.bizzeh.bruce.skills.settings

import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState

/**
 * Finding and reading the phone's settings (TASK-066). Finding reads nothing from the phone, so it
 * is Accepted; reading a value is device state, so it starts Declined, as reading files does.
 */
class SettingsSkills(private val reader: SettingsReader) {
    fun create(): List<Skill> = listOf(find(), get())

    private fun find() = Skill(
        id = "find_settings",
        version = 1,
        description = "Find phone settings by words, such as \"brightness\" or \"wifi\". With no query, lists every setting. Returns setting ids for get_setting.",
        input = InputSchema(listOf(Parameter("query", ParameterType.STRING, "Words describing the setting", required = false, maxLength = MAX_QUERY))),
        capabilities = emptySet(),
        defaultState = SkillState.ACCEPTED,
    ) { arguments ->
        val found = SettingsCatalog.find(arguments.string("query"))
        SkillOutcome.Done(
            if (found.isEmpty()) "No setting matched. Call find_settings with no query to list them all."
            else found.joinToString("\n", transform = SettingsCatalog::describe),
        )
    }

    private fun get() = Skill(
        id = "get_setting",
        version = 1,
        description = "Get the current value of a phone setting by its id from find_settings.",
        input = InputSchema(listOf(Parameter("id", ParameterType.STRING, "The setting id, for example screen_timeout", maxLength = MAX_ID))),
        capabilities = setOf(Capability.SETTINGS_READ),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        val entry = SettingsCatalog[arguments.string("id").orEmpty()]
            ?: return@Skill SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "Unknown setting id. Find it with find_settings.", retryable = true)
        val value = SettingsCatalog.value(entry, reader)
        SkillOutcome.Done(if (value == null) "${entry.name}: Android does not let Bruce read this setting on this phone." else "${entry.name}: $value")
    }

    private companion object {
        const val MAX_QUERY = 100
        const val MAX_ID = 40
    }
}

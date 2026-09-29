package com.bizzeh.bruce.skills.settings

import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.policy.ResourceTarget
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillState

/**
 * Finding and reading the phone's settings (TASK-066), and changing the few Android lets an app
 * change (TASK-067). Finding reads nothing from the phone, so it is Accepted; reading a value is
 * device state, so it starts Declined, as reading files does. Every change asks first by default,
 * and opening a settings page changes nothing, so it is Accepted.
 */
class SettingsSkills(private val reader: SettingsReader, private val writer: SettingsWriter) {
    fun create(): List<Skill> = listOf(find(), get(), set(), open())

    /**
     * The policy check for [ResourceScope.PHONE_SETTINGS]: the setting must be changeable, the value
     * valid, and the grant in place, before the user is asked. The target binds the approval to the
     * exact value, and names the value it replaces.
     */
    fun check(request: SkillRequest): ScopeCheck {
        val entry = SettingsCatalog[request.arguments.string(ID).orEmpty()] ?: return ScopeCheck.OutOfScope(UNKNOWN)
        return when (val plan = SettingChanges.plan(entry, request.arguments.string(VALUE).orEmpty(), reader)) {
            is ChangePlan.Invalid -> ScopeCheck.OutOfScope(plan.reason)
            is ChangePlan.Planned -> if (plan.write is SettingWrite.System && !writer.canWriteSystem()) {
                ScopeCheck.OutOfScope(NO_GRANT)
            } else {
                val current = SettingsCatalog.value(entry, reader) ?: "unknown"
                ScopeCheck.InScope(listOf(ResourceTarget("${entry.name}: $current → ${plan.shown}", "setting:${entry.id}:${plan.write}")))
            }
        }
    }

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
        input = InputSchema(listOf(Parameter(ID, ParameterType.STRING, "The setting id, for example screen_timeout", maxLength = MAX_ID))),
        capabilities = setOf(Capability.SETTINGS_READ),
        defaultState = SkillState.DECLINED,
    ) { arguments ->
        val entry = SettingsCatalog[arguments.string(ID).orEmpty()]
            ?: return@Skill SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, UNKNOWN, retryable = true)
        val value = SettingsCatalog.value(entry, reader)
        SkillOutcome.Done(if (value == null) "${entry.name}: Android does not let Bruce read this setting on this phone." else "${entry.name}: $value")
    }

    private fun set() = Skill(
        id = "set_setting",
        version = 1,
        description = "Change a phone setting that find_settings marks as one Bruce can change. Values: on/off, a brightness percentage, screen timeout in seconds, or a volume level.",
        input = InputSchema(
            listOf(
                Parameter(ID, ParameterType.STRING, "The setting id, for example brightness", maxLength = MAX_ID),
                Parameter(VALUE, ParameterType.STRING, "The new value, for example on, 40, or 60", maxLength = MAX_VALUE),
            ),
        ),
        capabilities = setOf(Capability.SETTINGS_WRITE),
        defaultState = SkillState.ASK,
        scope = ResourceScope.PHONE_SETTINGS,
    ) { arguments ->
        // The policy check ran on these same arguments; this plans again to write exactly that.
        val entry = SettingsCatalog[arguments.string(ID).orEmpty()] ?: return@Skill SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, UNKNOWN, retryable = true)
        when (val plan = SettingChanges.plan(entry, arguments.string(VALUE).orEmpty(), reader)) {
            is ChangePlan.Invalid -> SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, plan.reason, retryable = true)
            is ChangePlan.Planned -> when {
                plan.write is SettingWrite.System && !writer.canWriteSystem() -> SkillOutcome.Failed(DenialCode.ANDROID_PERMISSION_DENIED, NO_GRANT)
                !writer.write(plan.write) -> SkillOutcome.Failed(DenialCode.TOOL_FAILED, "Android did not change ${entry.name}.")
                else -> SkillOutcome.Done("${entry.name} is now ${plan.shown}.")
            }
        }
    }

    private fun open() = Skill(
        id = "open_settings_page",
        version = 1,
        description = "Open the phone's settings page for a setting, so the user can change it. Use for settings Bruce cannot change, such as wifi or bluetooth",
        input = InputSchema(listOf(Parameter(ID, ParameterType.STRING, "The setting id from find_settings, for example wifi", maxLength = MAX_ID))),
        capabilities = setOf(Capability.APP_LAUNCH),
        defaultState = SkillState.ACCEPTED,
    ) { arguments ->
        val entry = SettingsCatalog[arguments.string(ID).orEmpty()] ?: return@Skill SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, UNKNOWN, retryable = true)
        if (writer.open(entry.page)) {
            SkillOutcome.Done("Opened the phone's settings page for ${entry.name}. The user changes it there.")
        } else {
            SkillOutcome.Failed(DenialCode.TOOL_FAILED, "Android did not open the settings page for ${entry.name}.")
        }
    }

    private companion object {
        const val ID = "id"
        const val VALUE = "value"
        const val MAX_QUERY = 100
        const val MAX_ID = 40
        const val MAX_VALUE = 20
        const val UNKNOWN = "Unknown setting id. Find it with find_settings."
        const val NO_GRANT = "The user has not allowed Bruce to modify system settings. They can allow it in Bruce's Settings, Permissions."
    }
}

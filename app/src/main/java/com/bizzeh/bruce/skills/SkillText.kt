package com.bizzeh.bruce.skills

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bizzeh.bruce.R

/** What the user reads about a skill. A skill's [Skill.description] is written for the model, so the screen has its own words. */
object SkillText {
    private val TEXT = mapOf(
        "get_datetime" to (R.string.skill_datetime to R.string.skill_datetime_summary),
        "calculate" to (R.string.skill_calculate to R.string.skill_calculate_summary),
        "get_battery_status" to (R.string.skill_battery to R.string.skill_battery_summary),
        "get_device_info" to (R.string.skill_device to R.string.skill_device_summary),
        "get_storage_status" to (R.string.skill_storage to R.string.skill_storage_summary),
        "get_network_status" to (R.string.skill_network to R.string.skill_network_summary),
        "list_files" to (R.string.skill_list_files to R.string.skill_list_files_summary),
        "read_file" to (R.string.skill_read_file to R.string.skill_read_file_summary),
    )

    fun state(state: SkillState): Int = when (state) {
        SkillState.DECLINED -> R.string.skills_declined
        SkillState.ASK -> R.string.skills_ask
        SkillState.ACCEPTED -> R.string.skills_accepted
    }

    @Composable
    fun name(skill: Skill): String = name(skill.id)

    @Composable
    fun name(skillId: String): String = TEXT[skillId]?.let { stringResource(it.first) } ?: skillId

    @Composable
    fun summary(skill: Skill): String = TEXT[skill.id]?.let { stringResource(it.second) } ?: skill.description
}

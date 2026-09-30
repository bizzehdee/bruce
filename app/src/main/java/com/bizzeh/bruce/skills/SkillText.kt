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
        "create_file" to (R.string.skill_create_file to R.string.skill_create_file_summary),
        "write_file" to (R.string.skill_write_file to R.string.skill_write_file_summary),
        "list_events" to (R.string.skill_list_events to R.string.skill_list_events_summary),
        "create_event" to (R.string.skill_create_event to R.string.skill_create_event_summary),
        "update_event" to (R.string.skill_update_event to R.string.skill_update_event_summary),
        "delete_event" to (R.string.skill_delete_event to R.string.skill_delete_event_summary),
        "rename_file" to (R.string.skill_rename_file to R.string.skill_rename_file_summary),
        "share_text" to (R.string.skill_share_text to R.string.skill_share_text_summary),
        "open_url" to (R.string.skill_open_url to R.string.skill_open_url_summary),
        "open_app" to (R.string.skill_open_app to R.string.skill_open_app_summary),
        "copy_to_clipboard" to (R.string.skill_copy to R.string.skill_copy_summary),
        "write_text_message" to (R.string.skill_message to R.string.skill_message_summary),
        "dial_number" to (R.string.skill_dial to R.string.skill_dial_summary),
        "post_note" to (R.string.skill_post_note to R.string.skill_post_note_summary),
        "get_location" to (R.string.skill_location to R.string.skill_location_summary),
        "find_contact" to (R.string.skill_contact to R.string.skill_contact_summary),
        "take_photo" to (R.string.skill_take_photo to R.string.skill_take_photo_summary),
        "set_reminder" to (R.string.skill_reminder to R.string.skill_reminder_summary),
        "schedule_task" to (R.string.skill_schedule_task to R.string.skill_schedule_task_summary),
        "move_file" to (R.string.skill_move_file to R.string.skill_move_file_summary),
        "delete_file" to (R.string.skill_delete_file to R.string.skill_delete_file_summary),
        "find_settings" to (R.string.skill_find_settings to R.string.skill_find_settings_summary),
        "get_setting" to (R.string.skill_get_setting to R.string.skill_get_setting_summary),
        "set_setting" to (R.string.skill_set_setting to R.string.skill_set_setting_summary),
        "open_settings_page" to (R.string.skill_open_settings_page to R.string.skill_open_settings_page_summary),
        "fetch_page" to (R.string.skill_fetch_page to R.string.skill_fetch_page_summary),
        "web_search" to (R.string.skill_web_search to R.string.skill_web_search_summary),
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

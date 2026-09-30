package com.bizzeh.bruce.notifications

import android.Manifest
import android.os.Build
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState

/** Posts a notification; ReplyNotifications on a phone. */
fun interface NotePoster {
    fun post(title: String, text: String): Boolean
}

/**
 * The notifications skill (TASK-090, owner's option A): Bruce posts a note the user asked for, such
 * as a note to self. It never reads other apps' notifications. Off by default.
 */
class NoteSkills(private val poster: NotePoster, private val sdk: Int = Build.VERSION.SDK_INT) {
    fun create(): List<Skill> = listOf(post())

    private fun post() = Skill(
        id = "post_note",
        version = 1,
        description = "Post a notification on the phone with a note the user asked for, such as a note to self.",
        input = InputSchema(
            listOf(
                Parameter(TITLE, ParameterType.STRING, "A short title", maxLength = MAX_TITLE),
                Parameter(TEXT, ParameterType.STRING, "The note", maxLength = MAX_TEXT),
            ),
        ),
        // The spec's capability list has no notification capability; a note is a message to the user.
        capabilities = setOf(Capability.SHARE),
        defaultState = SkillState.DECLINED,
        androidPermissions = if (sdk >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
    ) { arguments ->
        if (poster.post(arguments.string(TITLE).orEmpty().trim(), arguments.string(TEXT).orEmpty().trim())) {
            SkillOutcome.Done("Posted the note.")
        } else {
            SkillOutcome.Failed(DenialCode.ANDROID_PERMISSION_DENIED, "Android does not let Bruce post notifications; the user can allow them in Android's settings.")
        }
    }

    private companion object {
        const val TITLE = "title"
        const val TEXT = "text"
        const val MAX_TITLE = 100
        const val MAX_TEXT = 1000
    }
}

package com.bizzeh.bruce.skills.contacts

import android.Manifest
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A contact with the details asked for; labels such as "mobile" go with each number or address. */
data class Contact(val name: String, val phones: List<String>, val emails: List<String>)

/** The phone's contacts; AndroidContacts on a phone. */
fun interface ContactsSource {
    /** Contacts whose name matches [name], at most [limit]. */
    fun find(name: String, limit: Int): List<Contact>
}

/**
 * The contacts skill (TASK-092): finds people by name and gives only the detail asked for, so a
 * question about a phone number does not also hand over email addresses. Off by default; the
 * contacts permission is asked for when it is turned on. Names and details are the user's data,
 * passed to the model as untrusted text like any skill result.
 */
class ContactSkills(private val source: ContactsSource, private val io: CoroutineDispatcher) {
    fun create(): List<Skill> = listOf(find())

    private fun find() = Skill(
        id = "find_contact",
        version = 1,
        description = "Find a contact by name and get their phone numbers or email addresses.",
        input = InputSchema(
            listOf(
                Parameter(NAME, ParameterType.STRING, "The person's name, or part of it", maxLength = MAX_NAME),
                Parameter(DETAIL, ParameterType.STRING, "Which detail: phone or email", allowed = listOf(PHONE, EMAIL)),
            ),
        ),
        capabilities = setOf(Capability.CONTACTS),
        defaultState = SkillState.DECLINED,
        androidPermissions = listOf(Manifest.permission.READ_CONTACTS),
    ) { arguments ->
        val name = arguments.string(NAME).orEmpty().trim()
        if (name.length < MIN_NAME) return@Skill SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "Give at least $MIN_NAME letters of the name.", retryable = true)
        val phone = arguments.string(DETAIL) == PHONE
        val found = withContext(io) { source.find(name, MAX_FOUND + 1) }
        if (found.isEmpty()) return@Skill SkillOutcome.Done("No contact matches that name.")
        val lines = found.take(MAX_FOUND).map { contact ->
            val details = if (phone) contact.phones else contact.emails
            "${contact.name}: " + details.ifEmpty { listOf(if (phone) "no phone number" else "no email address") }.joinToString(", ")
        }
        SkillOutcome.Done(lines.joinToString("\n") + if (found.size > MAX_FOUND) "\n[More contacts match; give more of the name.]" else "")
    }

    private companion object {
        const val NAME = "name"
        const val DETAIL = "detail"
        const val PHONE = "phone"
        const val EMAIL = "email"
        const val MIN_NAME = 2
        const val MAX_NAME = 100
        const val MAX_FOUND = 5
    }
}

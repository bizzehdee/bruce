package com.bizzeh.bruce.skills.contacts

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts

/** Contacts through ContactsContract: names first, then only the numbers and addresses of those found. */
class AndroidContacts(private val resolver: ContentResolver, private val resources: Resources) : ContactsSource {
    override fun find(name: String, limit: Int): List<Contact> {
        val matches = resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY),
            nameFilter, arrayOf(likePattern(name)), "${Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            buildList { while (cursor.moveToNext() && size < limit) add(cursor.getLong(0) to cursor.getString(1).orEmpty()) }
        }.orEmpty()
        if (matches.isEmpty()) return emptyList()
        val ids = matches.map { it.first }
        val phones = details(Phone.CONTENT_URI, Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, ids) { type -> Phone.getTypeLabel(resources, type, "").toString() }
        val emails = details(Email.CONTENT_URI, Email.CONTACT_ID, Email.ADDRESS, Email.TYPE, ids) { type -> Email.getTypeLabel(resources, type, "").toString() }
        return matches.map { (id, display) -> Contact(display, phones[id].orEmpty(), emails[id].orEmpty()) }
    }

    override fun count(name: String?): Int =
        if (name == null) resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID), null, null, null)?.use { it.count } ?: 0
        else resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID), nameFilter, arrayOf(likePattern(name)), null)?.use { it.count } ?: 0

    /** "value (label)" for each contact id; the label comes from Android's own type names. */
    private fun details(uri: Uri, contactId: String, value: String, type: String, ids: List<Long>, label: (Int) -> String): Map<Long, List<String>> =
        resolver.query(uri, arrayOf(contactId, value, type), "$contactId IN (${ids.joinToString(",") { "?" }})", ids.map(Long::toString).toTypedArray(), null)
            ?.use { cursor ->
                buildMap<Long, MutableList<String>> {
                    while (cursor.moveToNext()) {
                        val shown = cursor.getString(1)?.trim().orEmpty()
                        if (shown.isEmpty()) continue
                        val kind = if (cursor.isNull(2)) "" else label(cursor.getInt(2))
                        getOrPut(cursor.getLong(0)) { mutableListOf() }.add(if (kind.isEmpty()) shown else "$shown ($kind)")
                    }
                }
            }.orEmpty()

    private companion object {
        /**
         * A direct `LIKE` against the display name, not `Contacts.CONTENT_FILTER_URI`: that filter
         * matches by token prefix (each word's start), so a middle-of-word substring like "ovela" in
         * "Lovelace" never matched — the owner's "search by partial name" (TASK-125, 2026-11-07).
         */
        const val nameFilter = "${Contacts.DISPLAY_NAME_PRIMARY} LIKE ? ESCAPE '\\'"

        /** Escapes `%`/`_`/`\` so [name] is matched literally, with `%` wildcards only at the ends. */
        fun likePattern(name: String) = "%${name.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
    }
}

package com.bizzeh.bruce.skills.contacts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finds a contact made for the test by adb (name "Bruce Zzqtest", mobile +44 7700 900999, email
 * zzq@example.com), searching only that unusual name so no real contact is read. Grant
 * READ_CONTACTS by adb first; delete the contact and revoke after.
 */
@ManualOnly("needs a test contact added by adb")
@RunWith(AndroidJUnit4::class)
class ContactsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contacts = AndroidContacts(context.contentResolver, context.resources)

    @Test
    fun theTestContactIsFoundWithItsDetails() {
        val found = contacts.find("Zzqtest", 5)

        assertEquals(1, found.size)
        assertEquals("Bruce Zzqtest", found.single().name)
        assertEquals(listOf("+44 7700 900999 (Mobile)"), found.single().phones)
        assertEquals(listOf("zzq@example.com (Home)"), found.single().emails)
    }

    /** "zqtes" is a middle-of-word substring of "Zzqtest" — proves the search is not just a prefix match (TASK-125). */
    @Test
    fun theTestContactIsFoundByAMiddleOfWordSubstring() {
        val found = contacts.find("zqtes", 5)

        assertEquals(1, found.size)
        assertEquals("Bruce Zzqtest", found.single().name)
    }

    @Test
    fun theTestContactIsCountedByItsUnusualName() {
        assertEquals(1, contacts.count("Zzqtest"))
    }
}

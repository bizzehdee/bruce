package com.bizzeh.bruce.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric to read the personality rules from the app's raw resources. */
@RunWith(RobolectricTestRunner::class)
class PersonalitySettingsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun bruceByDefaultMiloWhenChosenAndBruceForAnUnknownValue() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { temp.newFile("p.preferences_pb").apply { delete() } }
        val repository = PersonalitySettingsRepository(dataStore)

        assertEquals(Personality.BRUCE, repository.personality.first())
        repository.set(Personality.MILO)
        assertEquals(Personality.MILO, repository.personality.first())
        dataStore.edit { it[stringPreferencesKey("personality")] = "REX" }
        assertEquals(Personality.BRUCE, repository.personality.first())
    }

    @Test
    fun eachPersonalitysRulesAreTheOwnersText() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        fun rules(personality: Personality) = resources.openRawResource(personality.rules).bufferedReader().use { it.readText() }

        assertTrue(rules(Personality.BRUCE).contains("You are Bruce: fast, energetic, confident, loving, helpful, and a little silly."))
        assertTrue(rules(Personality.BRUCE).contains("Never sacrifice accuracy just to be fast."))
        assertTrue(rules(Personality.MILO).contains("You are Milo: curious, observant, thoughtful, patient, and quiet."))
        assertTrue(rules(Personality.MILO).contains("Avoid overthinking or becoming indecisive."))
        assertEquals(listOf("Bruce", "Milo"), Personality.entries.map { it.displayName })
    }
}

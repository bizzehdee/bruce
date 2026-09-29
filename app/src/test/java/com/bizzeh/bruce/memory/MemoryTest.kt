package com.bizzeh.bruce.memory

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.ui.theme.BruceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MemoryDatabase::class.java).allowMainThreadQueries().build()
    private var now = 1_000L
    private val store = MemoryStore(database.facts()) { now++ }

    @After
    fun tearDown() = database.close()

    private fun texts(scope: String) = runBlocking { store.facts.first().filter { it.scope == scope }.map { it.text } }

    @Test
    fun factsAreKeptOncePerScopeTrimmedAndBounded() = runBlocking {
        assertEquals(2, store.add("global", listOf(" My name is Sam. ", "my name is  sam", "I live in Leeds", "", "x".repeat(MemoryStore.MAX_FACT_CHARS + 1))))
        assertEquals(0, store.add("global", listOf("My name is Sam")))
        assertEquals(0, store.add("global", listOf("\"I live in Leeds.\"")))
        assertEquals(1, store.add("m.gguf", listOf("My name is Sam")))

        assertEquals(listOf("I live in Leeds", "My name is Sam."), texts("global"))
        assertEquals(listOf("My name is Sam"), texts("m.gguf"))
    }

    @Test
    fun eachScopeKeepsOnlyItsNewestFacts() = runBlocking {
        (1..MemoryStore.MAX_FACTS_PER_SCOPE + 3).forEach { store.add("global", listOf("fact number $it")) }

        val kept = texts("global")
        assertEquals(MemoryStore.MAX_FACTS_PER_SCOPE, kept.size)
        assertEquals("fact number ${MemoryStore.MAX_FACTS_PER_SCOPE + 3}", kept.first())
    }

    @Test
    fun recallRanksByWordsSharedWithTheMessageWithinLimits() = runBlocking {
        store.add("global", listOf("My dog is called Bruce", "I live in Leeds", "My sister lives in Leeds too", "I like tea"))

        assertEquals(listOf("My sister lives in Leeds too", "I live in Leeds"), store.recall("global", "Any sister events in Leeds?"))
        assertEquals(listOf("My dog is called Bruce"), store.recall("global", "What is my dog called?", limit = 1))
        assertEquals(emptyList<String>(), store.recall("global", "Hi"))
        assertEquals(emptyList<String>(), store.recall("global", "Leeds", maxChars = 5))
        assertEquals(emptyList<String>(), store.recall("other", "Leeds"))
    }

    @Test
    fun deletingOneOrAll() = runBlocking {
        store.add("global", listOf("a fact", "another fact"))
        store.delete(store.facts.first().first().id)
        assertEquals(1, store.facts.first().size)
        store.deleteAll()
        assertEquals(0, store.facts.first().size)
    }

    @Test
    fun theModeDecidesTheScope() {
        assertEquals(null, MemoryStore.scope(MemoryMode.OFF, "m.gguf"))
        assertEquals("global", MemoryStore.scope(MemoryMode.GLOBAL, "m.gguf"))
        assertEquals("m.gguf", MemoryStore.scope(MemoryMode.PER_MODEL, "m.gguf"))
        assertEquals(null, MemoryStore.scope(MemoryMode.PER_MODEL, null))
    }

    @Test
    fun memoryIsOffUntilChosenAndUnknownValuesMeanOff() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create { temp.newFile("s.preferences_pb").also { it.delete() } }
        val settings = MemorySettingsRepository(dataStore)
        assertEquals(MemoryMode.OFF, settings.mode.first())
        settings.setMode(MemoryMode.GLOBAL)
        assertEquals(MemoryMode.GLOBAL, settings.mode.first())
        dataStore.edit { it[stringPreferencesKey("memory_mode")] = "ALWAYS" }
        assertEquals(MemoryMode.OFF, settings.mode.first())
    }

    @Test
    fun theScreenListsDeletesAndConfirmsDeletingAll() {
        val calls = mutableListOf<String>()
        val actions = object : MemoryActions {
            override fun delete(fact: Fact) { calls += "delete ${fact.id}" }
            override fun deleteAll() { calls += "all" }
        }
        val facts = listOf(Fact(1, "global", "I live in Leeds", 0), Fact(2, "m.gguf", "My dog is Bruce", 0))
        compose.setContent { BruceTheme { MemoryScreen(facts, actions) {} } }

        compose.onNodeWithText("I live in Leeds").assertIsDisplayed()
        compose.onNodeWithText("All models").assertIsDisplayed()
        compose.onNodeWithText("m.gguf only").assertIsDisplayed()
        compose.onNodeWithTag("deleteFact:2").performClick()
        compose.onNodeWithTag("deleteAllFacts").performClick()
        compose.onNodeWithTag("confirmDeleteAllFacts").performClick()

        assertEquals(listOf("delete 2", "all"), calls)
    }

    @Test
    fun anEmptyMemorySaysSo() {
        compose.setContent { BruceTheme { MemoryScreen(emptyList(), object : MemoryActions {
            override fun delete(fact: Fact) = Unit
            override fun deleteAll() = Unit
        }) {} } }

        compose.onNodeWithTag("memoryEmpty").assertIsDisplayed()
        compose.onNodeWithTag("deleteAllFacts").assertDoesNotExist()
    }
}

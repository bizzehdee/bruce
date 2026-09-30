package com.bizzeh.bruce.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.testing.FakeDocuments
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchSkillsTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SearchDatabase::class.java).allowMainThreadQueries().build()

    /** Queries mentioning "dog" match the dog passage fully; anything else barely. */
    private val embedder = object : Embedder {
        override suspend fun passage(text: String) = if ("dog" in text) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
        override suspend fun query(text: String) = if ("dog" in text) floatArrayOf(1f, 0f) else floatArrayOf(0.1f, 0.2f)
        override suspend fun release() = Unit
    }
    private val search = FileSearch(database.search(), { emptyList() }, FakeDocuments(), embedder, { _, _, _ -> null })
    private val skill = SearchSkills(search).create().single()

    @After
    fun tearDown() = database.close()

    private fun run(query: String) = runBlocking { skill.execute(SkillArguments(mapOf("query" to query))) }

    @Test
    fun offUntilSomethingIsIndexed() {
        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(setOf(SkillRequirement.FILE_INDEX), skill.requires)
    }

    @Test
    fun matchingPassagesComeWithTheirFileAndWeakOnesAreDropped() = runBlocking {
        database.search().replace(IndexedFileEntity("u1", 1, "Notes/dog.txt", 1), listOf(PassageEntity(documentUri = "u1", position = 0, text = "The dog sees the vet.", vector = Vectors.toBytes(floatArrayOf(1f, 0f)))))
        database.search().replace(IndexedFileEntity("u2", 1, "Notes/tax.txt", 1), listOf(PassageEntity(documentUri = "u2", position = 0, text = "Tax receipts.", vector = Vectors.toBytes(floatArrayOf(0f, 1f)))))

        assertEquals(SkillOutcome.Done("From Notes/dog.txt:\nThe dog sees the vet."), run("dog vet"))
        assertEquals(SkillOutcome.Done("Nothing in the user's files matches that."), run("capital of Mongolia"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run(" ") as SkillOutcome.Failed).code)
    }
}

package com.bizzeh.bruce.conversations

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.chat.ChatEntry
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/** Against a real (in-memory) Room database. Room runs queries on its own threads, so these block rather than use a test scheduler. */
@RunWith(RobolectricTestRunner::class)
class ConversationStoreTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ConversationDatabase::class.java).build()
    private var now = 1_000L
    private val store = ConversationStore(database.conversations()) { now }

    @After
    fun tearDown() = database.close()

    private fun user(text: String) = ChatEntry(ChatRole.USER, text)
    private fun reply(text: String) = ChatEntry(ChatRole.ASSISTANT, text)

    @Test
    fun savingANewChatTitlesItFromTheFirstMessage() = runBlocking {
        val stats = GenerationStats(1, 1.seconds, 1, 1.seconds)

        val id = store.save(null, listOf(user("  What is   the capital of France? "), reply("Paris.").copy(stats = stats)))

        assertEquals(listOf(Conversation(id, "What is the capital of France?", 1_000, archived = false)), store.active.first())
        assertEquals("stats are not saved", listOf(user("  What is   the capital of France? "), reply("Paris.")), store.load(id))
    }

    @Test
    fun laterSavesReplaceMessagesAndMoveTheChatToTheTop() = runBlocking {
        val first = store.save(null, listOf(user("one")))
        now = 2_000
        val second = store.save(null, listOf(user("two")))
        now = 3_000

        assertEquals(first, store.save(first, listOf(user("one"), reply("1"), user("again"))))

        assertEquals(listOf(first, second), store.active.first().map { it.id })
        assertEquals(listOf("one", "1", "again"), store.load(first)!!.map { it.text })
        assertEquals("the title stays", "one", store.active.first().first().title)
    }

    @Test
    fun savingToADeletedChatStartsANewOne() = runBlocking {
        val gone = store.save(null, listOf(user("old")))
        store.delete(setOf(gone))

        val id = store.save(gone, listOf(user("old"), reply("late")))

        assertTrue(id != gone)
        assertEquals(listOf("old", "late"), store.load(id)!!.map { it.text })
        assertNull(store.load(gone))
    }

    @Test
    fun renameTrimsCapsAndIgnoresBlank() = runBlocking {
        val id = store.save(null, listOf(user("hello")))

        store.rename(id, "   ")
        assertEquals("hello", store.active.first().single().title)
        store.rename(id, "  Trip plans  ")
        assertEquals("Trip plans", store.active.first().single().title)
        store.rename(id, "x".repeat(300))
        assertEquals(ConversationStore.MAX_TITLE_LENGTH, store.active.first().single().title.length)
    }

    @Test
    fun archiveRestoreAndDeleteSeveralAtOnce() = runBlocking {
        val a = store.save(null, listOf(user("a")))
        val b = store.save(null, listOf(user("b")))
        val c = store.save(null, listOf(user("c")))

        store.archive(setOf(a, b))
        assertEquals(listOf(c), store.active.first().map { it.id })
        assertEquals(setOf(a, b), store.archived.first().map { it.id }.toSet())
        assertTrue(store.archived.first().all { it.archived })

        store.restore(setOf(a))
        store.delete(setOf(b, c))

        assertEquals(listOf(a), store.active.first().map { it.id })
        assertTrue(store.archived.first().isEmpty())
        assertEquals("messages go with their chat", emptyList<MessageEntity>(), database.conversations().messages(b))
    }

    @Test
    fun deleteAllRemovesArchivedToo() = runBlocking {
        store.archive(setOf(store.save(null, listOf(user("a")))))
        store.save(null, listOf(user("b")))

        store.deleteAll()

        assertTrue(store.active.first().isEmpty())
        assertTrue(store.archived.first().isEmpty())
    }

    @Test
    fun messagesWithAnUnknownRoleAreSkipped() = runBlocking {
        val id = store.save(null, listOf(user("hi")))
        database.conversations().insert(listOf(MessageEntity(conversationId = id, position = 1, role = "FUTURE_ROLE", text = "?")))

        assertEquals(listOf(user("hi")), store.load(id))
    }

    @Test
    fun longFirstMessagesAreCutAtAWord() {
        assertEquals(
            "Please help me write a cover letter for…",
            ConversationStore.titleFor(listOf(reply("ignored"), user("Please help me write a cover letter for a job in Leeds"))),
        )
        assertEquals("a".repeat(40) + "…", ConversationStore.titleFor(listOf(user("a".repeat(60)))))
        assertEquals("", ConversationStore.titleFor(emptyList()))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConversationsViewModelTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ConversationDatabase::class.java).build()
    private val store = ConversationStore(database.conversations())
    private val removed = mutableListOf<Set<Long>>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun archivingAndDeletingTellTheChatWhatLeft() = runBlocking {
        // Room completes on its own threads; the view model's launches run straight through to them.
        Dispatchers.setMain(Dispatchers.Unconfined)
        val a = store.save(null, listOf(ChatEntry(ChatRole.USER, "a")))
        val b = store.save(null, listOf(ChatEntry(ChatRole.USER, "b")))
        val vm = ConversationsViewModel(store) { removed += it }

        vm.archive(setOf(a))
        store.archived.first { it.size == 1 }
        vm.restore(setOf(a))
        store.active.first { it.size == 2 }
        vm.rename(b, "Bee")
        store.active.first { list -> list.any { it.title == "Bee" } }
        vm.active.first { it.size == 2 }
        vm.deleteAll()
        store.active.first { it.isEmpty() }
        vm.delete(setOf(99))

        waitFor { removed.size == 3 }
        assertEquals(listOf(setOf(a), setOf(a, b), setOf(99L)), removed)
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }
}

package com.bizzeh.bruce.conversations

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.inference.ChatRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chats saved by earlier versions survive the upgrades to the current one. Each old database is
 * built from its exported schema (app/schemas, added to the device-test assets), and Room validates
 * the migrated tables against the current version when it opens them. Room's own MigrationTestHelper is not
 * used: it needs a newer kotlinx-serialization than the app ships.
 */
@RunWith(AndroidJUnit4::class)
class ConversationMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = instrumentation.targetContext.getDatabasePath("migration-test.db")

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(file.name)
    }

    private fun create(version: Int, rows: List<String>) {
        val schema = instrumentation.context.assets.open("${ConversationDatabase::class.java.name}/$version.json").bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            rows.forEach(db::execSQL)
            db.version = version
        }
    }

    private val version1Rows = listOf(
        "INSERT INTO conversations (id, title, createdAt, updatedAt, archived) VALUES (1, 'Old chat', 10, 20, 0)",
        "INSERT INTO messages (id, conversationId, position, role, text) VALUES (1, 1, 0, 'USER', 'Hello')",
        "INSERT INTO messages (id, conversationId, position, role, text) VALUES (2, 1, 1, 'ASSISTANT', 'Hi there')",
    )

    private fun open() = Room.databaseBuilder(instrumentation.targetContext, ConversationDatabase::class.java, file.name)
        .addMigrations(ConversationDatabase.MIGRATION_1_2, ConversationDatabase.MIGRATION_2_3, ConversationDatabase.MIGRATION_3_4, ConversationDatabase.MIGRATION_4_5)
        .build()

    @Test
    fun version1ChatsSurviveTheUpgrade() = runBlocking {
        create(1, version1Rows)

        val database = open()
        val store = ConversationStore(database.conversations())

        assertEquals(listOf("Old chat"), store.active.first().map { it.title })
        val messages = store.load(1)!!
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), messages.map { it.role })
        assertEquals(listOf("Hello", "Hi there"), messages.map { it.text })
        assertEquals(emptyList<Any>(), messages.flatMap { it.toolCalls })
        database.close()
    }

    @Test
    fun version2ToolResultsSurviveAndHaveNoImages() = runBlocking {
        create(2, version1Rows + "INSERT INTO messages (id, conversationId, position, role, text, toolCallId, toolName, toolStatus) VALUES (3, 1, 2, 'TOOL', '{}', 'c1', 'get_datetime', 'RAN')")

        val database = open()
        val messages = ConversationStore(database.conversations()).load(1)!!

        assertEquals("get_datetime", messages.last().tool?.name)
        assertEquals(emptyList<String>(), messages.flatMap { it.images })
        database.close()
    }

    @Test
    fun version3ImagesSurviveAndHaveNoDocuments() = runBlocking {
        val image = "0d49eb73-53a0-464b-9e5a-3eca3d64eb9d.jpg"
        create(3, version1Rows + "INSERT INTO messages (id, conversationId, position, role, text, imagesJson) VALUES (3, 1, 2, 'USER', 'Look', '[\"$image\"]')")

        val database = open()
        val messages = ConversationStore(database.conversations()).load(1)!!

        assertEquals(listOf(image), messages.last().images)
        assertEquals(emptyList<Any>(), messages.flatMap { it.documents })
        database.close()
    }

    @Test
    fun version4ChatsHaveNoProfile() = runBlocking {
        create(4, version1Rows)

        val database = open()
        val store = ConversationStore(database.conversations())
        assertEquals(null, store.profile(1))
        store.setProfile(1, "fast")
        assertEquals("fast", store.profile(1))
        database.close()
    }
}

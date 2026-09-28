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
 * Chats saved by version 1 survive the upgrade to version 2. The version 1 database is built from
 * the exported schema (app/schemas, added to the device-test assets), and Room validates the
 * migrated tables against version 2 when it opens them. Room's own MigrationTestHelper is not
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

    private fun createVersion1() {
        val schema = instrumentation.context.assets.open("${ConversationDatabase::class.java.name}/1.json").bufferedReader().use { JSONObject(it.readText()) }
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
            db.execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived) VALUES (1, 'Old chat', 10, 20, 0)")
            db.execSQL("INSERT INTO messages (id, conversationId, position, role, text) VALUES (1, 1, 0, 'USER', 'Hello')")
            db.execSQL("INSERT INTO messages (id, conversationId, position, role, text) VALUES (2, 1, 1, 'ASSISTANT', 'Hi there')")
            db.version = 1
        }
    }

    @Test
    fun version1ChatsSurviveTheUpgrade() = runBlocking {
        createVersion1()

        val database = Room.databaseBuilder(instrumentation.targetContext, ConversationDatabase::class.java, file.name)
            .addMigrations(ConversationDatabase.MIGRATION_1_2)
            .build()
        val store = ConversationStore(database.conversations())

        assertEquals(listOf("Old chat"), store.active.first().map { it.title })
        val messages = store.load(1)!!
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), messages.map { it.role })
        assertEquals(listOf("Hello", "Hi there"), messages.map { it.text })
        assertEquals(emptyList<Any>(), messages.flatMap { it.toolCalls })
        database.close()
    }
}

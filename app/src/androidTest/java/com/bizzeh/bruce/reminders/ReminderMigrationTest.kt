package com.bizzeh.bruce.reminders

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reminders saved by version 1 survive the upgrade that adds scheduled tasks (built from app/schemas). */
@RunWith(AndroidJUnit4::class)
class ReminderMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = instrumentation.targetContext.getDatabasePath("reminders-migration-test.db")

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(file.name)
    }

    @Test
    fun version1RemindersSurvive() = runBlocking {
        val schema = instrumentation.context.assets.open("${ReminderDatabase::class.java.name}/1.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("database")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO reminders (id, text, atMillis) VALUES (1, 'Tea', 5000)")
            db.version = 1
        }

        val database = Room.databaseBuilder(instrumentation.targetContext, ReminderDatabase::class.java, file.name).addMigrations(ReminderDatabase.MIGRATION_1_2).build()
        assertEquals(listOf("Tea"), database.reminders().allNow().map { it.text })
        assertTrue(database.tasks().allNow().isEmpty())
        assertTrue(database.tasks().all().first().isEmpty())
        database.close()
    }
}

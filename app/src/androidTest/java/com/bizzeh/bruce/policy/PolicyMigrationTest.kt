package com.bizzeh.bruce.policy

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Skill states saved by version 1 survive the upgrade that adds grants. Built from the exported
 * schema as ConversationMigrationTest does; Room validates the migrated tables against version 2.
 */
@RunWith(AndroidJUnit4::class)
class PolicyMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = instrumentation.targetContext.getDatabasePath("policy-migration-test.db")

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(file.name)
    }

    private fun createVersion1() {
        val schema = instrumentation.context.assets.open("${PolicyDatabase::class.java.name}/1.json").bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO skill_states (skillId, state, updatedAt) VALUES ('get_datetime', 'DECLINED', 10)")
            db.execSQL("INSERT INTO policy_version (id, version) VALUES (0, 7)")
            db.version = 1
        }
    }

    @Test
    fun version1StatesSurviveAndGrantsStartEmpty() = runBlocking {
        createVersion1()

        val database = Room.databaseBuilder(instrumentation.targetContext, PolicyDatabase::class.java, file.name)
            .addMigrations(PolicyDatabase.MIGRATION_1_2)
            .build()
        val clock = Skill("get_datetime", 1, "Time.", InputSchema(), emptySet(), SkillState.ACCEPTED) { SkillOutcome.Done("") }

        assertEquals(SkillState.DECLINED, SkillStateStore(database.policy()).state(clock))
        assertEquals(7L, database.policy().version())
        assertEquals(emptyList<GrantEntity>(), database.policy().allGrants())
        database.close()
    }
}

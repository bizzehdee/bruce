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
 * Skill states saved by version 1 survive the upgrade that adds grants, and grants survive the one that adds folder instructions. Built from the exported
 * schema as ConversationMigrationTest does; Room validates the migrated tables against the current version.
 */
@RunWith(AndroidJUnit4::class)
class PolicyMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = instrumentation.targetContext.getDatabasePath("policy-migration-test.db")

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(file.name)
    }

    private fun create(version: Int, vararg rows: String) {
        val schema = instrumentation.context.assets.open("${PolicyDatabase::class.java.name}/$version.json").bufferedReader().use { JSONObject(it.readText()) }
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
            rows.forEach(db::execSQL)
            db.version = version
        }
    }

    @Test
    fun version1StatesSurviveAndGrantsStartEmpty() = runBlocking {
        create(1, "INSERT INTO skill_states (skillId, state, updatedAt) VALUES ('get_datetime', 'DECLINED', 10)", "INSERT INTO policy_version (id, version) VALUES (0, 7)")

        val database = Room.databaseBuilder(instrumentation.targetContext, PolicyDatabase::class.java, file.name)
            .addMigrations(PolicyDatabase.MIGRATION_1_2, PolicyDatabase.MIGRATION_2_3, PolicyDatabase.MIGRATION_3_4)
            .build()
        val clock = Skill("get_datetime", 1, "Time.", InputSchema(), emptySet(), SkillState.ACCEPTED) { SkillOutcome.Done("") }

        assertEquals(SkillState.DECLINED, SkillStateStore(database.policy()).state(clock))
        assertEquals(7L, database.policy().version())
        assertEquals(emptyList<GrantEntity>(), database.policy().allGrants())
        database.close()
    }

    @Test
    fun version2GrantsSurviveAndNoInstructionsAreFollowedYet() = runBlocking {
        create(2, "INSERT INTO grants (id, uri, name, kind, grantedAt) VALUES (4, 'content://docs/tree/a', 'Documents', 'FOLDER', 10)")

        val database = Room.databaseBuilder(instrumentation.targetContext, PolicyDatabase::class.java, file.name)
            .addMigrations(PolicyDatabase.MIGRATION_2_3, PolicyDatabase.MIGRATION_3_4)
            .build()

        assertEquals(listOf("Documents"), database.policy().allGrants().map { it.name })
        assertEquals(null, database.policy().instructionChoice(4))
        database.policy().setInstructionChoice(FolderInstructionsEntity(4, "h", follow = true))
        database.policy().removeGrant(4)
        assertEquals(null, database.policy().instructionChoice(4))
        database.close()
    }

    @Test
    fun version3ChoicesSurviveAndNoSiteIsApprovedYet() = runBlocking {
        create(3, "INSERT INTO folder_instructions (grantId, hash, follow) VALUES (4, 'h', 1)")

        val database = Room.databaseBuilder(instrumentation.targetContext, PolicyDatabase::class.java, file.name)
            .addMigrations(PolicyDatabase.MIGRATION_3_4)
            .build()

        assertEquals("h", database.policy().instructionChoice(4)?.hash)
        assertEquals(false, database.policy().isApprovedSite("example.com"))
        database.policy().approveSite(ApprovedSiteEntity("example.com", 1))
        assertEquals(true, database.policy().isApprovedSite("example.com"))
        database.close()
    }
}

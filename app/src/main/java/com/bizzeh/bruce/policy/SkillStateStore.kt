package com.bizzeh.bruce.policy

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "skill_states")
data class SkillStateEntity(@PrimaryKey val skillId: String, val state: String, val updatedAt: Long)

/** One row: bumped on every policy change, so a confirmation made under an older policy can be refused (TASK-041). */
@Entity(tableName = "policy_version")
data class PolicyVersionEntity(@PrimaryKey val id: Int = 0, val version: Long)

@Dao
interface PolicyDao {
    @Query("SELECT * FROM approved_sites ORDER BY host")
    fun approvedSites(): Flow<List<ApprovedSiteEntity>>

    @Query("SELECT COUNT(*) > 0 FROM approved_sites WHERE host = :host")
    suspend fun isApprovedSite(host: String): Boolean

    @Upsert
    suspend fun approveSite(site: ApprovedSiteEntity)

    @Query("DELETE FROM approved_sites WHERE host = :host")
    suspend fun removeSite(host: String)

    @Query("DELETE FROM approved_sites")
    suspend fun clearSites()

    @Query("SELECT * FROM skill_states")
    fun states(): Flow<List<SkillStateEntity>>

    @Query("SELECT state FROM skill_states WHERE skillId = :skillId")
    suspend fun state(skillId: String): String?

    @Upsert
    suspend fun upsert(state: SkillStateEntity)

    @Query("SELECT version FROM policy_version WHERE id = 0")
    suspend fun version(): Long?

    @Upsert
    suspend fun setVersion(version: PolicyVersionEntity)

    @Transaction
    suspend fun change(state: SkillStateEntity) {
        upsert(state)
        bumpVersion()
    }

    @Query("DELETE FROM skill_states")
    suspend fun clearStates()

    @Query("SELECT * FROM grants ORDER BY grantedAt, id")
    fun grants(): Flow<List<GrantEntity>>

    @Query("SELECT * FROM grants ORDER BY grantedAt, id")
    suspend fun allGrants(): List<GrantEntity>

    @Insert
    suspend fun insertGrant(grant: GrantEntity): Long

    @Query("DELETE FROM grants WHERE id = :id")
    suspend fun deleteGrant(id: Long)

    @Query("DELETE FROM grants")
    suspend fun clearGrants()

    @Query("SELECT * FROM folder_instructions")
    fun instructionChoices(): Flow<List<FolderInstructionsEntity>>

    @Query("SELECT * FROM folder_instructions WHERE grantId = :grantId")
    suspend fun instructionChoice(grantId: Long): FolderInstructionsEntity?

    @Upsert
    suspend fun setInstructionChoice(choice: FolderInstructionsEntity)

    @Query("DELETE FROM folder_instructions WHERE grantId = :grantId")
    suspend fun deleteInstructionChoice(grantId: Long)

    @Query("DELETE FROM folder_instructions")
    suspend fun clearInstructionChoices()

    @Transaction
    suspend fun addGrant(grant: GrantEntity): Long = insertGrant(grant).also { bumpVersion() }

    @Transaction
    suspend fun removeGrant(id: Long) {
        deleteGrant(id)
        deleteInstructionChoice(id)
        bumpVersion()
    }

    @Transaction
    suspend fun resetGrants() {
        clearGrants()
        clearInstructionChoices()
        bumpVersion()
    }

    suspend fun bumpVersion() = setVersion(PolicyVersionEntity(version = (version() ?: 0) + 1))

    @Transaction
    suspend fun reset() {
        clearStates()
        bumpVersion()
    }
}

/** A file or folder the user granted through the Storage Access Framework (TASK-040). */
@Entity(tableName = "grants")
data class GrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A tree URI for a folder, a document URI for a file. */
    val uri: String,
    /** Unique among grants: the first segment of every path the model gives. */
    val name: String,
    /** A [GrantKind] name. */
    val kind: String,
    val grantedAt: Long,
)

/** The user's choice about a granted folder's instructions (TASK-049), valid only while its files hash to [hash]. */
@Entity(tableName = "folder_instructions")
data class FolderInstructionsEntity(@PrimaryKey val grantId: Long, val hash: String, val follow: Boolean)

/** A web site the user always allows in the Approved sites network mode (TASK-068). */
@Entity(tableName = "approved_sites")
data class ApprovedSiteEntity(@PrimaryKey val host: String, val approvedAt: Long)

@Database(entities = [SkillStateEntity::class, PolicyVersionEntity::class, GrantEntity::class, FolderInstructionsEntity::class, ApprovedSiteEntity::class], version = 4)
abstract class PolicyDatabase : RoomDatabase() {
    abstract fun policy(): PolicyDao

    companion object {
        const val NAME = "policy.db"

        /** Version 2 (TASK-040): file and folder grants. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `grants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `grantedAt` INTEGER NOT NULL)")
            }
        }

        /** Version 4 (TASK-068): sites the user always allows. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `approved_sites` (`host` TEXT NOT NULL, `approvedAt` INTEGER NOT NULL, PRIMARY KEY(`host`))")
            }
        }

        /** Version 3 (TASK-049): the choice about each folder's instructions. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `folder_instructions` (`grantId` INTEGER NOT NULL, `hash` TEXT NOT NULL, `follow` INTEGER NOT NULL, PRIMARY KEY(`grantId`))")
            }
        }
    }
}

sealed interface StateChange {
    data object Changed : StateChange

    /** Accepting a high-risk skill needs the user to have accepted the warning first. */
    data object WarningNotAccepted : StateChange
}

/**
 * The user's skill states. Only the Skills screen changes them; nothing the model can reach
 * holds this store's setters.
 */
class SkillStateStore(
    private val dao: PolicyDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Stored choices by skill id; skills absent here use their defaults. */
    val stored: Flow<Map<String, SkillState>> = dao.states().map { rows -> rows.associate { it.skillId to parse(it.state) } }

    suspend fun state(skill: Skill): SkillState = dao.state(skill.id)?.let(::parse) ?: skill.defaultState

    suspend fun policyVersion(): Long = dao.version() ?: 0

    suspend fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean = false): StateChange {
        if (skill.highRisk && state == SkillState.ACCEPTED && !highRiskWarningAccepted) return StateChange.WarningNotAccepted
        dao.change(SkillStateEntity(skill.id, state.name, clock()))
        return StateChange.Changed
    }

    /** Back to every skill's default, as after a fresh install. */
    suspend fun reset() = dao.reset()

    /** A value this version cannot read is treated as Declined, the safe side. */
    private fun parse(stored: String): SkillState = SkillState.entries.firstOrNull { it.name == stored } ?: SkillState.DECLINED
}

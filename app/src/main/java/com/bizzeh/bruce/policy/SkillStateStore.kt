package com.bizzeh.bruce.policy

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
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
        setVersion(PolicyVersionEntity(version = (version() ?: 0) + 1))
    }

    @Query("DELETE FROM skill_states")
    suspend fun clearStates()

    @Transaction
    suspend fun reset() {
        clearStates()
        setVersion(PolicyVersionEntity(version = (version() ?: 0) + 1))
    }
}

@Database(entities = [SkillStateEntity::class, PolicyVersionEntity::class], version = 1)
abstract class PolicyDatabase : RoomDatabase() {
    abstract fun policy(): PolicyDao

    companion object {
        const val NAME = "policy.db"
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

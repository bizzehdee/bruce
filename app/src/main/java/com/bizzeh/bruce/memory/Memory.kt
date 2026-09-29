package com.bizzeh.bruce.memory

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Memory (plan.md, TASK-047): off by default; per model, each model keeps its own; or one shared memory. */
enum class MemoryMode { OFF, PER_MODEL, GLOBAL }

class MemorySettingsRepository(private val dataStore: DataStore<Preferences>) {
    val mode: Flow<MemoryMode> = dataStore.data.map { preferences ->
        MemoryMode.entries.firstOrNull { it.name == preferences[MODE] } ?: MemoryMode.OFF
    }

    suspend fun setMode(mode: MemoryMode) {
        dataStore.edit { it[MODE] = mode.name }
    }

    private companion object {
        val MODE = stringPreferencesKey("memory_mode")
    }
}

/** One remembered fact. [scope] is [MemoryStore.GLOBAL] or a model's file name. */
@Entity(tableName = "facts", indices = [Index("scope")])
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val text: String,
    val createdAt: Long,
)

@Dao
interface FactDao {
    @Query("SELECT * FROM facts ORDER BY createdAt DESC, id DESC")
    fun all(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts WHERE scope = :scope ORDER BY createdAt DESC, id DESC")
    suspend fun inScope(scope: String): List<FactEntity>

    @Insert
    suspend fun insert(facts: List<FactEntity>)

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM facts")
    suspend fun deleteAll()

    /** Keeps the newest [keep] facts in [scope]. */
    @Query("DELETE FROM facts WHERE scope = :scope AND id NOT IN (SELECT id FROM facts WHERE scope = :scope ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(scope: String, keep: Int)

    @Transaction
    suspend fun add(scope: String, facts: List<FactEntity>, keep: Int) {
        insert(facts)
        trim(scope, keep)
    }
}

@Database(entities = [FactEntity::class], version = 1)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun facts(): FactDao

    companion object {
        const val NAME = "memory.db"
    }
}

data class Fact(val id: Long, val scope: String, val text: String, val createdAt: Long)

/**
 * The remembered facts. Facts come from the model reading the user's own chats, so they are kept
 * short, deduplicated and capped, and go back to the model as data, never as instructions.
 */
class MemoryStore(private val dao: FactDao, private val clock: () -> Long = System::currentTimeMillis) {
    val facts: Flow<List<Fact>> = dao.all().map { rows -> rows.map { Fact(it.id, it.scope, it.text, it.createdAt) } }

    /** Adds the facts not already known in [scope] (ignoring case and spacing); returns how many were new. */
    suspend fun add(scope: String, candidates: List<String>): Int {
        val known = dao.inScope(scope).mapTo(mutableSetOf()) { normalise(it.text) }
        val now = clock()
        val fresh = candidates.map { it.trim().trim('"', '“', '”').trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_FACT_CHARS }
            .distinctBy(::normalise)
            .filter { normalise(it) !in known }
        if (fresh.isEmpty()) return 0
        dao.add(scope, fresh.map { FactEntity(scope = scope, text = it, createdAt = now) }, MAX_FACTS_PER_SCOPE)
        return fresh.size
    }

    /**
     * The facts in [scope] most relevant to [message]: ranked by how many of its words they share,
     * newest first among equals, at most [limit] and [maxChars] characters in all.
     */
    suspend fun recall(scope: String, message: String, limit: Int = RECALL_LIMIT, maxChars: Int = RECALL_CHARS): List<String> {
        val words = words(message)
        val ranked = dao.inScope(scope)
            .map { it.text to words(it.text).count(words::contains) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val chosen = mutableListOf<String>()
        var chars = 0
        for ((text, _) in ranked) {
            if (chosen.size == limit || chars + text.length > maxChars) break
            chosen += text
            chars += text.length
        }
        return chosen
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()

    private fun normalise(text: String) = text.lowercase().replace(Regex("\\s+"), " ").trim().trim('"', '\'', '“', '”').trim().trimEnd('.')

    /** Words of three letters or more, so "a", "is" and the like do not count as overlap. */
    private fun words(text: String): Set<String> =
        Regex("[\\p{L}\\p{N}]{3,}").findAll(text.lowercase()).mapTo(mutableSetOf()) { it.value }

    companion object {
        const val GLOBAL = "global"
        const val MAX_FACT_CHARS = 200
        const val MAX_FACTS_PER_SCOPE = 200
        const val RECALL_LIMIT = 10

        /** About 200 tokens. */
        const val RECALL_CHARS = 800

        fun scope(mode: MemoryMode, modelFileName: String?): String? = when (mode) {
            MemoryMode.OFF -> null
            MemoryMode.GLOBAL -> GLOBAL
            MemoryMode.PER_MODEL -> modelFileName
        }
    }
}

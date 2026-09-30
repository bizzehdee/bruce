package com.bizzeh.bruce.search

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A file in the index, with when it last changed, so unchanged files are not embedded again. */
@Entity(tableName = "files")
data class IndexedFileEntity(
    @PrimaryKey val documentUri: String,
    val grantId: Long,
    /** The path the model names it by: `<grant name>/<folder>/<file>`. */
    val path: String,
    val modified: Long,
)

@Entity(tableName = "passages", indices = [Index("documentUri")])
data class PassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentUri: String,
    val position: Int,
    val text: String,
    /** Little-endian float32s. */
    val vector: ByteArray,
)

data class Passage(val path: String, val position: Int, val text: String)

@Dao
interface SearchDao {
    @Query("SELECT * FROM files")
    suspend fun files(): List<IndexedFileEntity>

    @Query("SELECT COUNT(*) FROM passages")
    suspend fun passageCount(): Int

    @Query("SELECT passages.* FROM passages")
    suspend fun passages(): List<PassageEntity>

    @Insert
    suspend fun insertFile(file: IndexedFileEntity)

    @Insert
    suspend fun insertPassages(passages: List<PassageEntity>)

    @Query("DELETE FROM files WHERE documentUri = :uri")
    suspend fun deleteFile(uri: String)

    @Query("DELETE FROM passages WHERE documentUri = :uri")
    suspend fun deletePassages(uri: String)

    @Transaction
    suspend fun replace(file: IndexedFileEntity, passages: List<PassageEntity>) {
        remove(file.documentUri)
        insertFile(file)
        insertPassages(passages)
    }

    @Transaction
    suspend fun remove(uri: String) {
        deletePassages(uri)
        deleteFile(uri)
    }

    @Query("DELETE FROM files")
    suspend fun deleteAllFiles()

    @Query("DELETE FROM passages")
    suspend fun deleteAllPassages()
}

@Database(entities = [IndexedFileEntity::class, PassageEntity::class], version = 1)
abstract class SearchDatabase : RoomDatabase() {
    abstract fun search(): SearchDao

    companion object {
        const val NAME = "search.db"
    }
}

object Vectors {
    fun toBytes(vector: FloatArray): ByteArray =
        ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(vector) }.array()

    fun fromBytes(bytes: ByteArray): FloatArray =
        FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }

    /** Vectors are normalised, so the dot product is the cosine. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}

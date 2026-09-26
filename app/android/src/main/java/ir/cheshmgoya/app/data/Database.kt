package ir.cheshmgoya.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history", indices = [Index("timeMs")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val timeMs: Long,
    /** Already sent to the local server for personalisation. */
    val synced: Boolean = false,
)

@Entity(tableName = "custom_phrases")
data class CustomPhraseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
)

@Entity(tableName = "word_stats")
data class WordStatEntity(@PrimaryKey val key: String, val display: String, val count: Double, val lastUsedMs: Long)

@Entity(tableName = "ngram_stats", primaryKeys = ["context", "next"])
data class NgramStatEntity(val context: String, val next: String, val count: Double, val lastUsedMs: Long)

@Entity(tableName = "sentence_stats")
data class SentenceStatEntity(@PrimaryKey val key: String, val text: String, val count: Double, val lastUsedMs: Long)

@Dao
interface HistoryDao {
    @Insert suspend fun insert(e: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY timeMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timeMs DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE synced = 0 ORDER BY timeMs LIMIT 200")
    suspend fun unsynced(): List<HistoryEntity>

    @Query("UPDATE history SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
abstract class CustomPhraseDao {
    @Query("SELECT * FROM custom_phrases ORDER BY position")
    abstract fun all(): Flow<List<CustomPhraseEntity>>

    @Query("SELECT * FROM custom_phrases ORDER BY position")
    abstract suspend fun allOnce(): List<CustomPhraseEntity>

    @Insert abstract suspend fun insert(e: CustomPhraseEntity): Long

    @Query("DELETE FROM custom_phrases WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM custom_phrases")
    abstract suspend fun deleteAll()

    @Upsert abstract suspend fun upsertAll(list: List<CustomPhraseEntity>)

    @Transaction
    open suspend fun replaceAll(texts: List<String>) {
        deleteAll()
        texts.forEachIndexed { i, t -> insert(CustomPhraseEntity(text = t, position = i)) }
    }
}

@Dao
interface LanguageStatsDao {
    @Upsert suspend fun upsertWords(rows: List<WordStatEntity>)
    @Upsert suspend fun upsertNgrams(rows: List<NgramStatEntity>)
    @Upsert suspend fun upsertSentences(rows: List<SentenceStatEntity>)

    @Query("SELECT * FROM word_stats") suspend fun words(): List<WordStatEntity>
    @Query("SELECT * FROM ngram_stats") suspend fun ngrams(): List<NgramStatEntity>
    @Query("SELECT * FROM sentence_stats") suspend fun sentences(): List<SentenceStatEntity>
}

@Database(
    entities = [HistoryEntity::class, CustomPhraseEntity::class, WordStatEntity::class, NgramStatEntity::class, SentenceStatEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun customPhrases(): CustomPhraseDao
    abstract fun languageStats(): LanguageStatsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cheshmgoya.db").build()
    }
}

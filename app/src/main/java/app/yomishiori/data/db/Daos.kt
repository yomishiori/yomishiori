package app.yomishiori.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LibraryItem(
    @Embedded val entry: LibraryEntry,
    val progression: Double?,
    val chapterLabel: String?,
)

data class HistoryItem(
    @Embedded val history: ReadingHistory,
    val title: String,
    val coverPath: String?,
    val sourceName: String,
    val sourceType: SourceType,
    val progression: Double?,
)

data class SourceWithEntry(
    @Embedded val source: ReadingSource,
    val entryTitle: String,
)

@Dao
interface LibraryDao {
    @Query(
        """SELECT e.*, s.progression AS progression, s.chapterLabel AS chapterLabel
           FROM library_entry e LEFT JOIN reading_source s ON s.id = e.lastReadSourceId"""
    )
    fun observeLibrary(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM library_entry WHERE id = :id")
    fun observeEntry(id: Long): Flow<LibraryEntry?>

    @Query("SELECT * FROM library_entry WHERE id = :id")
    suspend fun getEntry(id: Long): LibraryEntry?

    @Query("SELECT * FROM library_entry")
    suspend fun getAll(): List<LibraryEntry>

    @Insert
    suspend fun insert(entry: LibraryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LibraryEntry>)

    @Update
    suspend fun update(entry: LibraryEntry)

    @Query("UPDATE library_entry SET readingStatus = :status WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: ReadingStatus)

    @Query("DELETE FROM library_entry WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query(
        """UPDATE library_entry SET lastReadAt = :at, lastReadSourceId = :sourceId,
           readingStatus = CASE WHEN readingStatus = 'PLANNED' THEN 'READING' ELSE readingStatus END
           WHERE id = :entryId"""
    )
    suspend fun markRead(entryId: Long, sourceId: Long, at: Long)

    @Query("DELETE FROM library_entry")
    suspend fun deleteAll()
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM reading_source WHERE entryId = :entryId ORDER BY sortOrder, id")
    fun observeForEntry(entryId: Long): Flow<List<ReadingSource>>

    @Query("SELECT * FROM reading_source WHERE entryId IN (:entryIds)")
    suspend fun getForEntries(entryIds: List<Long>): List<ReadingSource>

    @Query(
        """SELECT s.*, e.title AS entryTitle FROM reading_source s
           JOIN library_entry e ON e.id = s.entryId ORDER BY s.sourceType, e.title COLLATE NOCASE"""
    )
    fun observeAllWithEntry(): Flow<List<SourceWithEntry>>

    @Query("SELECT * FROM reading_source WHERE id = :id")
    suspend fun get(id: Long): ReadingSource?

    @Query("SELECT * FROM reading_source WHERE id = :id")
    fun observe(id: Long): Flow<ReadingSource?>

    @Query("SELECT * FROM reading_source")
    suspend fun getAll(): List<ReadingSource>

    @Insert
    suspend fun insert(source: ReadingSource): Long

    @Insert
    suspend fun insertAll(sources: List<ReadingSource>)

    @Update
    suspend fun update(source: ReadingSource)

    @Delete
    suspend fun delete(source: ReadingSource)

    @Query("UPDATE reading_source SET lastUrl = :url, lastReadAt = :at, progressUpdatedAt = :at WHERE id = :id")
    suspend fun updateWebProgress(id: Long, url: String, at: Long)

    @Query(
        """UPDATE reading_source SET locatorJson = :locatorJson, progression = :progression,
           chapterLabel = :chapterLabel, lastReadAt = :at, progressUpdatedAt = :at WHERE id = :id"""
    )
    suspend fun updateEpubProgress(id: Long, locatorJson: String, progression: Double?, chapterLabel: String?, at: Long)

    @Query("UPDATE reading_source SET trackingEnabled = :enabled WHERE id = :id")
    suspend fun setTrackingEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM reading_source WHERE entryId = :entryId")
    suspend fun nextSortOrder(entryId: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM category")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM entry_category")
    fun observeMemberships(): Flow<List<EntryCategory>>

    @Query("SELECT * FROM entry_category")
    suspend fun getMemberships(): List<EntryCategory>

    @Query("SELECT categoryId FROM entry_category WHERE entryId = :entryId")
    fun observeCategoryIds(entryId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Insert
    suspend fun insertMemberships(memberships: List<EntryCategory>)

    @Query("DELETE FROM entry_category WHERE entryId = :entryId")
    suspend fun clearMemberships(entryId: Long)

    @Transaction
    suspend fun setCategories(entryIds: List<Long>, categoryIds: Set<Long>) {
        entryIds.forEach { entryId ->
            clearMemberships(entryId)
            insertMemberships(categoryIds.map { EntryCategory(entryId, it) })
        }
    }

    @Query("DELETE FROM category")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(history: ReadingHistory)

    @Insert
    suspend fun insertAll(history: List<ReadingHistory>)

    @Query(
        """SELECT h.*, e.title AS title, e.coverPath AS coverPath, s.name AS sourceName,
                  s.sourceType AS sourceType, s.progression AS progression
           FROM reading_history h
           JOIN library_entry e ON e.id = h.entryId
           JOIN reading_source s ON s.id = h.sourceId
           WHERE h.lastReadAt = (SELECT MAX(h2.lastReadAt) FROM reading_history h2 WHERE h2.entryId = h.entryId)
           ORDER BY h.lastReadAt DESC"""
    )
    fun observeLatestPerEntry(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM reading_history")
    suspend fun getAll(): List<ReadingHistory>

    @Query("DELETE FROM reading_history WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)

    @Query("DELETE FROM reading_history")
    suspend fun deleteAll()
}

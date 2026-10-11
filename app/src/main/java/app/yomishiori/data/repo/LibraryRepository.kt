package app.yomishiori.data.repo

import androidx.room.withTransaction
import app.yomishiori.data.db.AppDatabase
import app.yomishiori.data.db.Category
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.ReadingSource
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.db.SourceType
import app.yomishiori.data.files.AppFiles
import app.yomishiori.util.searchKeyOf

class LibraryRepository(private val db: AppDatabase, private val files: AppFiles) {
    private val libraryDao = db.libraryDao()
    private val sourceDao = db.sourceDao()
    private val categoryDao = db.categoryDao()
    private val historyDao = db.historyDao()

    fun observeLibrary() = libraryDao.observeLibrary()
    fun observeEntry(id: Long) = libraryDao.observeEntry(id)
    fun observeSources(entryId: Long) = sourceDao.observeForEntry(entryId)
    fun observeAllSources() = sourceDao.observeAllWithEntry()
    fun observeCategories() = categoryDao.observeAll()
    fun observeMemberships() = categoryDao.observeMemberships()
    fun observeCategoryIds(entryId: Long) = categoryDao.observeCategoryIds(entryId)
    fun observeHistory() = historyDao.observeLatestPerEntry()

    suspend fun getEntry(id: Long) = libraryDao.getEntry(id)
    suspend fun getAllEntries() = libraryDao.getAll()

    suspend fun createEntry(entry: LibraryEntry, categoryIds: Set<Long> = emptySet()): Long = db.withTransaction {
        val id = libraryDao.insert(entry.withSearchKey().copy(dateAdded = System.currentTimeMillis()))
        if (categoryIds.isNotEmpty()) categoryDao.setCategories(listOf(id), categoryIds)
        id
    }

    suspend fun updateEntry(entry: LibraryEntry, categoryIds: Set<Long>? = null) = db.withTransaction {
        val old = libraryDao.getEntry(entry.id)
        libraryDao.update(entry.withSearchKey())
        if (categoryIds != null) categoryDao.setCategories(listOf(entry.id), categoryIds)
        if (old?.coverPath != entry.coverPath) files.deleteOwned(old?.coverPath)
    }

    suspend fun setStatus(ids: List<Long>, status: ReadingStatus) = libraryDao.setStatus(ids, status)

    suspend fun setCategories(entryIds: List<Long>, categoryIds: Set<Long>) =
        categoryDao.setCategories(entryIds, categoryIds)

    suspend fun deleteEntries(ids: List<Long>) {
        val paths = db.withTransaction {
            val owned = sourceDao.getForEntries(ids).map { it.localFilePath } +
                ids.map { libraryDao.getEntry(it)?.coverPath }
            libraryDao.delete(ids)
            owned
        }
        paths.forEach(files::deleteOwned)
    }

    suspend fun addWebSource(entryId: Long, url: String, name: String): Long =
        sourceDao.insert(
            ReadingSource(
                entryId = entryId,
                sourceType = SourceType.WEB_URL,
                name = name,
                sortOrder = sourceDao.nextSortOrder(entryId),
                originalUrl = url,
            )
        )

    suspend fun addEpubSource(entryId: Long, filePath: String, name: String): Long =
        sourceDao.insert(
            ReadingSource(
                entryId = entryId,
                sourceType = SourceType.EPUB_FILE,
                name = name,
                sortOrder = sourceDao.nextSortOrder(entryId),
                localFilePath = filePath,
            )
        )

    suspend fun updateSource(source: ReadingSource) = sourceDao.update(source)

    suspend fun deleteSource(source: ReadingSource) {
        sourceDao.delete(source)
        files.deleteOwned(source.localFilePath)
    }

    suspend fun removeHistory(entryId: Long) = historyDao.deleteForEntry(entryId)

    suspend fun createCategory(name: String): Long =
        categoryDao.insert(Category(name = name.trim(), sortOrder = categoryDao.getAll().size))

    suspend fun renameCategory(category: Category, name: String) = categoryDao.update(category.copy(name = name.trim()))
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    private fun LibraryEntry.withSearchKey() = copy(searchKey = searchKeyOf(title, alternativeTitles, author))
}

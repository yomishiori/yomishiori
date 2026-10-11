package app.yomishiori.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.yomishiori.data.db.AppDatabase
import app.yomishiori.data.db.Category
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.EntryCategory
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.ReadingHistory
import app.yomishiori.data.db.ReadingSource
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.db.SourceType
import app.yomishiori.data.files.AppFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: Long,
    val categories: List<CategoryBackup>,
    val entries: List<BackupEntry>,
    val sources: List<ReadingSourceBackup>,
    val memberships: List<Membership>,
    val history: List<HistoryBackup>,
)

@Serializable
data class BackupEntry(
    val id: Long,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val author: String? = null,
    val description: String? = null,
    val cover: String? = null,
    val contentType: ContentType,
    val readingStatus: ReadingStatus,
    val tags: List<String> = emptyList(),
    val searchKey: String = "",
    val dateAdded: Long,
    val lastReadAt: Long? = null,
    val lastReadSourceId: Long? = null,
)

@Serializable
data class ReadingSourceBackup(
    val id: Long,
    val entryId: Long,
    val sourceType: SourceType,
    val name: String,
    val sortOrder: Int = 0,
    val originalUrl: String? = null,
    val localFile: String? = null,
    val lastUrl: String? = null,
    val locatorJson: String? = null,
    val progression: Double? = null,
    val chapterLabel: String? = null,
    val note: String? = null,
    val trackedHostRule: String? = null,
    val trackingEnabled: Boolean = true,
    val lastReadAt: Long? = null,
    val progressUpdatedAt: Long? = null,
)

@Serializable
data class CategoryBackup(val id: Long, val name: String, val sortOrder: Int = 0)

@Serializable
data class Membership(val entryId: Long, val categoryId: Long)

@Serializable
data class HistoryBackup(
    val sourceId: Long,
    val day: Long,
    val entryId: Long,
    val lastReadAt: Long,
    val lastUrl: String? = null,
    val locatorJson: String? = null,
    val chapterLabel: String? = null,
)

class BackupManager(private val context: Context, private val db: AppDatabase, private val files: AppFiles) {

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = snapshot()
        write(uri) { it.write(json.encodeToString(backup).toByteArray()) }
    }

    suspend fun exportZip(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = snapshot()
        write(uri) { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_JSON))
                zip.write(json.encodeToString(backup).toByteArray())
                zip.closeEntry()
                val referenced = backup.entries.mapNotNull { it.cover } + backup.sources.mapNotNull { it.localFile }
                referenced.filter { isStoredFile(it) }.distinct().forEach { relative ->
                    val file = File(files.root, relative)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry(relative))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val isZip = open(uri).use { it.read() == 0x50 && it.read() == 0x4B }
            val backup = if (isZip) {
                open(uri).use { extractZip(it, staging) }
                val jsonFile = File(staging, BACKUP_JSON)
                if (!jsonFile.exists()) throw IOException("Not a Yomishiori backup: $BACKUP_JSON missing.")
                parse(jsonFile.readText())
            } else {
                parse(open(uri).use { it.readBytes().decodeToString() })
            }
            replaceLibrary(backup)
            if (isZip) {
                listOf(AppFiles.EPUBS, AppFiles.COVERS).forEach { dir ->
                    val target = File(files.root, dir)
                    target.deleteRecursively()
                    File(staging, dir).takeIf { it.exists() }?.copyRecursively(target, overwrite = true)
                    target.mkdirs()
                }
            }
            backup.entries.size
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun snapshot(): BackupFile {
        val entries = db.libraryDao().getAll()
        val sources = db.sourceDao().getAll()
        return BackupFile(
            formatVersion = FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            categories = db.categoryDao().getAll().map { CategoryBackup(it.id, it.name, it.sortOrder) },
            entries = entries.map {
                BackupEntry(
                    it.id, it.title, it.alternativeTitles, it.author, it.description, files.relativize(it.coverPath),
                    it.contentType, it.readingStatus, it.tags, it.searchKey, it.dateAdded, it.lastReadAt, it.lastReadSourceId,
                )
            },
            sources = sources.map {
                ReadingSourceBackup(
                    it.id, it.entryId, it.sourceType, it.name, it.sortOrder, it.originalUrl, files.relativize(it.localFilePath),
                    it.lastUrl, it.locatorJson, it.progression, it.chapterLabel, it.note, it.trackedHostRule,
                    it.trackingEnabled, it.lastReadAt, it.progressUpdatedAt,
                )
            },
            memberships = db.categoryDao().getMemberships().map { Membership(it.entryId, it.categoryId) },
            history = db.historyDao().getAll().map {
                HistoryBackup(it.sourceId, it.day, it.entryId, it.lastReadAt, it.lastUrl, it.locatorJson, it.chapterLabel)
            },
        )
    }

    private suspend fun replaceLibrary(backup: BackupFile) = db.withTransaction {
        db.historyDao().deleteAll()
        db.libraryDao().deleteAll()
        db.categoryDao().deleteAll()
        db.categoryDao().insertAll(backup.categories.map { Category(it.id, it.name, it.sortOrder) })
        db.libraryDao().insertAll(
            backup.entries.map {
                LibraryEntry(
                    it.id, it.title, it.alternativeTitles, it.author, it.description, files.resolve(it.cover),
                    it.contentType, it.readingStatus, it.tags, it.searchKey, it.dateAdded, it.lastReadAt, it.lastReadSourceId,
                )
            }
        )
        db.sourceDao().insertAll(
            backup.sources.map {
                ReadingSource(
                    it.id, it.entryId, it.sourceType, it.name, it.sortOrder, it.originalUrl, files.resolve(it.localFile),
                    it.lastUrl, it.locatorJson, it.progression, it.chapterLabel, it.note, it.trackedHostRule,
                    it.trackingEnabled, it.lastReadAt, it.progressUpdatedAt,
                )
            }
        )
        db.categoryDao().insertMemberships(backup.memberships.map { EntryCategory(it.entryId, it.categoryId) })
        db.historyDao().insertAll(
            backup.history.map { ReadingHistory(it.sourceId, it.day, it.entryId, it.lastReadAt, it.lastUrl, it.locatorJson, it.chapterLabel) }
        )
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the selected file.")

    private fun write(uri: Uri, block: (OutputStream) -> Unit) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write the backup file.")
        out.use(block)
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val BACKUP_JSON = "backup.json"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(text: String): BackupFile {
            val backup = try {
                json.decodeFromString<BackupFile>(text)
            } catch (e: Exception) {
                throw IOException("Not a valid Yomishiori backup.", e)
            }
            if (backup.formatVersion > FORMAT_VERSION) {
                throw IOException("This backup was made by a newer version of the app (format ${backup.formatVersion}).")
            }
            return backup
        }

        private fun isStoredFile(path: String) = path.startsWith("${AppFiles.EPUBS}/") || path.startsWith("${AppFiles.COVERS}/")

        fun extractZip(input: InputStream, target: File) {
            val root = target.canonicalFile
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val out = File(root, entry.name).canonicalFile
                    if (!out.path.startsWith(root.path + File.separator)) throw IOException("Unsafe path in backup: ${entry.name}")
                    val allowed = entry.name == BACKUP_JSON || isStoredFile(entry.name)
                    if (!entry.isDirectory && allowed) {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
    }
}

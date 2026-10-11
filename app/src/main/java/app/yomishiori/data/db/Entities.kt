package app.yomishiori.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ContentType(val label: String) {
    LIGHT_NOVEL("Light Novel"),
    WEB_NOVEL("Web Novel"),
    EPUB("EPUB"),
    BOOK("Book"),
}

enum class ReadingStatus(val label: String) {
    PLANNED("Planned"),
    READING("Reading"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
}

enum class SourceType { EPUB_FILE, WEB_URL }

@Entity(tableName = "library_entry", indices = [Index("lastReadAt")])
data class LibraryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val author: String? = null,
    val description: String? = null,
    val coverPath: String? = null,
    val contentType: ContentType,
    val readingStatus: ReadingStatus = ReadingStatus.PLANNED,
    val tags: List<String> = emptyList(),
    val searchKey: String = "",
    val dateAdded: Long,
    val lastReadAt: Long? = null,
    val lastReadSourceId: Long? = null,
)

@Entity(
    tableName = "reading_source",
    foreignKeys = [ForeignKey(LibraryEntry::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class ReadingSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val sourceType: SourceType,
    val name: String,
    val sortOrder: Int = 0,
    val originalUrl: String? = null,
    val localFilePath: String? = null,
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

@Entity(tableName = "category", indices = [Index("name", unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "entry_category",
    primaryKeys = ["entryId", "categoryId"],
    foreignKeys = [
        ForeignKey(LibraryEntry::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class EntryCategory(val entryId: Long, val categoryId: Long)

@Entity(
    tableName = "reading_history",
    primaryKeys = ["sourceId", "day"],
    foreignKeys = [
        ForeignKey(LibraryEntry::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ReadingSource::class, ["id"], ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("entryId"), Index("lastReadAt")],
)
data class ReadingHistory(
    val sourceId: Long,
    val day: Long,
    val entryId: Long,
    val lastReadAt: Long,
    val lastUrl: String? = null,
    val locatorJson: String? = null,
    val chapterLabel: String? = null,
)

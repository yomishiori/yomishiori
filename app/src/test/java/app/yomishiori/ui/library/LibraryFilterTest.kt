package app.yomishiori.ui.library

import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.EntryCategory
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.LibraryItem
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.prefs.LibrarySort
import app.yomishiori.util.searchKeyOf
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private fun item(id: Long, title: String, status: ReadingStatus, type: ContentType, added: Long, read: Long?, progress: Double?, alt: List<String> = emptyList()) =
        LibraryItem(
            LibraryEntry(
                id = id, title = title, alternativeTitles = alt, contentType = type, readingStatus = status,
                searchKey = searchKeyOf(title, alt, null), dateAdded = added, lastReadAt = read,
            ),
            progression = progress,
            chapterLabel = null,
        )

    private val items = listOf(
        item(1, "Bravo", ReadingStatus.READING, ContentType.WEB_NOVEL, added = 3, read = 10, progress = null, alt = listOf("ブラボー")),
        item(2, "alpha", ReadingStatus.PLANNED, ContentType.EPUB, added = 1, read = null, progress = 0.5),
        item(3, "Charlie", ReadingStatus.COMPLETED, ContentType.EPUB, added = 2, read = 20, progress = 1.0),
    )
    private val memberships = listOf(EntryCategory(1, 100), EntryCategory(3, 100))

    private fun run(
        query: String = "",
        category: Long? = null,
        statuses: Set<ReadingStatus> = emptySet(),
        types: Set<ContentType> = emptySet(),
        sort: LibrarySort = LibrarySort.TITLE,
        asc: Boolean = true,
    ) = LibraryFilter.apply(items, memberships, query, category, statuses, types, sort, asc).map { it.entry.id }

    @Test fun `sorts by each key`() {
        assertEquals(listOf(2L, 1L, 3L), run(sort = LibrarySort.TITLE))
        assertEquals(listOf(3L, 1L, 2L), run(sort = LibrarySort.TITLE, asc = false))
        assertEquals(listOf(2L, 3L, 1L), run(sort = LibrarySort.DATE_ADDED))
        assertEquals(listOf(3L, 1L, 2L), run(sort = LibrarySort.LAST_READ, asc = false))
        assertEquals(listOf(3L, 2L, 1L), run(sort = LibrarySort.PROGRESS, asc = false))
    }

    @Test fun `filters combine`() {
        assertEquals(listOf(2L, 3L), run(types = setOf(ContentType.EPUB)))
        assertEquals(listOf(3L), run(types = setOf(ContentType.EPUB), statuses = setOf(ReadingStatus.COMPLETED)))
        assertEquals(listOf(1L, 3L), run(category = 100))
        assertEquals(emptyList<Long>(), run(category = 999))
    }

    @Test fun `search matches alternative titles and ignores width and case`() {
        assertEquals(listOf(1L), run(query = "ブラボ"))
        assertEquals(listOf(2L), run(query = "ＡＬＰ"))
        assertEquals(listOf(3L), run(query = "charlie"))
    }
}

package app.yomishiori.ui.library

import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.EntryCategory
import app.yomishiori.data.db.LibraryItem
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.prefs.LibrarySort
import app.yomishiori.util.normalizeForSearch

object LibraryFilter {
    fun apply(
        items: List<LibraryItem>,
        memberships: List<EntryCategory>,
        query: String,
        categoryId: Long?,
        statuses: Set<ReadingStatus>,
        types: Set<ContentType>,
        sort: LibrarySort,
        ascending: Boolean,
    ): List<LibraryItem> {
        val needle = normalizeForSearch(query)
        val inCategory = categoryId?.let { id -> memberships.filter { it.categoryId == id }.map { it.entryId }.toSet() }
        val filtered = items.filter { item ->
            val e = item.entry
            (needle.isEmpty() || e.searchKey.contains(needle)) &&
                (inCategory == null || e.id in inCategory) &&
                (statuses.isEmpty() || e.readingStatus in statuses) &&
                (types.isEmpty() || e.contentType in types)
        }
        val comparator: Comparator<LibraryItem> = when (sort) {
            LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.entry.title }
            LibrarySort.DATE_ADDED -> compareBy { it.entry.dateAdded }
            LibrarySort.LAST_READ -> compareBy(nullsFirst()) { it.entry.lastReadAt }
            LibrarySort.PROGRESS -> compareBy(nullsFirst()) { it.progression }
        }
        val tieBreak = comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.entry.title }
        return filtered.sortedWith(if (ascending) tieBreak else tieBreak.reversed())
    }
}

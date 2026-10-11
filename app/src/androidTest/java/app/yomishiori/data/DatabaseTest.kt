package app.yomishiori.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.AppDatabase
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.files.AppFiles
import app.yomishiori.data.repo.LibraryRepository
import app.yomishiori.data.repo.ProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var library: LibraryRepository
    private lateinit var progress: ProgressRepository
    private var now = 1_000_000L
    private val files = AppFiles(context)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        library = LibraryRepository(db, files)
        progress = ProgressRepository(db) { now }
    }

    @After fun tearDown() = db.close()

    private suspend fun newEntry(title: String = "Novel") =
        library.createEntry(LibraryEntry(title = title, alternativeTitles = listOf("小説"), contentType = ContentType.WEB_NOVEL, dateAdded = 0))

    @Test fun createEntryComputesSearchKeyAndDate() = runBlocking<Unit> {
        val id = newEntry("ＡＢＣ")
        val entry = library.getEntry(id)!!
        assertTrue(entry.searchKey.contains("abc"))
        assertTrue(entry.searchKey.contains("小説"))
        assertTrue(entry.dateAdded > 0)
    }

    @Test fun webProgressUpdatesSourceEntryAndHistory() = runBlocking<Unit> {
        val id = newEntry()
        val sourceId = library.addWebSource(id, "https://example.com/ch1", "example.com")
        progress.saveWebProgress(sourceId, "https://example.com/ch2")

        val source = progress.getSource(sourceId)!!
        assertEquals("https://example.com/ch2", source.lastUrl)
        val entry = library.getEntry(id)!!
        assertEquals(sourceId, entry.lastReadSourceId)
        assertEquals(now, entry.lastReadAt)
        assertEquals(ReadingStatus.READING, entry.readingStatus)
        val history = library.observeHistory().first().single()
        assertEquals("https://example.com/ch2", history.history.lastUrl)
    }

    @Test fun historyIsOneRowPerSourcePerDay() = runBlocking<Unit> {
        val id = newEntry()
        val sourceId = library.addWebSource(id, "https://example.com/ch1", "s")
        progress.saveWebProgress(sourceId, "https://example.com/ch2")
        now += 60_000
        progress.saveWebProgress(sourceId, "https://example.com/ch3")
        assertEquals(1, db.historyDao().getAll().size)
        now += 2 * 86_400_000L
        progress.saveWebProgress(sourceId, "https://example.com/ch4")
        assertEquals(2, db.historyDao().getAll().size)
        assertEquals("https://example.com/ch4", library.observeHistory().first().single().history.lastUrl)
    }

    @Test fun completedStatusIsNotOverwrittenByReading() = runBlocking<Unit> {
        val id = newEntry()
        library.setStatus(listOf(id), ReadingStatus.COMPLETED)
        val sourceId = library.addWebSource(id, "https://example.com/ch1", "s")
        progress.saveWebProgress(sourceId, "https://example.com/ch2")
        assertEquals(ReadingStatus.COMPLETED, library.getEntry(id)!!.readingStatus)
    }

    @Test fun manualPositionOverridesAndKeepsUnsetFields() = runBlocking<Unit> {
        val id = newEntry()
        val sourceId = library.addWebSource(id, "https://example.com/ch1", "s")
        progress.saveWebProgress(sourceId, "https://example.com/ch2")
        progress.setManualPosition(sourceId, url = null, chapterLabel = "Chapter 7", note = "")
        var s = progress.getSource(sourceId)!!
        assertEquals("https://example.com/ch2", s.lastUrl)
        assertEquals("Chapter 7", s.chapterLabel)
        progress.setManualPosition(sourceId, url = "https://example.com/ch9", chapterLabel = null, note = null)
        s = progress.getSource(sourceId)!!
        assertEquals("https://example.com/ch9", s.lastUrl)
        assertEquals("Chapter 7", s.chapterLabel)
    }

    @Test fun deletingEntryCascadesAndRemovesOwnedFiles() = runBlocking<Unit> {
        val epub = files.newEpubFile().apply { writeText("x") }
        val cover = files.newCoverFile().apply { writeText("x") }
        val id = library.createEntry(LibraryEntry(title = "E", coverPath = cover.path, contentType = ContentType.EPUB, dateAdded = 0))
        val sourceId = library.addEpubSource(id, epub.path, "Vol 1")
        progress.saveEpubProgress(sourceId, "{}", 0.5, "Ch 1")
        val category = library.createCategory("Fav")
        library.setCategories(listOf(id), setOf(category))

        library.deleteEntries(listOf(id))

        assertNull(library.getEntry(id))
        assertNull(progress.getSource(sourceId))
        assertTrue(db.historyDao().getAll().isEmpty())
        assertTrue(db.categoryDao().getMemberships().isEmpty())
        assertEquals(1, db.categoryDao().getAll().size)
        assertFalse(epub.exists())
        assertFalse(cover.exists())
    }

    @Test fun neverDeletesFilesOutsideAppStorage() = runBlocking<Unit> {
        val outside = File(context.cacheDir, "user.epub").apply { writeText("x") }
        val id = newEntry()
        library.addEpubSource(id, outside.path, "external")
        library.deleteEntries(listOf(id))
        assertTrue(outside.exists())
        outside.delete()
    }

    @Test fun categoriesMembership() = runBlocking<Unit> {
        val a = newEntry("A")
        val b = newEntry("B")
        val c1 = library.createCategory("One")
        val c2 = library.createCategory("Two")
        library.setCategories(listOf(a, b), setOf(c1))
        library.setCategories(listOf(a), setOf(c2))
        val memberships = db.categoryDao().getMemberships().map { it.entryId to it.categoryId }.toSet()
        assertEquals(setOf(a to c2, b to c1), memberships)
    }
}

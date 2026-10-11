package app.yomishiori.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.resetAppData
import app.yomishiori.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get() = testContainer.libraryRepository
    private val progress get() = testContainer.progressRepository
    private val backup get() = testContainer.backupManager

    @Before fun setUp() = resetAppData()

    private fun seed(): Triple<Long, Long, Long> = runBlocking {
        val category = repo.createCategory("Favorites")
        val webEntry = repo.createEntry(
            LibraryEntry(title = "Web", alternativeTitles = listOf("ウェブ"), contentType = ContentType.WEB_NOVEL, readingStatus = ReadingStatus.READING, dateAdded = 0),
            setOf(category),
        )
        val webSource = repo.addWebSource(webEntry, "https://example.com/ch1", "example.com")
        progress.saveWebProgress(webSource, "https://example.com/ch7")

        val epub = testContainer.files.newEpubFile().apply { writeText("epub-bytes") }
        val cover = testContainer.files.newCoverFile().apply { writeText("cover-bytes") }
        val epubEntry = repo.createEntry(LibraryEntry(title = "Book", coverPath = cover.path, contentType = ContentType.EPUB, dateAdded = 0))
        val epubSource = repo.addEpubSource(epubEntry, epub.path, "Vol 1")
        progress.saveEpubProgress(epubSource, """{"href":"ch1.xhtml","type":"application/xhtml+xml"}""", 0.42, "Chapter 1")
        Triple(webSource, epubSource, category)
    }

    @Test fun fullZipBackupRestoresLibraryProgressAndFiles() = runBlocking<Unit> {
        val (webSource, epubSource, _) = seed()
        val zip = File(context.cacheDir, "backup.zip")
        backup.exportZip(Uri.fromFile(zip))

        repo.deleteEntries(repo.getAllEntries().map { it.id })
        assertTrue(repo.getAllEntries().isEmpty())

        assertEquals(2, backup.restore(Uri.fromFile(zip)))
        assertEquals("https://example.com/ch7", progress.getSource(webSource)!!.lastUrl)
        val epub = progress.getSource(epubSource)!!
        assertEquals(0.42, epub.progression!!, 0.0001)
        assertEquals("epub-bytes", File(epub.localFilePath!!).readText())
        val book = repo.getAllEntries().single { it.title == "Book" }
        assertEquals("cover-bytes", File(book.coverPath!!).readText())
        assertEquals(listOf("ウェブ"), repo.getAllEntries().single { it.title == "Web" }.alternativeTitles)
        assertEquals(1, repo.observeMemberships().first().size)
        assertEquals(2, repo.observeHistory().first().size)
    }

    @Test fun jsonBackupRestoresMetadataButNotFiles() = runBlocking<Unit> {
        val (_, epubSource, _) = seed()
        val json = File(context.cacheDir, "backup.json")
        backup.exportJson(Uri.fromFile(json))
        assertFalse(json.readText().contains("epub-bytes"))

        repo.deleteEntries(repo.getAllEntries().map { it.id })
        assertEquals(2, backup.restore(Uri.fromFile(json)))

        val source = progress.getSource(epubSource)!!
        assertEquals(0.42, source.progression!!, 0.0001)
        assertFalse("EPUB file is reported missing", File(source.localFilePath!!).exists())
    }
}

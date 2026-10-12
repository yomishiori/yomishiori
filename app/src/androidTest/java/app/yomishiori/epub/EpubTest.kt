package app.yomishiori.epub

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.resetAppData
import app.yomishiori.testContainer
import app.yomishiori.waitFor
import app.yomishiori.writeTestEpub
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.use
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class EpubTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setUp() = resetAppData()

    @Test fun reflowableChaptersGetTheReaderScript() = runBlocking<Unit> {
        val imported = testContainer.readium.import(Uri.fromFile(writeTestEpub(File(context.cacheDir, "inject.epub"))))
        val publication = testContainer.readium.open(File(imported.filePath))
        val html = publication.get(publication.readingOrder.first())!!.use { it.read().getOrNull()!! }.decodeToString()
        assertTrue(html.contains("yomishiori-reader"))
        assertTrue(html.indexOf("yomishiori-reader") < html.indexOf("</head>"))
        publication.close()
    }

    @Test fun importCopiesFileAndExtractsMetadataAndCover() = runBlocking<Unit> {
        val picked = writeTestEpub(File(context.cacheDir, "picked.epub"), title = "異世界の本", author = "作者")
        val imported = testContainer.readium.import(Uri.fromFile(picked))
        picked.delete()

        assertEquals("異世界の本", imported.title)
        assertEquals("作者", imported.authors)
        assertTrue(File(imported.filePath).exists())
        assertTrue(testContainer.files.isOwned(imported.filePath))
        assertNotNull(imported.coverPath)
        assertTrue(File(imported.coverPath!!).length() > 0)

        val publication = testContainer.readium.open(File(imported.filePath))
        assertEquals(2, publication.tableOfContents.size)
        publication.close()
    }

    @Test fun invalidFileIsRejectedAndCleanedUp() = runBlocking<Unit> {
        val bogus = File(context.cacheDir, "bogus.epub").apply { writeText("not a zip") }
        val before = testContainer.files.epubDir.listFiles().orEmpty().size
        val result = runCatching { testContainer.readium.import(Uri.fromFile(bogus)) }
        assertTrue(result.isFailure)
        assertEquals(before, testContainer.files.epubDir.listFiles().orEmpty().size)
    }

    @Test fun locatorSurvivesJsonRoundTrip() = runBlocking<Unit> {
        val imported = testContainer.readium.import(Uri.fromFile(writeTestEpub(File(context.cacheDir, "rt.epub"))))
        val publication = testContainer.readium.open(File(imported.filePath))
        val locator = publication.locateProgression(0.6)!!
        val restored = Locator.fromJSON(JSONObject(locator.toJSON().toString()))
        assertEquals(locator, restored)
        publication.close()
    }

    @Test fun readerRestoresSavedLocatorAndSavesProgress() {
        val (sourceId, target) = runBlocking {
            val imported = testContainer.readium.import(Uri.fromFile(writeTestEpub(File(context.cacheDir, "read.epub"))))
            val entryId = testContainer.libraryRepository.createEntry(
                LibraryEntry(title = imported.title, contentType = ContentType.EPUB, dateAdded = 0)
            )
            val sourceId = testContainer.libraryRepository.addEpubSource(entryId, imported.filePath, imported.title)
            val publication = testContainer.readium.open(File(imported.filePath))
            val target = publication.locateProgression(0.6)!!
            testContainer.progressRepository.saveEpubProgress(sourceId, target.toJSON().toString(), target.locations.totalProgression, target.title)
            val stored = testContainer.progressRepository.getSource(sourceId)!!
            val initial = Locator.fromJSON(JSONObject(stored.locatorJson!!))
            testContainer.epubSessions.put(EpubSession(sourceId, publication, initial, EpubPreferences(fontSize = 1.4)))
            sourceId to target
        }
        val before = runBlocking { testContainer.progressRepository.getSource(sourceId)!!.progressUpdatedAt }

        ActivityScenario.launch<EpubReaderActivity>(EpubReaderActivity.intent(context, sourceId)).use {
            assertTrue("reader saved", waitFor(30_000) {
                runBlocking { testContainer.progressRepository.getSource(sourceId)!!.progressUpdatedAt != before }
            })
        }
        val saved = runBlocking { testContainer.progressRepository.getSource(sourceId)!! }
        val progression = saved.progression!!
        assertTrue("restored near 60% (was $progression)", abs(progression - target.locations.totalProgression!!) < 0.1)
        assertEquals(target.href, Locator.fromJSON(JSONObject(saved.locatorJson!!))!!.href)
    }
}

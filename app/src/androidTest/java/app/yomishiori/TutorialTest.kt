package app.yomishiori

import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.epub.TutorialBook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.mediatype.MediaType
import java.io.File

@RunWith(AndroidJUnit4::class)
class TutorialTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setUp() = resetAppData()

    private fun entries() = runBlocking { testContainer.libraryRepository.getAllEntries() }

    private fun setFirstLaunch() = runBlocking {
        testContainer.preferences.update { it.copy(tutorialDone = false, readerHintsShown = false) }
    }

    @Test fun firstLaunchAddsAndOpensTheTutorial() {
        setFirstLaunch()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("tutorial added", waitFor(20_000) { entries().any { it.title == TutorialBook.TITLE } })
            val entry = entries().single { it.title == TutorialBook.TITLE }
            assertTrue("tutorial opened in the reader", waitFor(30_000) {
                runBlocking { testContainer.libraryRepository.getEntry(entry.id)?.lastReadAt != null }
            })
        }
        assertTrue(runBlocking { testContainer.preferences.current().tutorialDone })
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(3_000)
            assertEquals(1, entries().count { it.title == TutorialBook.TITLE })
        }
    }

    @Test fun existingLibraryDoesNotGetTheTutorial() {
        runBlocking {
            testContainer.libraryRepository.createEntry(LibraryEntry(title = "Mine", contentType = ContentType.EPUB, dateAdded = 0))
        }
        setFirstLaunch()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(waitFor(10_000) { runBlocking { testContainer.preferences.current().tutorialDone } })
            Thread.sleep(2_000)
            assertFalse(entries().any { it.title == TutorialBook.TITLE })
        }
    }

    @Test fun tutorialBookOpensWithContentsAndPicture() = runBlocking<Unit> {
        val file = File(context.cacheDir, "tutorial.epub")
        TutorialBook.write(file, BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground))
        val publication = testContainer.readium.open(file)
        assertEquals(TutorialBook.TITLE, publication.metadata.title)
        assertEquals(7, publication.tableOfContents.size)
        assertTrue(publication.resources.any { it.mediaType?.matches(MediaType.PNG) == true })
        publication.close()
    }
}

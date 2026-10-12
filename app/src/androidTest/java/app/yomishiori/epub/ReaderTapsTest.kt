package app.yomishiori.epub

import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.prefs.PageTurnDirection
import app.yomishiori.data.prefs.ReaderPrefs
import app.yomishiori.resetAppData
import app.yomishiori.testContainer
import app.yomishiori.waitFor
import app.yomishiori.writeTestEpub
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderTapsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val width = context.resources.displayMetrics.widthPixels.toFloat()
    private val height = context.resources.displayMetrics.heightPixels.toFloat()

    @Before fun setUp() {
        resetAppData()
        instrumentation.uiAutomation.executeShellCommand("settings put secure immersive_mode_confirmations confirmed").close()
    }

    private fun setReader(prefs: ReaderPrefs) = runBlocking { testContainer.preferences.update { it.copy(reader = prefs) } }

    private fun openReader(): ActivityScenario<EpubReaderActivity> {
        val sourceId = runBlocking {
            val imported = testContainer.readium.import(Uri.fromFile(writeTestEpub(File(context.cacheDir, "taps.epub"))))
            val entry = testContainer.libraryRepository.createEntry(LibraryEntry(title = imported.title, contentType = ContentType.EPUB, dateAdded = 0))
            val id = testContainer.libraryRepository.addEpubSource(entry, imported.filePath, imported.title)
            testContainer.epubSessions.put(EpubSession(id, testContainer.readium.open(File(imported.filePath)), null, EpubPreferences()))
            id
        }
        val scenario = ActivityScenario.launch<EpubReaderActivity>(EpubReaderActivity.intent(context, sourceId))
        assertTrue("reader ready", waitFor(30_000) {
            runBlocking { testContainer.progressRepository.getSource(sourceId)?.progressUpdatedAt != null }
        })
        Thread.sleep(1_500)
        return scenario
    }

    private fun ActivityScenario<EpubReaderActivity>.progression(): Double {
        var value = -1.0
        onActivity { value = (it.supportFragmentManager.findFragmentByTag("navigator") as EpubNavigatorFragment).currentLocator.value.locations.progression ?: 0.0 }
        return value
    }

    private fun ActivityScenario<EpubReaderActivity>.menuVisible(): Boolean {
        var visible = false
        onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == true }
        return visible
    }

    private fun touch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun tap(x: Float, y: Float = height / 2) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, x, y, down)
        touch(MotionEvent.ACTION_UP, x, y, down)
    }

    private fun doubleTap(x: Float) {
        tap(x)
        Thread.sleep(90)
        tap(x)
        Thread.sleep(1_200)
    }

    private fun swipeLeft() {
        val y = (height / 2).toInt()
        instrumentation.uiAutomation.executeShellCommand("input swipe ${(width * 0.85f).toInt()} $y ${(width * 0.15f).toInt()} $y 400").close()
        Thread.sleep(1_500)
    }

    @Test fun doubleTapRightGoesForwardAndLeftGoesBack() {
        openReader().use { s ->
            val start = s.progression()
            doubleTap(width * 0.8f)
            assertTrue("forward", waitFor(6_000) { s.progression() > start })
            Thread.sleep(1_500)
            doubleTap(width * 0.2f)
            assertTrue("back", waitFor(6_000) { s.progression() <= start + 0.0001 })
            assertTrue("double-taps don't open the menu", !s.menuVisible())
        }
    }

    @Test fun singleTapTogglesTheMenu() {
        openReader().use { s ->
            val start = s.progression()
            tap(width / 2)
            assertTrue("menu shown", waitFor(3_000) { s.menuVisible() })
            assertEquals(start, s.progression(), 0.0001)
            tap(width / 2)
            assertTrue("menu hidden", waitFor(3_000) { !s.menuVisible() })
        }
    }

    @Test fun swipeIsOffByDefaultAndCanBeTurnedOn() {
        openReader().use { s ->
            val start = s.progression()
            swipeLeft()
            assertEquals("swipe ignored", start, s.progression(), 0.0001)
            setReader(ReaderPrefs(swipeToTurn = true))
            Thread.sleep(2_000)
            swipeLeft()
            assertTrue("swipe turned the page", waitFor(6_000) { s.progression() > start })
        }
    }

    @Test fun leftMeansNextWhenChosen() {
        setReader(ReaderPrefs(pageTurn = PageTurnDirection.RIGHT_TO_LEFT))
        openReader().use { s ->
            val start = s.progression()
            doubleTap(width * 0.2f)
            assertTrue(waitFor(6_000) { s.progression() > start })
        }
    }
}

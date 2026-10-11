package app.yomishiori.web

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.resetAppData
import app.yomishiori.testContainer
import app.yomishiori.waitFor
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewTrackingTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<String>()

    private fun page(title: String, body: String) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body("<html><head><title>$title</title></head><body><h1>$title</h1>$body</body></html>")
            .build()

    @Before fun setUp() {
        resetAppData()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(requests) { requests += path }
                return when (path) {
                    "/novel/ch1/page1" -> page("Page 1", """<a id="next" href="/novel/ch1/page2">Next</a>""")
                    "/novel/ch1/page2" -> page(
                        "Page 2",
                        """<button id="spa" onclick="history.pushState({}, '', '/novel/ch1/page3')">Next (SPA)</button>""",
                    )
                    "/novel/ch1/page3" -> page("Page 3", "content")
                    "/login" -> page("Login", "<form></form>")
                    "/novel/ch1/moved" -> MockResponse.Builder().code(301).addHeader("Location", "/").build()
                    "/" -> page("Home", """<a id="first" href="/novel/ch1/page1">Start</a>""")
                    else -> MockResponse.Builder().code(404).body("<html><body>Not found</body></html>").build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun savedUrl(sourceId: Long) = runBlocking { testContainer.progressRepository.getSource(sourceId)?.lastUrl }

    private fun ActivityScenario<WebReaderActivity>.run(js: String) = onActivity { it.webView().evaluateJavascript(js, null) }

    private fun ActivityScenario<WebReaderActivity>.currentUrl(): String? {
        var url: String? = null
        onActivity { url = it.webView().url }
        return url
    }

    @Test fun tracksValidNavigationAndIgnoresInvalidOnes() {
        val page1 = server.url("/novel/ch1/page1").toString()
        val page2 = server.url("/novel/ch1/page2").toString()
        val page3 = server.url("/novel/ch1/page3").toString()
        val sourceId = runBlocking {
            val entryId = testContainer.libraryRepository.createEntry(
                LibraryEntry(title = "Shuukura", contentType = ContentType.WEB_NOVEL, dateAdded = 0)
            )
            testContainer.libraryRepository.addWebSource(entryId, page1, "local")
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch<WebReaderActivity>(WebReaderActivity.intent(context, sourceId)).use { scenario ->
            assertTrue("page1 saved", waitFor { savedUrl(sourceId) == page1 })

            scenario.run("document.getElementById('next').click()")
            assertTrue("page2 saved", waitFor { savedUrl(sourceId) == page2 })

            scenario.run("document.getElementById('spa').click()")
            assertTrue("page3 saved", waitFor { savedUrl(sourceId) == page3 })

            val missing = server.url("/novel/ch1/missing").toString()
            scenario.run("location.href = '$missing'")
            assertTrue("navigated to 404", waitFor { scenario.currentUrl() == missing })
            Thread.sleep(2_500)
            assertEquals(page3, savedUrl(sourceId))

            val otherHost = if (server.hostName == "localhost") "127.0.0.1" else "localhost"
            val otherSite = "http://$otherHost:${server.port}/novel/ch1/page1"
            scenario.run("location.href = '$otherSite'")
            assertTrue("navigated to other site", waitFor { scenario.currentUrl() == otherSite })
            Thread.sleep(2_500)
            assertEquals(page3, savedUrl(sourceId))

            val login = server.url("/login").toString()
            scenario.run("location.href = '$login'")
            assertTrue("navigated to login", waitFor { scenario.currentUrl() == login })
            Thread.sleep(2_500)
            assertEquals(page3, savedUrl(sourceId))
        }

        synchronized(requests) { requests.clear() }
        ActivityScenario.launch<WebReaderActivity>(WebReaderActivity.intent(context, sourceId)).use { scenario ->
            assertTrue("restored page3", waitFor { scenario.currentUrl() == page3 })
            assertTrue(synchronized(requests) { requests.first() == "/novel/ch1/page3" })
        }
    }

    @Test fun redirectWhenRestoringDoesNotOverwriteSavedPosition() {
        val moved = server.url("/novel/ch1/moved").toString()
        val home = server.url("/").toString()
        val page1 = server.url("/novel/ch1/page1").toString()
        val sourceId = runBlocking {
            val entryId = testContainer.libraryRepository.createEntry(
                LibraryEntry(title = "Moved", contentType = ContentType.WEB_NOVEL, dateAdded = 0)
            )
            val id = testContainer.libraryRepository.addWebSource(entryId, page1, "local")
            testContainer.progressRepository.setManualPosition(id, moved, null, null)
            id
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<WebReaderActivity>(WebReaderActivity.intent(context, sourceId)).use { scenario ->
            assertTrue("redirected home", waitFor { scenario.currentUrl() == home })
            Thread.sleep(2_500)
            assertEquals("saved chapter kept", moved, savedUrl(sourceId))
            scenario.run("document.getElementById('first').click()")
            assertTrue("next page saved", waitFor { savedUrl(sourceId) == page1 })
        }
    }

    @Test fun rendererCrashClosesReaderWithoutCrashingApp() {
        val page1 = server.url("/novel/ch1/page1").toString()
        val sourceId = runBlocking {
            val entryId = testContainer.libraryRepository.createEntry(
                LibraryEntry(title = "Crash", contentType = ContentType.WEB_NOVEL, dateAdded = 0)
            )
            testContainer.libraryRepository.addWebSource(entryId, page1, "local")
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<WebReaderActivity>(WebReaderActivity.intent(context, sourceId)).use { scenario ->
            assertTrue(waitFor { savedUrl(sourceId) == page1 })
            scenario.onActivity { it.webView().loadUrl("chrome://crash") }
            assertTrue("reader closed", waitFor { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED })
            assertEquals(page1, savedUrl(sourceId))
        }
    }

    @Test fun manualSaveOverridesPolicyAndPausedTrackingStopsAutoSave() {
        val page1 = server.url("/novel/ch1/page1").toString()
        val page2 = server.url("/novel/ch1/page2").toString()
        val sourceId = runBlocking {
            val entryId = testContainer.libraryRepository.createEntry(
                LibraryEntry(title = "Manual", contentType = ContentType.WEB_NOVEL, dateAdded = 0)
            )
            testContainer.libraryRepository.addWebSource(entryId, page1, "local")
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<WebReaderActivity>(WebReaderActivity.intent(context, sourceId)).use { scenario ->
            assertTrue(waitFor { savedUrl(sourceId) == page1 })
            runBlocking { testContainer.progressRepository.setTrackingEnabled(sourceId, false) }
            Thread.sleep(500)
            scenario.run("document.getElementById('next').click()")
            assertTrue(waitFor { scenario.currentUrl() == page2 })
            Thread.sleep(2_500)
            assertEquals("paused tracking keeps old position", page1, savedUrl(sourceId))

            scenario.onActivity { activity -> activity.viewModelForTest().saveCurrentPosition() }
            assertTrue("manual save", waitFor { savedUrl(sourceId) == page2 })
        }
    }
}

private fun Activity.webView(): WebView = findWebView(window.decorView)!!

private fun findWebView(view: View): WebView? = when (view) {
    is WebView -> view
    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
    else -> null
}

private fun WebReaderActivity.viewModelForTest(): WebReaderViewModel =
    androidx.lifecycle.ViewModelProvider(this)[WebReaderViewModel::class.java]

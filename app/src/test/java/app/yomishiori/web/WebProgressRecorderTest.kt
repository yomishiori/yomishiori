package app.yomishiori.web

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebProgressRecorderTest {
    private val saved = mutableListOf<String>()

    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private fun TestScope.recorder(initial: String? = null) =
        WebProgressRecorder(backgroundScope, this, initial, debounceMillis = 1_000) { saved += it }

    @Test fun `rapid urls collapse into one save of the last`() = runTest {
        val r = recorder()
        r.offer("https://a.com/1")
        advanceTimeBy(300)
        r.offer("https://a.com/2")
        advanceTimeBy(300)
        r.offer("https://a.com/3")
        settle()
        assertEquals(listOf("https://a.com/3"), saved)
    }

    @Test fun `already saved url is not written again`() = runTest {
        val r = recorder(initial = "https://a.com/1")
        r.offer("https://a.com/1")
        settle()
        assertEquals(emptyList<String>(), saved)
        r.offer("https://a.com/2")
        settle()
        r.offer("https://a.com/2")
        settle()
        assertEquals(listOf("https://a.com/2"), saved)
    }

    @Test fun `going back to the saved url cancels a pending save`() = runTest {
        val r = recorder(initial = "https://a.com/1")
        r.offer("https://a.com/2")
        r.offer("https://a.com/1")
        settle()
        assertEquals(emptyList<String>(), saved)
    }

    @Test fun `flush saves immediately and survives the screen scope`() = runTest {
        val r = recorder()
        r.offer("https://a.com/2")
        r.flush()
        backgroundScope.cancel()
        runCurrent()
        assertEquals(listOf("https://a.com/2"), saved)
        assertEquals("https://a.com/2", r.lastSavedUrl)
    }

    @Test fun `flush with nothing pending does nothing`() = runTest {
        val r = recorder()
        r.flush()
        settle()
        assertEquals(emptyList<String>(), saved)
    }

    @Test fun `manual save bypasses dedupe`() = runTest {
        val r = recorder(initial = "https://a.com/1")
        r.saveNow("https://a.com/1")
        settle()
        assertEquals(listOf("https://a.com/1"), saved)
    }
}

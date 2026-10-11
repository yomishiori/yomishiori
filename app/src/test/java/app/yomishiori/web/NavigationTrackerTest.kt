package app.yomishiori.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrackerTest {
    private val tracker = NavigationTracker()

    @Test fun `normal load is confirmed when content is visible`() {
        tracker.onPageStarted("https://a.com/ch1")
        assertNull(tracker.onHistoryUpdated("https://a.com/ch1"))
        assertEquals("https://a.com/ch1", tracker.onPageCommitVisible("https://a.com/ch1"))
        assertEquals("https://a.com/ch1", tracker.onPageFinished("https://a.com/ch1"))
    }

    @Test fun `history update before page started is fine for a good page`() {
        assertEquals("https://a.com/ch2", tracker.onHistoryUpdated("https://a.com/ch2"))
        tracker.onPageStarted("https://a.com/ch2")
        assertEquals("https://a.com/ch2", tracker.onPageCommitVisible("https://a.com/ch2"))
    }

    @Test fun `server redirect confirms only the final url`() {
        tracker.onPageStarted("https://a.com/latest")
        tracker.onPageStarted("https://a.com/ch9")
        assertNull(tracker.onHistoryUpdated("https://a.com/ch9"))
        assertEquals("https://a.com/ch9", tracker.onPageCommitVisible("https://a.com/ch9"))
    }

    @Test fun `javascript redirect after load confirms both pages in order`() {
        tracker.onPageStarted("https://a.com/ch1")
        assertEquals("https://a.com/ch1", tracker.onPageCommitVisible("https://a.com/ch1"))
        tracker.onPageFinished("https://a.com/ch1")
        tracker.onPageStarted("https://a.com/ch2")
        assertEquals("https://a.com/ch2", tracker.onPageCommitVisible("https://a.com/ch2"))
    }

    @Test fun `pushState and hash changes without a load are confirmed immediately`() {
        tracker.onPageStarted("https://a.com/novel")
        tracker.onPageFinished("https://a.com/novel")
        assertEquals("https://a.com/novel/ch2", tracker.onHistoryUpdated("https://a.com/novel/ch2"))
        assertEquals("https://a.com/novel/ch2#p3", tracker.onHistoryUpdated("https://a.com/novel/ch2#p3"))
    }

    @Test fun `http error reported after page started is never confirmed`() {
        tracker.onPageStarted("https://a.com/missing")
        tracker.onMainFrameError("https://a.com/missing")
        assertNull(tracker.onPageCommitVisible("https://a.com/missing"))
        assertNull(tracker.onPageFinished("https://a.com/missing"))
        assertNull(tracker.onHistoryUpdated("https://a.com/missing#x"))
    }

    @Test fun `http error reported before page started is never confirmed`() {
        tracker.onMainFrameError("https://a.com/missing")
        assertNull(tracker.onHistoryUpdated("https://a.com/missing"))
        tracker.onPageStarted("https://a.com/missing")
        assertNull(tracker.onPageCommitVisible("https://a.com/missing"))
        assertNull(tracker.onPageFinished("https://a.com/missing"))
    }

    @Test fun `same url loading fine after a security check is confirmed`() {
        tracker.onMainFrameError("https://a.com/ch7")
        tracker.onPageStarted("https://a.com/ch7")
        assertNull(tracker.onPageCommitVisible("https://a.com/ch7"))
        assertNull(tracker.onPageFinished("https://a.com/ch7"))
        tracker.onPageStarted("https://a.com/ch7")
        assertEquals("https://a.com/ch7", tracker.onPageCommitVisible("https://a.com/ch7"))
    }

    @Test fun `network error mid load is never confirmed`() {
        tracker.onPageStarted("https://a.com/ch3")
        tracker.onMainFrameError("https://a.com/ch3")
        assertNull(tracker.onPageFinished("https://a.com/ch3"))
    }

    @Test fun `next successful load after an error is confirmed`() {
        tracker.onMainFrameError("https://a.com/bad")
        tracker.onPageStarted("https://a.com/bad")
        tracker.onPageFinished("https://a.com/bad")
        tracker.onPageStarted("https://a.com/ch4")
        assertEquals("https://a.com/ch4", tracker.onPageCommitVisible("https://a.com/ch4"))
    }

    @Test fun `reload confirms the same url again (deduped later)`() {
        tracker.onPageStarted("https://a.com/ch1")
        tracker.onPageFinished("https://a.com/ch1")
        tracker.onPageStarted("https://a.com/ch1")
        assertNull(tracker.onHistoryUpdated("https://a.com/ch1"))
        assertEquals("https://a.com/ch1", tracker.onPageFinished("https://a.com/ch1"))
    }

    @Test fun `onPageFinished is the fallback when commit visible never fires`() {
        tracker.onPageStarted("https://a.com/ch5")
        assertEquals("https://a.com/ch5", tracker.onPageFinished("https://a.com/ch5"))
    }
}

package app.yomishiori.epub

import app.yomishiori.data.prefs.PageTurnDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderGesturesTest {
    @Test fun `follow the book uses the book direction`() {
        assertEquals(true, isForward(tapLeft = false, PageTurnDirection.AUTO, bookIsRtl = false))
        assertEquals(false, isForward(tapLeft = true, PageTurnDirection.AUTO, bookIsRtl = false))
        assertEquals(true, isForward(tapLeft = true, PageTurnDirection.AUTO, bookIsRtl = true))
        assertEquals(false, isForward(tapLeft = false, PageTurnDirection.AUTO, bookIsRtl = true))
    }

    @Test fun `right means next for every book`() {
        listOf(false, true).forEach { rtl ->
            assertEquals(true, isForward(tapLeft = false, PageTurnDirection.LEFT_TO_RIGHT, rtl))
            assertEquals(false, isForward(tapLeft = true, PageTurnDirection.LEFT_TO_RIGHT, rtl))
        }
    }

    @Test fun `left means next for every book`() {
        listOf(false, true).forEach { rtl ->
            assertEquals(true, isForward(tapLeft = true, PageTurnDirection.RIGHT_TO_LEFT, rtl))
            assertEquals(false, isForward(tapLeft = false, PageTurnDirection.RIGHT_TO_LEFT, rtl))
        }
    }
}

package app.yomishiori.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAndUrlTest {
    @Test fun `search key folds width and case`() {
        val key = searchKeyOf("ＳＨＵＵＫＵＲＡ", listOf("終末なにしてますか？"), "Author ﾃｽﾄ")
        assertTrue(key.contains(normalizeForSearch("shuukura")))
        assertTrue(key.contains(normalizeForSearch("終末なに")))
        assertTrue(key.contains(normalizeForSearch("テスト")))
        assertTrue(key.contains(normalizeForSearch("ＡＵＴＨＯＲ")))
    }

    @Test fun `split list trims and dedupes`() {
        assertEquals(listOf("a", "b", "c"), splitList(" a, b ,\nc,, a、"))
    }

    @Test fun `percent formatting clamps`() {
        assertEquals("42%", formatPercent(0.425))
        assertEquals("100%", formatPercent(1.3))
        assertNull(formatPercent(null))
    }

    @Test fun `user urls are normalized to https`() {
        assertEquals("https://example.com/ch1", normalizeWebUrl(" example.com/ch1 "))
        assertEquals("https://example.com/ch1", normalizeWebUrl("http://example.com/ch1"))
        assertEquals("https://ncode.syosetu.com/n1234/5/", normalizeWebUrl("https://ncode.syosetu.com/n1234/5/"))
        assertNull(normalizeWebUrl("javascript:alert(1)"))
        assertNull(normalizeWebUrl("ftp://example.com"))
        assertNull(normalizeWebUrl("not a url"))
        assertNull(normalizeWebUrl("hello"))
        assertNull(normalizeWebUrl(""))
    }
}

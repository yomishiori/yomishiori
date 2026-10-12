package app.yomishiori.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHtmlTest {
    private fun scriptOf(html: String) = html.substringAfter("<script").substringBefore("</script>")

    @Test fun `script goes before the end of head`() {
        val out = ReaderHtml.inject("<html><head><title>t</title></head><body><p>x</p></body></html>")
        assertTrue(out.indexOf("yomishiori-reader") < out.indexOf("</head>"))
        assertTrue(out.endsWith("<body><p>x</p></body></html>"))
    }

    @Test fun `uppercase head is found`() {
        val out = ReaderHtml.inject("<HTML><HEAD></HEAD><BODY></BODY></HTML>")
        assertTrue(out.indexOf("yomishiori-reader") < out.indexOf("</HEAD>"))
    }

    @Test fun `without head the script goes at the start of body`() {
        val out = ReaderHtml.inject("<html><body class=\"a\"><p>x</p></body></html>")
        assertTrue(out.startsWith("<html><body class=\"a\"><script"))
    }

    @Test fun `files without head or body are left alone`() {
        assertEquals("<svg/>", ReaderHtml.inject("<svg/>"))
    }

    @Test fun `injecting twice changes nothing`() {
        val once = ReaderHtml.inject("<html><head></head><body></body></html>")
        assertEquals(once, ReaderHtml.inject(once))
    }

    @Test fun `script is valid inside xhtml`() {
        val body = scriptOf(ReaderHtml.inject("<html><head></head></html>")).substringAfter(">")
        assertFalse(body.contains('<'))
        assertFalse(body.contains('&'))
    }

    @Test fun `utf16 files are left untouched`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x3C, 0x00)
        assertTrue(bytes.contentEquals(ReaderHtml.inject(bytes)))
    }

    @Test fun `only html resources are rewritten`() {
        assertTrue(ReaderHtml.isHtml("OEBPS/Text/chapter01.xhtml"))
        assertTrue(ReaderHtml.isHtml("a/B.HTML"))
        assertFalse(ReaderHtml.isHtml("OEBPS/Styles/stylesheet.css"))
        assertFalse(ReaderHtml.isHtml("OEBPS/Images/cover.jpg"))
    }
}

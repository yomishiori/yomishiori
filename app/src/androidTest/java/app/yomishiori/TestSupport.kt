package app.yomishiori

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.prefs.ReaderPrefs
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

val testContainer: AppContainer
    get() = InstrumentationRegistry.getInstrumentation().targetContext.container

fun resetAppData(): Unit = runBlocking {
    testContainer.database.clearAllTables()
    val files = testContainer.files
    files.epubDir.deleteRecursively()
    files.coverDir.deleteRecursively()
    testContainer.preferences.update { it.copy(reader = ReaderPrefs(), tutorialDone = true, readerHintsShown = true) }
    Unit
}

fun waitFor(timeoutMs: Long = 15_000, condition: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (condition()) return true
        Thread.sleep(100)
    }
    return condition()
}

fun writeTestEpub(file: File, title: String = "Test Novel", author: String = "Test Author"): File {
    val cover = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.rgb(80, 90, 160)) }
    val coverPng = ByteArrayOutputStream().also { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    val paragraph = "<p>" + "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(40) + "</p>"
    fun chapter(n: Int) = """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter $n</title></head>
        <body><h1>Chapter $n</h1>${paragraph.repeat(8)}</body></html>""".trimIndent()

    ZipOutputStream(file.outputStream()).use { zip ->
        val mimetype = "application/epub+zip".toByteArray()
        zip.putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetype.size.toLong()
            crc = CRC32().apply { update(mimetype) }.value
        })
        zip.write(mimetype)
        zip.closeEntry()
        fun put(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        put(
            "META-INF/container.xml",
            """<?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>""".trimIndent().toByteArray(),
        )
        put(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="uid">urn:uuid:yomishiori-test</dc:identifier>
                <dc:title>$title</dc:title>
                <dc:creator>$author</dc:creator>
                <dc:language>en</dc:language>
                <dc:description>A test book.</dc:description>
                <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="ch1"/><itemref idref="ch2"/></spine>
            </package>""".trimIndent().toByteArray(),
        )
        put(
            "OEBPS/nav.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head>
            <body><nav epub:type="toc"><ol>
              <li><a href="ch1.xhtml">Chapter 1</a></li><li><a href="ch2.xhtml">Chapter 2</a></li>
            </ol></nav></body></html>""".trimIndent().toByteArray(),
        )
        put("OEBPS/cover.png", coverPng)
        put("OEBPS/ch1.xhtml", chapter(1).toByteArray())
        put("OEBPS/ch2.xhtml", chapter(2).toByteArray())
    }
    return file
}

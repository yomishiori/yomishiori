package app.yomishiori.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import app.yomishiori.R
import app.yomishiori.container
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.openReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TutorialBook {
    const val TITLE = "How to use Yomishiori"

    private const val LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

    private val chapters = listOf(
        "Welcome" to """
            <p>Yomishiori keeps your light novels, web novels and EPUB books in one place, and remembers where you stopped reading.</p>
            <p>This short book shows you the basics. To go to the next page, <b>double-tap the right side</b> of the screen.</p>
        """,
        "Turning pages" to """
            <p><b>Double-tap the right side</b> of the page to go forward, and <b>double-tap the left side</b> to go back.</p>
            <p><b>Tap once</b> anywhere to show or hide the menu. At the top you'll find the table of contents and the <b>Aa</b> button for text settings. At the bottom you'll see the chapter, how far you are, and a bar to jump through the book.</p>
            <p>In <b>Aa</b> you can choose which side means "next page". By default it follows the book, so Japanese and Arabic books turn the other way. You can also turn on swiping if you prefer it.</p>
            <p>Your position is saved automatically, even if you close the app.</p>
        """,
        "Pictures" to """
            <p>Tap the picture below to see it full screen. Pinch or double-tap to zoom in, and press Back to close it.</p>
            <div><img src="logo.png" alt="The Yomishiori logo"/></div>
            <p>You can turn this off in <b>Aa</b> with "Tap pictures to zoom".</p>
        """,
        "Web novels" to """
            <p>In <b>Browse</b>, choose <b>Add from website URL</b> and paste the address of the chapter you're reading.</p>
            <p>The novel opens in the built-in browser. When you move to the next chapter, the new page is saved for you. The cloud icon at the top shows whether this page is saved.</p>
            <p>Some sites change pages without changing the address. On those, tap the <b>bookmark</b> button to save your place by hand.</p>
            <p>If a site shows a security check, complete it on the page and keep reading.</p>
        """,
        "Your library" to """
            <p>Every book has a status (Reading, Planned, Completed and so on) and can go into categories, which show up as tabs in the library.</p>
            <p>A book can have several sources, for example the EPUB volumes and the translator's website. Each one keeps its own place.</p>
            <p>In <b>Settings</b> you can back up your library. The .zip backup also contains your EPUB files.</p>
        """,
        "Practice pages" to """
            <p>Use these pages to practice. Double-tap right to go forward and left to go back.</p>
        """ + (1..14).joinToString("") { "<p>$LOREM</p>" },
        "That's it" to """
            <p>You're ready. You can delete this book from its details screen, and bring it back any time from <b>Settings</b> with "Show tutorial again".</p>
        """,
    )

    suspend fun open(context: Context) {
        val c = context.container
        val existing = c.libraryRepository.getAllEntries().firstOrNull { it.title == TITLE }
        val existingSource = existing?.let { e ->
            c.database.sourceDao().getForEntries(listOf(e.id)).firstOrNull { it.localFilePath?.let(::File)?.exists() == true }
        }
        if (existingSource != null) {
            openReader(context, existingSource)
            return
        }
        existing?.let { c.libraryRepository.deleteEntries(listOf(it.id)) }
        val file = c.files.newEpubFile()
        val cover = withContext(Dispatchers.IO) {
            val logo = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            write(file, logo)
            c.files.saveCover(coverOf(logo)).path
        }
        val id = c.libraryRepository.createEntry(
            LibraryEntry(
                title = TITLE,
                author = "Yomishiori",
                description = "A short guide to reading with Yomishiori.",
                coverPath = cover,
                contentType = ContentType.EPUB,
                dateAdded = 0,
            )
        )
        val sourceId = c.libraryRepository.addEpubSource(id, file.path, TITLE)
        c.progressRepository.getSource(sourceId)?.let { openReader(context, it) }
    }

    fun write(file: File, logo: Bitmap) {
        val png = ByteArrayOutputStream().also { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
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
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
            )
            val items = chapters.indices.joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" }
            val spine = chapters.indices.joinToString("") { """<itemref idref="c$it"/>""" }
            put(
                "OEBPS/content.opf",
                ("""<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">""" +
                    """<metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:yomishiori-tutorial</dc:identifier>""" +
                    """<dc:title>$TITLE</dc:title><dc:creator>Yomishiori</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata>""" +
                    """<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" +
                    """<item id="logo" href="logo.png" media-type="image/png"/><item id="css" href="style.css" media-type="text/css"/>$items</manifest>""" +
                    """<spine>$spine</spine></package>""").toByteArray(),
            )
            val links = chapters.withIndex().joinToString("") { (i, ch) -> """<li><a href="c$i.xhtml">${ch.first}</a></li>""" }
            put("OEBPS/nav.xhtml", page("Contents", """<nav epub:type="toc"><h1>Contents</h1><ol>$links</ol></nav>""").toByteArray())
            put("OEBPS/style.css", "body { line-height: 1.6; } h1 { text-align: center; } img { max-width: 60%; }".toByteArray())
            put("OEBPS/logo.png", png)
            chapters.forEachIndexed { i, (title, body) ->
                put("OEBPS/c$i.xhtml", page(title, "<h1>$title</h1>${body.trimIndent()}").toByteArray())
            }
        }
    }

    private fun page(title: String, body: String) =
        """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en" xml:lang="en">""" +
            """<head><title>$title</title><link rel="stylesheet" type="text/css" href="style.css"/></head><body>$body</body></html>"""

    private fun coverOf(logo: Bitmap): Bitmap {
        val cover = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cover)
        canvas.drawColor(Color.WHITE)
        val size = 420
        val scaled = Bitmap.createScaledBitmap(logo, size, size, true)
        canvas.drawBitmap(scaled, (600 - size) / 2f, 160f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 110, 143)
            textSize = 54f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("How to use", 300f, 680f, paint)
        canvas.drawText("Yomishiori", 300f, 750f, paint)
        return cover
    }
}

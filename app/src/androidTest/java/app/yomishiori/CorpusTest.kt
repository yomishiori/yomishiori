package app.yomishiori

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.epub.EpubReaderActivity
import app.yomishiori.epub.EpubSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CorpusTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "corpus")
    private val out = File(context.getExternalFilesDir(null), "corpus-out").apply { deleteRecursively(); mkdirs() }
    private val report = StringBuilder()

    private fun log(line: String) {
        report.appendLine(line)
        android.util.Log.i("Corpus", line)
        File(out, "report.txt").writeText(report.toString())
    }

    private fun shot(name: String) {
        Thread.sleep(1200)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val scaled = Bitmap.createScaledBitmap(bmp, bmp.width / 3, bmp.height / 3, true)
        File(out, "$name.png").outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun source(id: Long) = runBlocking { testContainer.progressRepository.getSource(id)!! }

    @Test
    fun runCorpus() {
        val from = InstrumentationRegistry.getArguments().getString("from").orEmpty()
        val files = dir.listFiles { f -> f.name.endsWith(".epub") && f.name >= from }.orEmpty().sortedBy { it.name }
        assumeTrue("no corpus at $dir", files.isNotEmpty())
        resetAppData()
        files.forEach { file ->
            val tag = file.nameWithoutExtension.take(40).replace(Regex("[^A-Za-z0-9_-]"), "_")
            try {
                runBook(file, tag)
            } catch (t: Throwable) {
                log("FAIL  ${file.name}: ${t::class.simpleName}: ${t.message}")
            }
        }
        log("DONE")
    }

    private fun runBook(file: File, tag: String) {
        val t0 = System.currentTimeMillis()
        val imported = try {
            runBlocking { testContainer.readium.import(Uri.fromFile(file)) }
        } catch (e: Exception) {
            log("REJECT ${file.name}: ${e.message}")
            return
        }
        val importMs = System.currentTimeMillis() - t0
        val coverKb = imported.coverPath?.let { File(it).length() / 1024 }
        val (sourceId, info) = runBlocking {
            val entry = testContainer.libraryRepository.createEntry(
                LibraryEntry(title = imported.title, author = imported.authors, coverPath = imported.coverPath, contentType = ContentType.EPUB, dateAdded = 0)
            )
            val id = testContainer.libraryRepository.addEpubSource(entry, imported.filePath, imported.title)
            val pub = testContainer.readium.open(File(imported.filePath))
            val info = "layout=${pub.metadata.layout} progression=${pub.metadata.readingProgression} " +
                "spine=${pub.readingOrder.size} toc=${pub.tableOfContents.size} positions=${pub.positions().size}"
            pub.close()
            id to info
        }
        log("OK    ${file.name} | title=\"${imported.title}\" author=${imported.authors} cover=${coverKb}KB import=${importMs}ms $info")

        val pub = runBlocking { testContainer.readium.open(File(imported.filePath)) }
        testContainer.epubSessions.put(EpubSession(sourceId, pub, null, EpubPreferences()))
        val before = source(sourceId).progressUpdatedAt
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderActivity.intent(context, sourceId)).use { scenario ->
            if (!waitFor(30_000) { source(sourceId).progressUpdatedAt != before }) {
                log("   WARN ${file.name}: reader never saved a position on open")
            }
            shot("$tag-1-open")
            val target = runBlocking { pub.locateProgression(0.5) }
            if (target == null) {
                log("   WARN ${file.name}: locateProgression(0.5) returned null")
            } else {
                scenario.onActivity { navigator(it).go(target) }
                val ok = waitFor(15_000) { abs((source(sourceId).progression ?: -1.0) - 0.5) < 0.2 }
                log("   jump50 -> saved progression=${source(sourceId).progression} chapter=\"${source(sourceId).chapterLabel}\"${if (ok) "" else "  <-- not near 0.5"}")
            }
            shot("$tag-2-middle")
            scenario.onActivity { navigator(it).submitPreferences(EpubPreferences(fontSize = 1.6, theme = Theme.DARK)) }
            Thread.sleep(2500)
            val afterPrefs = source(sourceId).progression
            log("   prefs(font160,dark) -> progression=$afterPrefs")
            shot("$tag-3-dark-big")
        }
        val saved = source(sourceId)
        val pub2 = runBlocking { testContainer.readium.open(File(imported.filePath)) }
        val initial = saved.locatorJson?.let { Locator.fromJSON(JSONObject(it)) }
        testContainer.epubSessions.put(EpubSession(sourceId, pub2, initial, EpubPreferences()))
        val before2 = saved.progressUpdatedAt
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderActivity.intent(context, sourceId)).use {
            waitFor(30_000) { source(sourceId).progressUpdatedAt != before2 }
            val reopened = source(sourceId).progression
            val drift = if (reopened != null && saved.progression != null) abs(reopened - saved.progression!!) else null
            log("   reopen -> progression=$reopened (was ${saved.progression}, drift=$drift)${if (drift != null && drift > 0.05) "  <-- position drifted" else ""}")
            shot("$tag-4-reopen")
        }
    }

    private fun navigator(activity: EpubReaderActivity) =
        activity.supportFragmentManager.findFragmentByTag("navigator") as EpubNavigatorFragment
}

package app.yomishiori.web

import android.graphics.Bitmap
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiveSitesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.getExternalFilesDir(null), "sites-out").apply { deleteRecursively(); mkdirs() }
    private val report = StringBuilder()

    private val sites = listOf(
        "ja-syosetu" to "https://ncode.syosetu.com/n9669bk/1/",
        "ja-kakuyomu" to "https://kakuyomu.jp/works/1177354054882154317/episodes/1177354054882154330",
        "ja-aozora" to "https://www.aozora.gr.jp/cards/000148/files/789_14547.html",
        "zh-wikisource" to "https://zh.wikisource.org/wiki/%E7%B4%85%E6%A8%93%E5%A4%A2/%E7%AC%AC001%E5%9B%9E",
        "ar-safahat" to "https://www.safahat.org/books/61302419/1/",
        "en-royalroad" to "https://www.royalroad.com/fiction/21220/mother-of-learning/chapter/301778/1-good-morning-brother",
        "en-scribblehub" to "https://www.scribblehub.com/read/862913-the-ultimate-loner/chapter/862916/",
        "en-avelilium" to "https://avelilium.com/2023/07/07/sendai-san-is-worth-five-thousand-yen-no-more-no-less-i/",
    )

    private val clickNext = """
        (function(){
          var norm = function(s){ return (s||'').replace(/\s+/g,' ').trim(); };
          var pat = /^(next|next chapter|next part.*|次へ.*|次の話.*|次のエピソード.*|下一[章回页頁].*|التالي.*|الفصل التالي.*)\s*[>»→›]*$/i;
          var a = document.querySelector('a[rel~=next]');
          if (!a) a = Array.prototype.slice.call(document.querySelectorAll('a')).filter(function(x){ return pat.test(norm(x.textContent)); })[0];
          if (!a) a = document.querySelector('#contentMain-readNextEpisode, .c-pager__item--next');
          if (!a) { var l = document.querySelector('link[rel=next]'); if (l) { location.href = l.href; return 'link-rel ' + l.href; } return 'none'; }
          a.click(); return 'clicked ' + a.href;
        })()
    """.trimIndent()

    private fun log(line: String) {
        report.appendLine(line)
        File(out, "report.txt").writeText(report.toString())
    }

    private fun shot(name: String) {
        Thread.sleep(1500)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(out, "$name.png").outputStream().use { Bitmap.createScaledBitmap(bmp, bmp.width / 3, bmp.height / 3, true).compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun saved(id: Long) = runBlocking { testContainer.progressRepository.getSource(id)?.lastUrl }

    @Test
    fun sites() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        resetAppData()
        sites.forEach { (tag, url) ->
            try {
                site(tag, url)
            } catch (t: Throwable) {
                log("FAIL $tag: ${t::class.simpleName}: ${t.message}")
            }
        }
        log("DONE")
    }

    private fun site(tag: String, url: String) {
        val id = runBlocking {
            val entry = testContainer.libraryRepository.createEntry(LibraryEntry(title = tag, contentType = ContentType.WEB_NOVEL, dateAdded = 0))
            testContainer.libraryRepository.addWebSource(entry, url, tag)
        }
        ActivityScenario.launch<WebReaderActivity>(WebReaderActivity.intent(context, id)).use { s ->
            val first = waitFor(40_000) { saved(id) != null }
            var current: String? = null
            var title: String? = null
            s.onActivity { val w = find(it.window.decorView)!!; current = w.url; title = w.title }
            log("$tag: open saved=${saved(id)} (ok=$first) webview=$current title=\"$title\"")
            shot("$tag-1")
            val before = saved(id)
            var result: String? = null
            s.onActivity { find(it.window.decorView)!!.evaluateJavascript(clickNext) { r -> result = r } }
            waitFor(5_000) { result != null }
            val moved = waitFor(40_000) { saved(id) != null && saved(id) != before }
            s.onActivity { val w = find(it.window.decorView)!!; current = w.url; title = w.title }
            log("$tag: next=$result -> saved=${saved(id)} (changed=$moved) webview=$current title=\"$title\"")
            shot("$tag-2")
        }
    }

    private fun find(v: View): WebView? = when (v) {
        is WebView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }
}

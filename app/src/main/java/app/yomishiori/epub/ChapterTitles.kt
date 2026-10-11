package app.yomishiori.epub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.use
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

class ChapterTitles(private val publication: Publication) {
    private class Mark(val index: Int, val ratio: Double, val title: String)

    private val order = publication.readingOrder.map { it.url().toString().substringBefore('#') }
    private var marks: List<Mark>? = null

    suspend fun titleFor(locator: Locator): String? {
        locator.title?.takeIf { it.isNotBlank() }?.let { return it }
        val index = order.indexOf(locator.href.toString().substringBefore('#'))
        if (index < 0) return null
        val progression = locator.locations.progression ?: 0.0
        val all = marks ?: build().also { marks = it }
        return all.lastOrNull { it.index < index || (it.index == index && it.ratio <= progression + 0.001) }?.title
    }

    private suspend fun build(): List<Mark> = withContext(Dispatchers.IO) {
        val texts = mutableMapOf<Int, String?>()
        flatten(publication.tableOfContents).mapNotNull { link ->
            val title = link.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = link.url().toString()
            val index = order.indexOf(url.substringBefore('#')).takeIf { it >= 0 } ?: return@mapNotNull null
            val fragment = url.substringAfter('#', "")
            val ratio = if (fragment.isEmpty()) 0.0 else {
                val text = texts.getOrPut(index) { read(publication.readingOrder[index]) }
                val at = text?.let { t -> listOf("id=\"$fragment\"", "id='$fragment'").map { t.indexOf(it) }.firstOrNull { it >= 0 } }
                if (text == null || at == null) 0.0 else at.toDouble() / text.length
            }
            Mark(index, ratio, title)
        }.sortedWith(compareBy({ it.index }, { it.ratio }))
    }

    private suspend fun read(link: Link): String? =
        publication.get(link)?.use { it.read().getOrNull()?.decodeToString() }

    private fun flatten(links: List<Link>): List<Link> = links.flatMap { listOf(it) + flatten(it.children) }
}

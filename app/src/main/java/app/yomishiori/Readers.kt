package app.yomishiori

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.yomishiori.data.db.ReadingSource
import app.yomishiori.data.db.SourceType
import app.yomishiori.epub.EpubReaderActivity
import app.yomishiori.epub.EpubSession
import app.yomishiori.epub.epubPreferencesOf
import app.yomishiori.web.WebReaderActivity
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import java.io.File

suspend fun openReader(context: Context, source: ReadingSource) {
    when (source.sourceType) {
        SourceType.WEB_URL -> context.startActivity(WebReaderActivity.intent(context, source.id))
        SourceType.EPUB_FILE -> {
            val c = context.container
            val path = source.localFilePath ?: error("This source has no EPUB file.")
            val publication = c.readium.open(File(path))
            val locator = source.locatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
            val preferences = epubPreferencesOf(c.preferences.current().epubPreferencesJson)
            c.epubSessions.put(EpubSession(source.id, publication, locator, preferences))
            context.startActivity(EpubReaderActivity.intent(context, source.id))
        }
    }
}

@Composable
fun rememberReaderOpener(): (Long) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { sourceId ->
        scope.launch {
            try {
                val source = context.container.progressRepository.getSource(sourceId) ?: error("Source not found.")
                openReader(context, source)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Cannot open reader.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

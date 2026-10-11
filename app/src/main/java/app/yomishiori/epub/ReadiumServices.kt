package app.yomishiori.epub

import android.content.Context
import android.net.Uri
import app.yomishiori.data.files.AppFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.IOException

data class ImportedEpub(
    val filePath: String,
    val title: String,
    val authors: String?,
    val description: String?,
    val coverPath: String?,
)

class ReadiumServices(context: Context, private val files: AppFiles) {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(
        DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null),
    )

    suspend fun open(file: File): Publication = withContext(Dispatchers.IO) {
        if (!file.exists()) throw IOException("The EPUB file is missing. Re-import it from the details screen.")
        val asset = assetRetriever.retrieve(file).getOrElse { throw IOException("Cannot read EPUB: ${it.message}") }
        opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            throw IOException("Cannot open EPUB: ${it.message}")
        }
    }

    suspend fun import(uri: Uri): ImportedEpub = withContext(Dispatchers.IO) {
        val dest = files.newEpubFile()
        files.copyFromUri(uri, dest)
        try {
            val publication = open(dest)
            try {
                val meta = publication.metadata
                ImportedEpub(
                    filePath = dest.path,
                    title = meta.title?.takeIf { it.isNotBlank() }
                        ?: files.displayName(uri)?.substringBeforeLast('.')
                        ?: "Untitled",
                    authors = meta.authors.joinToString(", ") { it.name }.ifBlank { null },
                    description = meta.description?.let(::stripHtml),
                    coverPath = publication.cover()?.let { files.saveCover(it).path },
                )
            } finally {
                publication.close()
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    private fun stripHtml(text: String): String =
        android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

package app.yomishiori.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import app.yomishiori.data.files.AppFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.encryption.encryption
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.use
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
        val asset = assetRetriever.retrieve(file).getOrElse { throw IOException(UNREADABLE) }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            throw IOException(UNREADABLE)
        }
        val encrypted = publication.readingOrder.any { link ->
            link.properties.encryption?.algorithm?.let { it !in fontObfuscation } == true
        }
        if (publication.isRestricted || encrypted) {
            publication.close()
            throw IOException("This book is DRM-protected, so it can't be opened here. Only DRM-free EPUBs are supported.")
        }
        publication
    }

    private companion object {
        const val UNREADABLE = "This file isn't a readable EPUB. It may be damaged or in another format."
    }

    private val fontObfuscation = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")

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
                        ?: (files.displayName(uri) ?: uri.lastPathSegment)?.substringBeforeLast('.')
                        ?: "Untitled",
                    authors = meta.authors.joinToString(", ") { it.name }.ifBlank { null },
                    description = meta.description?.let(::stripHtml),
                    coverPath = (publication.coverFitting(Size(600, 900)) ?: firstImage(publication))?.let { files.saveCover(it).path },
                )
            } finally {
                publication.close()
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    private suspend fun firstImage(publication: Publication): Bitmap? {
        val images = (publication.readingOrder + publication.resources).filter { it.mediaType?.isBitmap == true }
        val ordered = images.filter { it.href.toString().contains("cover", ignoreCase = true) } + images
        for (link in ordered.distinct().take(8)) {
            val bytes = publication.get(link)?.use { it.read().getOrNull() } ?: continue
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth < 200 || bounds.outHeight < 200) continue
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 600) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: continue
        }
        return null
    }

    private fun stripHtml(text: String): String =
        android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

package app.yomishiori.data.files

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

class AppFiles(private val context: Context) {
    val root: File get() = context.filesDir
    val epubDir: File get() = File(root, EPUBS).apply { mkdirs() }
    val coverDir: File get() = File(root, COVERS).apply { mkdirs() }

    fun newEpubFile(): File = File(epubDir, "${UUID.randomUUID()}.epub")
    fun newCoverFile(): File = File(coverDir, "${UUID.randomUUID()}.jpg")

    fun copyFromUri(uri: Uri, dest: File) {
        val size = sizeOf(uri)
        if (size != null && size > dest.parentFile!!.usableSpace - FREE_SPACE_MARGIN) {
            throw IOException("Not enough free storage to import this file.")
        }
        val temp = File(dest.parentFile, dest.name + ".part")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the selected file.")
            input.use { inp -> temp.outputStream().use { inp.copyTo(it) } }
            if (!temp.renameTo(dest)) throw IOException("Cannot store the imported file.")
        } finally {
            temp.delete()
        }
    }

    fun saveCover(bitmap: Bitmap): File {
        val file = newCoverFile()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun sizeOf(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    fun isOwned(path: String?): Boolean {
        if (path == null || path.contains("://")) return false
        return File(path).canonicalPath.startsWith(root.canonicalPath + File.separator)
    }

    fun deleteOwned(path: String?) {
        if (isOwned(path)) File(path!!).delete()
    }

    fun relativize(path: String?): String? =
        if (isOwned(path)) File(path!!).canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath else path

    fun resolve(stored: String?): String? = when {
        stored == null -> null
        stored.startsWith("$EPUBS/") || stored.startsWith("$COVERS/") -> File(root, stored).path
        else -> stored
    }

    companion object {
        const val EPUBS = "epubs"
        const val COVERS = "covers"
        private const val FREE_SPACE_MARGIN = 20L * 1024 * 1024
    }
}

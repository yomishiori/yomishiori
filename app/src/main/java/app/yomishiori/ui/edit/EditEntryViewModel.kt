package app.yomishiori.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomishiori.AppContainer
import app.yomishiori.data.db.Category
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.util.normalizeWebUrl
import app.yomishiori.util.splitList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditForm(
    val loading: Boolean = true,
    val title: String = "",
    val alternativeTitles: String = "",
    val author: String = "",
    val description: String = "",
    val contentType: ContentType = ContentType.WEB_NOVEL,
    val status: ReadingStatus = ReadingStatus.PLANNED,
    val tags: String = "",
    val coverPath: String? = null,
    val categoryIds: Set<Long> = emptySet(),
    val categories: List<Category> = emptyList(),
    val sourceUrl: String = "",
    val error: String? = null,
)

class EditEntryViewModel(private val entryId: Long, private val container: AppContainer) : ViewModel() {
    private val repo = container.libraryRepository
    private val _form = MutableStateFlow(EditForm())
    val form: StateFlow<EditForm> = _form
    val isNew get() = entryId == 0L

    private var original: LibraryEntry? = null
    private var savedCover: String? = null

    init {
        viewModelScope.launch {
            val categories = repo.observeCategories().first()
            val entry = if (isNew) null else repo.getEntry(entryId)
            original = entry
            savedCover = entry?.coverPath
            val ids = if (isNew) emptySet() else repo.observeCategoryIds(entryId).first().toSet()
            _form.value = if (entry == null) {
                EditForm(loading = false, categories = categories)
            } else {
                EditForm(
                    loading = false,
                    title = entry.title,
                    alternativeTitles = entry.alternativeTitles.joinToString("\n"),
                    author = entry.author.orEmpty(),
                    description = entry.description.orEmpty(),
                    contentType = entry.contentType,
                    status = entry.readingStatus,
                    tags = entry.tags.joinToString(", "),
                    coverPath = entry.coverPath,
                    categoryIds = ids,
                    categories = categories,
                )
            }
        }
    }

    fun update(block: (EditForm) -> EditForm) = _form.update { block(it).copy(error = null) }

    fun pickCover(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = container.files.newCoverFile()
                withContext(Dispatchers.IO) { container.files.copyFromUri(uri, file) }
                replaceUnsavedCover(file.path)
            } catch (e: Exception) {
                _form.update { it.copy(error = e.message) }
            }
        }
    }

    fun setCoverUrl(url: String) {
        val normalized = normalizeWebUrl(url)
        if (normalized == null) _form.update { it.copy(error = "Invalid image URL") } else replaceUnsavedCover(normalized)
    }

    fun removeCover() = replaceUnsavedCover(null)

    private fun replaceUnsavedCover(path: String?) {
        val previous = _form.value.coverPath
        if (previous != savedCover) container.files.deleteOwned(previous)
        _form.update { it.copy(coverPath = path) }
    }

    suspend fun save(): Long? {
        val f = _form.value
        if (f.title.isBlank()) {
            _form.update { it.copy(error = "Title is required") }
            return null
        }
        val url = f.sourceUrl.takeIf { it.isNotBlank() }?.let {
            normalizeWebUrl(it) ?: run {
                _form.update { s -> s.copy(error = "Invalid source URL") }
                return null
            }
        }
        val base = original ?: LibraryEntry(title = "", contentType = f.contentType, dateAdded = 0)
        val entry = base.copy(
            title = f.title.trim(),
            alternativeTitles = splitList(f.alternativeTitles, Regex("\\n")),
            author = f.author.trim().ifBlank { null },
            description = f.description.trim().ifBlank { null },
            contentType = f.contentType,
            readingStatus = f.status,
            tags = splitList(f.tags),
            coverPath = f.coverPath,
        )
        val id = if (isNew) {
            repo.createEntry(entry, f.categoryIds).also { id ->
                if (url != null) repo.addWebSource(id, url, runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrDefault("Website"))
            }
        } else {
            repo.updateEntry(entry, f.categoryIds)
            entryId
        }
        savedCover = f.coverPath
        return id
    }

    override fun onCleared() {
        val current = _form.value.coverPath
        if (current != savedCover) container.files.deleteOwned(current)
    }
}

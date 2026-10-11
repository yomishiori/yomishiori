package app.yomishiori.ui.details

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomishiori.AppContainer
import app.yomishiori.data.db.Category
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.ReadingSource
import app.yomishiori.data.db.ReadingStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class DetailsUiState(
    val loading: Boolean = true,
    val entry: LibraryEntry? = null,
    val sources: List<ReadingSource> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryIds: Set<Long> = emptySet(),
)

class DetailsViewModel(private val entryId: Long, private val container: AppContainer) : ViewModel() {
    private val library = container.libraryRepository
    private val progress = container.progressRepository

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val state: StateFlow<DetailsUiState> = combine(
        library.observeEntry(entryId),
        library.observeSources(entryId),
        library.observeCategories(),
        library.observeCategoryIds(entryId),
    ) { entry, sources, categories, ids ->
        DetailsUiState(false, entry, sources, categories, ids.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState())

    fun primarySource(s: DetailsUiState = state.value): ReadingSource? =
        s.sources.firstOrNull { it.id == s.entry?.lastReadSourceId } ?: s.sources.firstOrNull()

    fun setStatus(status: ReadingStatus) = launch { library.setStatus(listOf(entryId), status) }
    fun setCategories(ids: Set<Long>) = launch { library.setCategories(listOf(entryId), ids) }
    fun deleteEntry() = launch { library.deleteEntries(listOf(entryId)) }

    fun addWebSource(url: String, name: String) = launch { library.addWebSource(entryId, url, name.ifBlank { hostName(url) }) }

    fun addEpubSource(uri: Uri) = launch {
        val imported = container.readium.import(uri)
        library.addEpubSource(entryId, imported.filePath, imported.title)
        val entry = library.getEntry(entryId)
        if (entry != null && entry.coverPath == null && imported.coverPath != null) {
            library.updateEntry(entry.copy(coverPath = imported.coverPath))
        } else {
            container.files.deleteOwned(imported.coverPath)
        }
        _messages.emit("Added \"${imported.title}\"")
    }

    fun relinkEpub(source: ReadingSource, uri: Uri) = launch {
        val imported = container.readium.import(uri)
        container.files.deleteOwned(imported.coverPath)
        val old = source.localFilePath
        library.updateSource(source.copy(localFilePath = imported.filePath))
        if (old != null && old != imported.filePath) container.files.deleteOwned(old)
        _messages.emit("EPUB file replaced")
    }

    fun renameSource(source: ReadingSource, name: String) = launch { library.updateSource(source.copy(name = name)) }
    fun setHostRule(source: ReadingSource, rule: String) =
        launch { library.updateSource(source.copy(trackedHostRule = rule.trim().ifBlank { null })) }

    fun setManualPosition(source: ReadingSource, url: String?, chapter: String, note: String) =
        launch { progress.setManualPosition(source.id, url, chapter, note) }

    fun deleteSource(source: ReadingSource) = launch { library.deleteSource(source) }

    fun isFileMissing(source: ReadingSource) = source.localFilePath != null && !File(source.localFilePath).exists()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Something went wrong")
            }
        }
    }

    private fun hostName(url: String) = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: "Website"
}

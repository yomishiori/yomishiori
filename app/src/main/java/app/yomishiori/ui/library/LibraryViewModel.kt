package app.yomishiori.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomishiori.data.db.Category
import app.yomishiori.data.db.LibraryItem
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.prefs.AppPreferences
import app.yomishiori.data.prefs.LibraryPrefs
import app.yomishiori.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val items: List<LibraryItem> = emptyList(),
    val libraryEmpty: Boolean = true,
    val categories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val query: String = "",
    val prefs: LibraryPrefs = LibraryPrefs(),
    val selection: Set<Long> = emptySet(),
)

class LibraryViewModel(private val repository: LibraryRepository, private val preferences: AppPreferences) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<Long?>(null)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private data class Ui(val query: String, val categoryId: Long?, val selection: Set<Long>)

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        repository.observeMemberships(),
        repository.observeCategories(),
        preferences.settings.map { it.library },
        combine(query, categoryId, selection, ::Ui),
    ) { items, memberships, categories, prefs, ui ->
        val category = ui.categoryId?.takeIf { id -> categories.any { it.id == id } }
        LibraryUiState(
            loading = false,
            items = LibraryFilter.apply(
                items, memberships, ui.query, category, prefs.statusFilter, prefs.typeFilter, prefs.sort, prefs.sortAscending,
            ),
            libraryEmpty = items.isEmpty(),
            categories = categories,
            categoryId = category,
            query = ui.query,
            prefs = prefs,
            selection = ui.selection.filterTo(mutableSetOf()) { id -> items.any { it.entry.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) = query.update { value }
    fun selectCategory(id: Long?) = categoryId.update { id }

    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun selectAll() = selection.update { state.value.items.map { it.entry.id }.toSet() }
    fun clearSelection() = selection.update { emptySet() }

    fun updatePrefs(block: (LibraryPrefs) -> LibraryPrefs) {
        viewModelScope.launch { preferences.update { it.copy(library = block(it.library)) } }
    }

    fun setStatusForSelection(status: ReadingStatus) = withSelection { repository.setStatus(it, status) }
    fun setCategoriesForSelection(categoryIds: Set<Long>) = withSelection { repository.setCategories(it, categoryIds) }
    fun deleteSelection() = withSelection { repository.deleteEntries(it) }

    private fun withSelection(action: suspend (List<Long>) -> Unit) {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch { action(ids) }
    }
}

package app.yomishiori.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomishiori.data.db.Category
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryItem
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.prefs.LibraryDisplay
import app.yomishiori.data.prefs.LibraryPrefs
import app.yomishiori.data.prefs.LibrarySort
import app.yomishiori.ui.components.AppBottomSheet
import app.yomishiori.ui.components.ConfirmDialog
import app.yomishiori.ui.components.CoverImage
import app.yomishiori.ui.components.EmptyState
import app.yomishiori.ui.containerViewModel
import app.yomishiori.util.formatPercent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpenEntry: (Long) -> Unit, onResume: (Long) -> Unit, onAddNovel: () -> Unit) {
    val vm = containerViewModel { LibraryViewModel(it.libraryRepository, it.preferences) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = state.selection.isNotEmpty()

    BackHandler(selecting) { vm.clearSelection() }
    BackHandler(searching && !selecting) { searching = false; vm.setQuery("") }

    Scaffold(
        topBar = {
            when {
                selecting -> TopAppBar(
                    title = { Text("${state.selection.size} selected") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Default.SelectAll, "Select all") }
                        Box {
                            IconButton(onClick = { statusMenu = true }) { Icon(Icons.Default.CheckCircle, "Change status") }
                            DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }) {
                                ReadingStatus.entries.forEach {
                                    DropdownMenuItem(text = { Text(it.label) }, onClick = { statusMenu = false; vm.setStatusForSelection(it) })
                                }
                            }
                        }
                        IconButton(onClick = { categoryDialog = true }) { Icon(Icons.AutoMirrored.Filled.Label, "Set categories") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    },
                )
                searching -> TopAppBar(
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Search library") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("library_search"),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.setQuery("") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
                    },
                )
                else -> TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { showSheet = true }) {
                            val active = state.prefs.statusFilter.isNotEmpty() || state.prefs.typeFilter.isNotEmpty()
                            Icon(
                                Icons.Default.FilterList,
                                "Filter and sort",
                                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.categories.isNotEmpty()) {
                val tabs = listOf<Category?>(null) + state.categories
                PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it?.id == state.categoryId }.coerceAtLeast(0), edgePadding = 8.dp) {
                    tabs.forEach { category ->
                        Tab(
                            selected = category?.id == state.categoryId,
                            onClick = { vm.selectCategory(category?.id) },
                            text = { Text(category?.name ?: "All", maxLines = 1) },
                        )
                    }
                }
            }
            when {
                state.loading -> Unit
                state.libraryEmpty -> EmptyState(
                    Icons.AutoMirrored.Filled.LibraryBooks,
                    "Your library is empty",
                    "Add a web novel by its URL, import EPUB files, or add a book manually.",
                ) { Button(onClick = onAddNovel) { Text("Add a novel") } }
                state.items.isEmpty() -> EmptyState(Icons.Default.Search, "Nothing matches", "Try another search or clear the filters.")
                else -> LibraryContent(
                    items = state.items,
                    prefs = state.prefs,
                    selection = state.selection,
                    onClick = { item -> if (selecting) vm.toggleSelection(item.entry.id) else onOpenEntry(item.entry.id) },
                    onLongClick = { vm.toggleSelection(it.entry.id) },
                    onResume = onResume,
                )
            }
        }
    }

    if (showSheet) {
        AppBottomSheet(onDismissRequest = { showSheet = false }) {
            LibrarySheet(state.prefs, vm::updatePrefs)
        }
    }
    if (categoryDialog) {
        CategoryPickerDialog(state.categories, initial = emptySet(), onConfirm = vm::setCategoriesForSelection, onDismiss = { categoryDialog = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${state.selection.size} entries?",
            message = "Reading progress and imported EPUB files for these entries will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = vm::deleteSelection,
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryContent(
    items: List<LibraryItem>,
    prefs: LibraryPrefs,
    selection: Set<Long>,
    onClick: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit,
    onResume: (Long) -> Unit,
) {
    if (prefs.display == LibraryDisplay.LIST) {
        LazyColumn(Modifier.fillMaxSize().testTag("library_list"), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.entry.id }) { item ->
                val selected = item.entry.id in selection
                ListItem(
                    headlineContent = { Text(item.entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(subtitle(item)) },
                    leadingContent = { CoverImage(item.entry.coverPath, null, Modifier.width(48.dp)) },
                    trailingContent = {
                        val sourceId = item.entry.lastReadSourceId
                        if (prefs.showResumeButton && sourceId != null && selection.isEmpty()) {
                            IconButton(onClick = { onResume(sourceId) }) { Icon(Icons.Default.PlayArrow, "Continue reading") }
                        }
                    },
                    modifier = Modifier
                        .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier),
                )
            }
        }
        return
    }
    val columns = if (prefs.gridColumns > 0) GridCells.Fixed(prefs.gridColumns) else GridCells.Adaptive(112.dp)
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("library_grid"),
    ) {
        items(items, key = { it.entry.id }) { item ->
            GridItem(
                item = item,
                compact = prefs.display == LibraryDisplay.COMPACT_GRID,
                showProgress = prefs.showProgress,
                showResume = prefs.showResumeButton && selection.isEmpty(),
                selected = item.entry.id in selection,
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) },
                onResume = onResume,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(
    item: LibraryItem,
    compact: Boolean,
    showProgress: Boolean,
    showResume: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onResume: (Long) -> Unit,
) {
    val entry = item.entry
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier
            )
            .padding(2.dp),
    ) {
        Box {
            CoverImage(entry.coverPath, entry.title, Modifier.fillMaxWidth())
            Badge(typeShort(entry.contentType), Modifier.align(Alignment.TopStart).padding(4.dp))
            if (compact) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = if (showProgress && item.progression != null) 8.dp else 6.dp),
                ) {
                    Text(entry.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val sourceId = entry.lastReadSourceId
            if (showResume && sourceId != null) {
                FilledTonalIconButton(
                    onClick = { onResume(sourceId) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
                ) { Icon(Icons.Default.PlayArrow, "Continue reading ${entry.title}", Modifier.size(18.dp)) }
            }
            if (showProgress && item.progression != null) {
                LinearProgressIndicator(
                    progress = { item.progression.toFloat() },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    drawStopIndicator = {},
                )
            }
        }
        if (!compact) {
            Text(
                entry.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
            Text(
                subtitle(item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

private fun typeShort(type: ContentType) = when (type) {
    ContentType.LIGHT_NOVEL -> "LN"
    ContentType.WEB_NOVEL -> "WN"
    ContentType.EPUB -> "EPUB"
    ContentType.BOOK -> "Book"
}

private fun subtitle(item: LibraryItem): String =
    listOfNotNull(item.entry.readingStatus.label, formatPercent(item.progression) ?: item.chapterLabel).joinToString(" · ")

@Composable
private fun LibrarySheet(prefs: LibraryPrefs, update: ((LibraryPrefs) -> LibraryPrefs) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.padding(bottom = 24.dp)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Filter", "Sort", "Display").forEachIndexed { i, label -> Tab(tab == i, onClick = { tab = i }, text = { Text(label) }) }
        }
        when (tab) {
            0 -> Column {
                SectionLabel("Status")
                ReadingStatus.entries.forEach { status ->
                    CheckRow(status.label, status in prefs.statusFilter) { checked ->
                        update { it.copy(statusFilter = if (checked) it.statusFilter + status else it.statusFilter - status) }
                    }
                }
                SectionLabel("Type")
                ContentType.entries.forEach { type ->
                    CheckRow(type.label, type in prefs.typeFilter) { checked ->
                        update { it.copy(typeFilter = if (checked) it.typeFilter + type else it.typeFilter - type) }
                    }
                }
            }
            1 -> Column {
                LibrarySort.entries.forEach { sort ->
                    val selected = prefs.sort == sort
                    ListItem(
                        headlineContent = { Text(sort.label) },
                        trailingContent = {
                            if (selected) Icon(Icons.Default.SwapVert, if (prefs.sortAscending) "Ascending" else "Descending")
                        },
                        supportingContent = { if (selected) Text(if (prefs.sortAscending) "Ascending" else "Descending") },
                        modifier = Modifier.clickable {
                            update { if (it.sort == sort) it.copy(sortAscending = !it.sortAscending) else it.copy(sort = sort, sortAscending = sort == LibrarySort.TITLE) }
                        },
                    )
                }
            }
            else -> Column {
                SectionLabel("Layout")
                LibraryDisplay.entries.forEach { display ->
                    CheckRow(display.label, prefs.display == display, radio = true) { update { it.copy(display = display) } }
                }
                if (prefs.display != LibraryDisplay.LIST) {
                    SectionLabel("Columns: ${if (prefs.gridColumns == 0) "Auto" else prefs.gridColumns}")
                    Slider(
                        value = prefs.gridColumns.toFloat(),
                        onValueChange = { v -> update { it.copy(gridColumns = v.toInt()) } },
                        valueRange = 0f..6f,
                        steps = 5,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                SwitchRow("Show progress", prefs.showProgress) { v -> update { it.copy(showProgress = v) } }
                SwitchRow("Show continue reading button", prefs.showResumeButton) { v -> update { it.copy(showResumeButton = v) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun CheckRow(label: String, checked: Boolean, radio: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) androidx.compose.material3.RadioButton(selected = checked, onClick = { onChange(true) })
        else Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun CategoryPickerDialog(categories: List<Category>, initial: Set<Long>, onConfirm: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            if (categories.isEmpty()) {
                Text("No categories yet. Create them in Settings → Library.")
            } else {
                Column {
                    categories.forEach { c ->
                        CheckRow(c.name, c.id in selected) { checked -> selected = if (checked) selected + c.id else selected - c.id }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDismiss(); onConfirm(selected) }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

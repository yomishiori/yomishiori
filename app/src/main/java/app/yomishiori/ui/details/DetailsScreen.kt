package app.yomishiori.ui.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomishiori.data.db.ReadingSource
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.data.db.SourceType
import app.yomishiori.ui.components.ConfirmDialog
import app.yomishiori.ui.components.CoverImage
import app.yomishiori.ui.components.TextInputDialog
import app.yomishiori.ui.containerViewModel
import app.yomishiori.ui.library.CategoryPickerDialog
import app.yomishiori.util.formatPercent
import app.yomishiori.util.normalizeWebUrl
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(entryId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenSource: (Long) -> Unit) {
    val vm = containerViewModel(key = "details_$entryId") { DetailsViewModel(entryId, it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var addUrlDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var relinkTarget by remember { mutableStateOf<ReadingSource?>(null) }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.loading, state.entry) { if (!state.loading && state.entry == null) onBack() }

    val pickEpub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = relinkTarget
            if (target != null) vm.relinkEpub(target, uri) else vm.addEpubSource(uri)
        }
        relinkTarget = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { onEdit(entryId) }) { Icon(Icons.Default.Edit, "Edit metadata") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete entry") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val entry = state.entry ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverImage(entry.coverPath, entry.title, Modifier.width(120.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleLarge)
                        entry.alternativeTitles.forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        entry.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(entry.contentType.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    AssistChip(onClick = { statusMenu = true }, label = { Text(entry.readingStatus.label) })
                    DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }) {
                        ReadingStatus.entries.forEach {
                            DropdownMenuItem(text = { Text(it.label) }, onClick = { statusMenu = false; vm.setStatus(it) })
                        }
                    }
                }
                val names = state.categories.filter { it.id in state.categoryIds }.joinToString { it.name }
                AssistChip(onClick = { categoryDialog = true }, label = { Text(names.ifEmpty { "Categories" }) })
                entry.tags.forEach { FilterChip(selected = false, onClick = {}, label = { Text(it) }) }
            }

            val primary = vm.primarySource(state)
            if (primary != null) {
                Button(onClick = { onOpenSource(primary.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (primary.progressUpdatedAt != null) "Continue · ${primary.name}" else "Start reading")
                }
            }

            entry.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .animateContentSize()
                        .clickable { descriptionExpanded = !descriptionExpanded },
                )
            }

            Text(
                buildString {
                    append("Added ${formatDate(entry.dateAdded)}")
                    entry.lastReadAt?.let { append(" · Last read ${formatDate(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { addMenuOpen = true }) {
                        Icon(Icons.Default.Add, null)
                        Text("Add source")
                    }
                    DropdownMenu(addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Website URL") },
                            leadingIcon = { Icon(Icons.Default.Language, null) },
                            onClick = { addMenuOpen = false; addUrlDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("EPUB file") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                            onClick = { addMenuOpen = false; pickEpub.launch(arrayOf("application/epub+zip")) },
                        )
                    }
                }
            }
            if (state.sources.isEmpty()) {
                Text(
                    "No sources yet. Add the website you read it on, or an EPUB file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.sources.forEach { source ->
                SourceCard(
                    source = source,
                    fileMissing = vm.isFileMissing(source),
                    onOpen = { onOpenSource(source.id) },
                    onRename = { vm.renameSource(source, it) },
                    onManualPosition = { url, chapter, note -> vm.setManualPosition(source, url, chapter, note) },
                    onHostRule = { vm.setHostRule(source, it) },
                    onRelink = { relinkTarget = source; pickEpub.launch(arrayOf("application/epub+zip")) },
                    onDelete = { vm.deleteSource(source) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addUrlDialog) {
        AddUrlDialog(onConfirm = { url, name -> vm.addWebSource(url, name) }, onDismiss = { addUrlDialog = false })
    }
    if (categoryDialog) {
        CategoryPickerDialog(state.categories, state.categoryIds, onConfirm = vm::setCategories, onDismiss = { categoryDialog = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete \"${state.entry?.title}\"?",
            message = "Its reading progress, history and imported EPUB files will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = vm::deleteEntry,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SourceCard(
    source: ReadingSource,
    fileMissing: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onManualPosition: (url: String?, chapter: String, note: String) -> Unit,
    onHostRule: (String) -> Unit,
    onRelink: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(false) }
    var hostRule by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val web = source.sourceType == SourceType.WEB_URL

    Card(onClick = { if (fileMissing) onRelink() else onOpen() }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (web) Icons.Default.Language else Icons.AutoMirrored.Filled.MenuBook, null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (fileMissing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Text(" File missing, tap to relink", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val detail = listOfNotNull(
                    source.chapterLabel,
                    formatPercent(source.progression),
                    if (web) (source.lastUrl ?: source.originalUrl) else null,
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                source.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                source.lastReadAt?.let {
                    Text("Read ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                source.progression?.let { LinearProgressIndicator({ it.toFloat() }, Modifier.fillMaxWidth().padding(top = 4.dp)) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Source options") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Set position manually") }, onClick = { menu = false; position = true })
                    if (web) DropdownMenuItem(text = { Text("Tracked sites") }, onClick = { menu = false; hostRule = true })
                    if (!web) DropdownMenuItem(text = { Text("Replace EPUB file") }, onClick = { menu = false; onRelink() })
                    DropdownMenuItem(text = { Text("Remove source") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
    }

    if (rename) TextInputDialog("Rename source", "Name", source.name, onConfirm = onRename, onDismiss = { rename = false })
    if (hostRule) {
        TextInputDialog(
            title = "Tracked sites",
            label = "Hosts, comma separated (empty = same site)",
            initial = source.trackedHostRule.orEmpty(),
            validate = { null },
            onConfirm = onHostRule,
            onDismiss = { hostRule = false },
        )
    }
    if (position) {
        var url by remember { mutableStateOf(source.lastUrl ?: source.originalUrl.orEmpty()) }
        var chapter by remember { mutableStateOf(source.chapterLabel.orEmpty()) }
        var note by remember { mutableStateOf(source.note.orEmpty()) }
        val normalized = if (web) normalizeWebUrl(url) else null
        AlertDialog(
            onDismissRequest = { position = false },
            title = { Text("Reading position") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (web) {
                        OutlinedTextField(url, { url = it }, label = { Text("Page URL") }, isError = normalized == null, singleLine = true)
                    }
                    OutlinedTextField(chapter, { chapter = it }, label = { Text("Chapter / volume") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Note") })
                }
            },
            confirmButton = {
                TextButton(enabled = !web || normalized != null, onClick = {
                    position = false
                    onManualPosition(normalized, chapter, note)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { position = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Remove \"${source.name}\"?",
            message = if (web) "Its saved reading position will be lost." else "The imported EPUB file and its reading position will be deleted.",
            confirmLabel = "Remove",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun AddUrlDialog(onConfirm: (url: String, name: String) -> Unit, onDismiss: () -> Unit, withTitle: Boolean = false) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val normalized = normalizeWebUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (withTitle) "Add from website" else "Add website") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("URL of the page you are reading") },
                    singleLine = true,
                    isError = url.isNotEmpty() && normalized == null,
                    supportingText = { if (url.startsWith("http://")) Text("Will open over HTTPS") },
                )
                OutlinedTextField(name, { name = it }, label = { Text(if (withTitle) "Title" else "Source name (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null && (!withTitle || name.isNotBlank()),
                onClick = { onDismiss(); onConfirm(normalized!!, name.trim()) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

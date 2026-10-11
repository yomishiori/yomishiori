package app.yomishiori.ui.browse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yomishiori.AppContainer
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.LibraryEntry
import app.yomishiori.data.db.SourceType
import app.yomishiori.data.db.SourceWithEntry
import app.yomishiori.ui.containerViewModel
import app.yomishiori.ui.details.AddUrlDialog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI

class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.libraryRepository
    val sources = repo.observeAllSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val importing = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun importEpubs(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            var imported = 0
            val failures = mutableListOf<String>()
            uris.forEach { uri ->
                try {
                    val epub = container.readium.import(uri)
                    val id = repo.createEntry(
                        LibraryEntry(
                            title = epub.title,
                            author = epub.authors,
                            description = epub.description,
                            coverPath = epub.coverPath,
                            contentType = ContentType.EPUB,
                            dateAdded = 0,
                        )
                    )
                    repo.addEpubSource(id, epub.filePath, epub.title)
                    imported++
                } catch (e: Exception) {
                    failures += e.message ?: "Unknown error"
                }
            }
            importing.value = false
            messages.emit(
                buildString {
                    append("Imported $imported EPUB${if (imported == 1) "" else "s"}")
                    if (failures.isNotEmpty()) append(". ${failures.size} failed: ${failures.first()}")
                }
            )
        }
    }

    fun addFromUrl(url: String, title: String, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val host = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("Website")
            val id = repo.createEntry(LibraryEntry(title = title, contentType = ContentType.WEB_NOVEL, dateAdded = 0))
            repo.addWebSource(id, url, host)
            onAdded(id)
        }
    }
}

@Composable
fun BrowseScreen(onAddManually: () -> Unit, onOpenEntry: (Long) -> Unit, onOpenSource: (Long) -> Unit) {
    val vm = containerViewModel { BrowseViewModel(it) }
    val sources by vm.sources.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var addUrl by remember { mutableStateOf(false) }
    val pickEpubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importEpubs(it) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Browse") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                Action(Icons.Default.Language, "Add from website URL", "Read a web novel in the built-in browser; your page is remembered") { addUrl = true }
                Action(Icons.Default.FileOpen, "Import EPUB files", "Copied into the app so they stay readable offline") {
                    pickEpubs.launch(arrayOf("application/epub+zip"))
                }
                Action(Icons.Default.Edit, "Add novel manually", "Track a book or light novel with your own details", onAddManually)
            }
            if (sources.isNotEmpty()) {
                val groups = sources.groupBy { groupName(it) }.toSortedMap(compareBy<String> { it == LOCAL_EPUBS }.thenBy { it })
                groups.forEach { (group, rows) ->
                    item(key = "group_$group") {
                        Text(
                            group,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.source.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.entryTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(row.source.chapterLabel ?: row.source.lastUrl ?: row.source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                Icon(if (row.source.sourceType == SourceType.WEB_URL) Icons.Default.Language else Icons.AutoMirrored.Filled.MenuBook, null)
                            },
                            trailingContent = {
                                Text("Details", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenEntry(row.source.entryId) }.padding(8.dp))
                            },
                            modifier = Modifier.clickable { onOpenSource(row.source.id) },
                        )
                    }
                }
            }
        }
    }

    if (addUrl) {
        AddUrlDialog(
            withTitle = true,
            onConfirm = { url, title -> vm.addFromUrl(url, title, onOpenEntry) },
            onDismiss = { addUrl = false },
        )
    }
}

private const val LOCAL_EPUBS = "Local EPUBs"

private fun groupName(row: SourceWithEntry): String =
    if (row.source.sourceType == SourceType.EPUB_FILE) LOCAL_EPUBS
    else row.source.originalUrl?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() } ?: "Websites"

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Box { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

package app.yomishiori.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomishiori.container
import app.yomishiori.data.db.HistoryItem
import app.yomishiori.data.db.SourceType
import app.yomishiori.ui.components.CoverImage
import app.yomishiori.ui.components.EmptyState
import app.yomishiori.util.formatPercent
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
fun HistoryScreen(onOpenEntry: (Long) -> Unit, onResume: (Long) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = context.container.libraryRepository
    val history by repo.observeHistory().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        val items = history ?: return@Scaffold
        if (items.isEmpty()) {
            EmptyState(
                Icons.Default.History,
                "Nothing read yet",
                "Books and web novels you open appear here, with where you left off.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        val grouped = items.groupBy { dayLabel(it.history.day) }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            grouped.forEach { (day, rows) ->
                item(key = "header_$day") {
                    Text(
                        day,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { it.history.entryId }) { item ->
                    HistoryRow(
                        item = item,
                        onClick = { onOpenEntry(item.history.entryId) },
                        onResume = { onResume(item.history.sourceId) },
                        onRemove = { scope.launch { repo.removeHistory(item.history.entryId) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(item.coverPath, null, Modifier.width(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val position = when (item.sourceType) {
                SourceType.WEB_URL -> item.history.chapterLabel ?: item.history.lastUrl
                SourceType.EPUB_FILE -> listOfNotNull(item.history.chapterLabel, formatPercent(item.progression)).joinToString(" · ")
            }
            Text(
                "${item.sourceName} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.history.lastReadAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!position.isNullOrBlank()) {
                Text(position, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, "Remove from history") }
        FilledTonalButton(onClick = onResume) {
            Icon(Icons.Default.PlayArrow, null)
            Text("Resume")
        }
    }
}

private fun dayLabel(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date.from(LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }
}

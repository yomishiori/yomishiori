package app.yomishiori.web

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.net.URI

@Composable
fun WebReaderScreen(
    webView: WebView,
    viewModel: WebReaderViewModel,
    immersiveByDefault: Boolean,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var barsVisible by remember { mutableStateOf(true) }
    var fullscreen by rememberSaveable { mutableStateOf(immersiveByDefault) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf<RejectReason?>(null) }
    var showTrackingInfo by remember { mutableStateOf(false) }
    var editChapter by remember { mutableStateOf(false) }

    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }

    DisposableEffect(webView) {
        webView.setOnScrollChangeListener { _, _, y, _, oldY ->
            if (y - oldY > 24) barsVisible = false else if (oldY - y > 24 || y == 0) barsVisible = true
        }
        onDispose { webView.setOnScrollChangeListener(null) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        )

        AnimatedVisibility(barsVisible, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(Modifier.statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close reader") }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { showTrackingInfo = true },
                        ) {
                            Text(
                                state.pageTitle.ifBlank { state.source?.name.orEmpty() },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                state.error ?: state.currentUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("reader_url"),
                            )
                        }
                        TrackingIcon(state.tracking) { showTrackingInfo = true }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Open in browser") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    onClick = { menuOpen = false; onOpenExternal(state.currentUrl) },
                                )
                                val enabled = state.source?.trackingEnabled ?: true
                                DropdownMenuItem(
                                    text = { Text(if (enabled) "Pause auto-tracking" else "Resume auto-tracking") },
                                    leadingIcon = { Icon(if (enabled) Icons.Default.Pause else Icons.Default.PlayArrow, null) },
                                    onClick = { menuOpen = false; viewModel.setTrackingEnabled(!enabled) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Chapter & note") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuOpen = false; editChapter = true },
                                )
                            }
                        }
                    }
                    if (state.progress < 100) LinearProgressIndicator({ state.progress / 100f }, Modifier.fillMaxWidth())
                }
            }
        }

        AnimatedVisibility(
            barsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { webView.goBack() }, enabled = state.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    IconButton(onClick = { webView.goForward() }, enabled = state.canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                    }
                    IconButton(onClick = { webView.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                    Box(Modifier.weight(1f))
                    IconButton(onClick = {
                        val reason = viewModel.rejectReason()
                        if (reason == null) viewModel.saveCurrentPosition() else confirmSave = reason
                    }) { Icon(Icons.Default.Bookmark, "Save position") }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle full screen")
                    }
                }
            }
        }
    }

    confirmSave?.let { reason ->
        AlertDialog(
            onDismissRequest = { confirmSave = null },
            title = { Text("Save this page anyway?") },
            text = { Text("${reason.message}.\n\n${hostOf(state.currentUrl)}\n\nSaving will make this page your reading position.") },
            confirmButton = { TextButton(onClick = { confirmSave = null; viewModel.saveCurrentPosition() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirmSave = null }) { Text("Cancel") } },
        )
    }

    if (showTrackingInfo) {
        AlertDialog(
            onDismissRequest = { showTrackingInfo = false },
            title = { Text("Reading position") },
            text = {
                Column {
                    Text(trackingText(state.tracking))
                    Text("\nSaved position:", style = MaterialTheme.typography.labelLarge)
                    Text(state.source?.lastUrl ?: "Nothing saved yet", style = MaterialTheme.typography.bodySmall)
                    state.source?.chapterLabel?.let { Text("\nChapter: $it") }
                    Text(
                        "\nPages on the same site are saved automatically. Some sites change pages without changing the " +
                            "address. Use the bookmark button to save manually.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showTrackingInfo = false }) { Text("OK") } },
        )
    }

    if (editChapter) {
        var chapter by remember { mutableStateOf(state.source?.chapterLabel.orEmpty()) }
        var note by remember { mutableStateOf(state.source?.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editChapter = false },
            title = { Text("Chapter & note") },
            text = {
                Column {
                    OutlinedTextField(chapter, { chapter = it }, label = { Text("Chapter") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Note") })
                }
            },
            confirmButton = {
                TextButton(onClick = { editChapter = false; viewModel.setChapterAndNote(chapter, note) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editChapter = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrackingIcon(status: TrackingStatus, onClick: () -> Unit) {
    val (icon, description) = when (status) {
        TrackingStatus.Saved -> Icons.Default.CloudDone to "Position saved"
        TrackingStatus.Pending -> Icons.Default.CloudSync to "Saving position"
        is TrackingStatus.NotTracked -> Icons.Default.CloudOff to status.reason.message
    }
    IconButton(onClick = onClick, modifier = Modifier.testTag("tracking_status")) {
        Icon(
            icon,
            description,
            tint = if (status is TrackingStatus.NotTracked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun trackingText(status: TrackingStatus) = when (status) {
    TrackingStatus.Saved -> "This page is your saved reading position."
    TrackingStatus.Pending -> "This page will be saved as your reading position."
    is TrackingStatus.NotTracked -> "${status.reason.message}: this page will not replace your saved position."
}

private fun hostOf(url: String) = runCatching { URI(url).host }.getOrNull() ?: url

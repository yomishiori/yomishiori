package app.yomishiori.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.ReadingStatus
import app.yomishiori.ui.components.CoverImage
import app.yomishiori.ui.components.TextInputDialog
import app.yomishiori.ui.containerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEntryScreen(entryId: Long, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = containerViewModel(key = "edit_$entryId") { EditEntryViewModel(entryId, it) }
    val form by vm.form.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var coverUrlDialog by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::pickCover) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Add novel" else "Edit") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") } },
                actions = {
                    TextButton(
                        onClick = { scope.launch { vm.save()?.let(onSaved) } },
                        enabled = !form.loading,
                        modifier = Modifier.testTag("save_entry"),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (form.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverImage(form.coverPath, "Cover", Modifier.width(96.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Choose image")
                    }
                    OutlinedButton(onClick = { coverUrlDialog = true }) { Text("Image URL") }
                    if (form.coverPath != null) TextButton(onClick = vm::removeCover) { Text("Remove cover") }
                }
            }
            Field("Title", form.title, Modifier.testTag("title_field")) { v -> vm.update { it.copy(title = v) } }
            Field("Alternative titles (one per line, e.g. Japanese title)", form.alternativeTitles, singleLine = false) { v ->
                vm.update { it.copy(alternativeTitles = v) }
            }
            Field("Author", form.author) { v -> vm.update { it.copy(author = v) } }

            Label("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentType.entries.forEach { type ->
                    FilterChip(form.contentType == type, onClick = { vm.update { it.copy(contentType = type) } }, label = { Text(type.label) })
                }
            }
            Label("Status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStatus.entries.forEach { status ->
                    FilterChip(form.status == status, onClick = { vm.update { it.copy(status = status) } }, label = { Text(status.label) })
                }
            }
            if (form.categories.isNotEmpty()) {
                Label("Categories")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.categories.forEach { c ->
                        val selected = c.id in form.categoryIds
                        FilterChip(selected, onClick = {
                            vm.update { it.copy(categoryIds = if (selected) it.categoryIds - c.id else it.categoryIds + c.id) }
                        }, label = { Text(c.name) })
                    }
                }
            }
            Field("Tags (comma separated)", form.tags) { v -> vm.update { it.copy(tags = v) } }
            Field("Description", form.description, singleLine = false) { v -> vm.update { it.copy(description = v) } }
            if (vm.isNew) {
                Field("Website URL to read it on (optional)", form.sourceUrl, Modifier.testTag("url_field")) { v ->
                    vm.update { it.copy(sourceUrl = v) }
                }
            }
        }
    }

    if (coverUrlDialog) {
        TextInputDialog("Cover image URL", "https://…", onConfirm = vm::setCoverUrl, onDismiss = { coverUrlDialog = false })
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall)

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = modifier.fillMaxWidth(),
    )
}

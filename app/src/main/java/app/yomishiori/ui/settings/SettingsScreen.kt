package app.yomishiori.ui.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomishiori.BuildConfig
import app.yomishiori.container
import app.yomishiori.data.db.Category
import app.yomishiori.data.prefs.AppSettings
import app.yomishiori.data.prefs.ThemeMode
import app.yomishiori.epub.ReaderSettings
import app.yomishiori.epub.TutorialBook
import app.yomishiori.epub.epubPreferencesOf
import app.yomishiori.ui.components.AppBottomSheet
import app.yomishiori.ui.components.ConfirmDialog
import app.yomishiori.ui.components.TextInputDialog
import app.yomishiori.ui.theme.supportsDynamicColor
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val settings by c.preferences.settings.collectAsStateWithLifecycle(AppSettings())
    val categories by c.libraryRepository.observeCategories().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var categoriesDialog by remember { mutableStateOf(false) }
    var readerSheet by remember { mutableStateOf(false) }
    var clearBrowserData by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }

    fun update(block: (AppSettings) -> AppSettings) = scope.launch { c.preferences.update(block) }
    fun runBackupTask(success: (Int?) -> String, task: suspend () -> Int?) = c.applicationScope.launch {
        busy = true
        val message = try {
            success(task())
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
        busy = false
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runBackupTask({ "Library metadata exported" }) { c.backupManager.exportJson(it); null } }
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { runBackupTask({ "Full backup exported" }) { c.backupManager.exportZip(it); null } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runBackupTask({ "Restored $it entries" }) { c.backupManager.restore(uri) } }
    }
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Section("Appearance")
            Item("Theme", settings.theme.label) { themeDialog = true }
            if (supportsDynamicColor) {
                SwitchItem("Dynamic colors", "Use colors from your wallpaper (Material You)", settings.dynamicColor) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }

            Section("Library")
            Item("Categories", if (categories.isEmpty()) "None" else categories.joinToString { it.name }) { categoriesDialog = true }
            Item("Layout, sorting and filters", "Use the filter button on the Library screen") {}

            Section("EPUB reader")
            Item("Text and theme", "Font, size, spacing, margins, light/sepia/dark, scrolling") { readerSheet = true }

            Section("Browser")
            SwitchItem("JavaScript", "Most novel sites need it", settings.browser.javaScript) { v ->
                update { it.copy(browser = it.browser.copy(javaScript = v)) }
            }
            SwitchItem("Darken web pages", "Darken sites that have no dark theme", settings.browser.darkenPages) { v ->
                update { it.copy(browser = it.browser.copy(darkenPages = v)) }
            }
            SwitchItem("Full screen by default", "Hide the status bar while reading", settings.browser.immersiveByDefault) { v ->
                update { it.copy(browser = it.browser.copy(immersiveByDefault = v)) }
            }
            SwitchItem("Third-party cookies", "Only needed by some login systems", settings.browser.thirdPartyCookies) { v ->
                update { it.copy(browser = it.browser.copy(thirdPartyCookies = v)) }
            }
            var zoom by remember(settings.browser.textZoom) { mutableFloatStateOf(settings.browser.textZoom.toFloat()) }
            ListItem(
                headlineContent = { Text("Text size: ${zoom.toInt()}%") },
                supportingContent = {
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        onValueChangeFinished = { update { it.copy(browser = it.browser.copy(textZoom = zoom.toInt())) } },
                        valueRange = 50f..200f,
                        steps = 14,
                    )
                },
            )
            Item("Clear cookies and site data", "Signs you out of websites") { clearBrowserData = true }

            Section("Data")
            Item("Export library metadata (.json)", "Entries, sources, progress, categories and history. EPUB files are not included.") {
                exportJson.launch("yomishiori-library-$stamp.json")
            }
            Item("Export full backup (.zip)", "Everything above plus imported EPUB files and covers") {
                exportZip.launch("yomishiori-backup-$stamp.zip")
            }
            Item("Restore from backup", "Replaces the current library with a .json or .zip backup") { confirmRestore = true }

            Section("About")
            Item("Yomishiori ${BuildConfig.VERSION_NAME}", "Local-first reader for EPUBs, light novels and web novels. No account, no tracking.") {}
            Item("Show tutorial again", "Open the short guide on reading with Yomishiori") {
                scope.launch {
                    try {
                        TutorialBook.open(context)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Couldn't open the tutorial.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            Item("Open-source licenses", "Readium, AndroidX, Kotlin, Coil, OkHttp") { licenses = true }
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable { themeDialog = false; update { it.copy(theme = mode) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(settings.theme == mode, onClick = null)
                            Text(mode.label, Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (categoriesDialog) {
        CategoriesDialog(categories, onDismiss = { categoriesDialog = false })
    }
    if (readerSheet) {
        AppBottomSheet(onDismissRequest = { readerSheet = false }) {
            ReaderSettings(
                preferences = epubPreferencesOf(settings.epubPreferencesJson),
                onChange = { prefs -> update { it.copy(epubPreferencesJson = EpubPreferencesSerializer().serialize(prefs)) } },
                reader = settings.reader,
                onReaderChange = { reader -> update { it.copy(reader = reader) } },
            )
        }
    }
    if (clearBrowserData) {
        ConfirmDialog(
            title = "Clear browser data?",
            message = "Cookies, site storage and cache will be removed. You will be signed out of websites. Reading positions are kept.",
            confirmLabel = "Clear",
            onConfirm = {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                WebView(context).apply { clearCache(true); destroy() }
                scope.launch { snackbar.showSnackbar("Browser data cleared") }
            },
            onDismiss = { clearBrowserData = false },
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore a backup?",
            message = "Your current library, progress and history will be replaced by the backup's contents.",
            confirmLabel = "Choose file",
            onConfirm = { restore.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
            onDismiss = { confirmRestore = false },
        )
    }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "Readium Kotlin Toolkit (BSD 3-Clause)\n" +
                        "AndroidX, Jetpack Compose, Room, DataStore (Apache 2.0)\n" +
                        "Kotlin, kotlinx.coroutines, kotlinx.serialization (Apache 2.0)\n" +
                        "Coil (Apache 2.0)\n" +
                        "OkHttp (Apache 2.0)\n" +
                        "Material Symbols icons (Apache 2.0)",
                )
            },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun CategoriesDialog(categories: List<Category>, onDismiss: () -> Unit) {
    val repo = LocalContext.current.container.libraryRepository
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val names = categories.map { it.name.lowercase() }
    val validate = { name: String, except: String? ->
        when {
            name.isBlank() -> "Required"
            name.trim().lowercase() != except?.lowercase() && name.trim().lowercase() in names -> "Already exists"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column {
                if (categories.isEmpty()) Text("Create categories to group your library into tabs.")
                categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category.name, Modifier.weight(1f))
                        IconButton(onClick = { renaming = category }) { Icon(Icons.Default.Edit, "Rename ${category.name}") }
                        IconButton(onClick = { deleting = category }) { Icon(Icons.Default.Delete, "Delete ${category.name}") }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null)
                    Text("New category")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    if (adding) {
        TextInputDialog("New category", "Name", validate = { validate(it, null) }, onConfirm = { scope.launch { repo.createCategory(it) } }, onDismiss = { adding = false })
    }
    renaming?.let { category ->
        TextInputDialog(
            "Rename category", "Name", category.name,
            validate = { validate(it, category.name) },
            onConfirm = { scope.launch { repo.renameCategory(category, it) } },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            "Delete \"${category.name}\"?", "Entries stay in your library; only the category is removed.", "Delete",
            onConfirm = { scope.launch { repo.deleteCategory(category) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Item(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

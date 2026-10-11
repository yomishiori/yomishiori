package app.yomishiori.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.yomishiori.ui.components.AppBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomishiori.util.formatPercent
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@Composable
fun EpubReaderChrome(
    visible: Boolean,
    title: String,
    locator: Locator?,
    toc: List<Link>,
    preferences: EpubPreferences,
    onBack: () -> Unit,
    onTocClick: (Link) -> Unit,
    onSeek: (Double) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    var showToc by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        locator?.title?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showToc = true }) { Icon(Icons.AutoMirrored.Filled.List, "Table of contents") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.TextFields, "Reader settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ProgressBar(locator, onSeek)
        }
    }

    if (showToc) {
        AppBottomSheet(onDismissRequest = { showToc = false }) {
            TocList(toc, currentHref = locator?.href?.toString()) {
                showToc = false
                onTocClick(it)
            }
        }
    }
    if (showSettings) {
        AppBottomSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettings(preferences, onPreferencesChange)
        }
    }
}

@Composable
private fun ProgressBar(locator: Locator?, onSeek: (Double) -> Unit) {
    val progression = locator?.locations?.totalProgression ?: 0.0
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    locator?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatPercent(if (dragging) dragValue.toDouble() else progression).orEmpty(), style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = if (dragging) dragValue else progression.toFloat(),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { dragging = false; onSeek(dragValue.toDouble()) },
            )
        }
    }
}

@Composable
private fun TocList(toc: List<Link>, currentHref: String?, onClick: (Link) -> Unit) {
    val flat = remember(toc) { flatten(toc, 0) }
    if (flat.isEmpty()) {
        Text("This book has no table of contents.", Modifier.padding(24.dp))
        return
    }
    LazyColumn {
        items(flat) { (link, depth) ->
            val current = currentHref != null && link.href.toString().substringBefore('#') == currentHref.substringBefore('#')
            Text(
                link.title ?: link.href.toString(),
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(link) }
                    .padding(start = (24 + depth * 16).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }
}

private fun flatten(links: List<Link>, depth: Int): List<Pair<Link, Int>> =
    links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }

private val fontChoices = listOf(
    null to "Publisher",
    FontFamily.SERIF to "Serif",
    FontFamily.SANS_SERIF to "Sans",
    FontFamily.MONOSPACE to "Mono",
    FontFamily.OPEN_DYSLEXIC to "OpenDyslexic",
    FontFamily.ACCESSIBLE_DFA to "AccessibleDfA",
)

@Composable
fun ReaderSettings(preferences: EpubPreferences, onChange: (EpubPreferences) -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        val themes = listOf(Theme.LIGHT to "Light", Theme.SEPIA to "Sepia", Theme.DARK to "Dark")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            themes.forEachIndexed { i, (theme, label) ->
                SegmentedButton(
                    selected = (preferences.theme ?: Theme.LIGHT) == theme,
                    onClick = { onChange(preferences.copy(theme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                ) { Text(label) }
            }
        }

        Text("Font", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fontChoices.forEach { (family, label) ->
                FilterChip(
                    selected = preferences.fontFamily == family,
                    onClick = {
                        onChange(preferences.copy(fontFamily = family, publisherStyles = if (family == null) preferences.publisherStyles else false))
                    },
                    label = { Text(label) },
                )
            }
        }

        LabeledSlider("Font size", preferences.fontSize ?: 1.0, 0.5..2.5, "%.0f%%".format((preferences.fontSize ?: 1.0) * 100)) {
            onChange(preferences.copy(fontSize = it))
        }
        LabeledSlider("Line height", preferences.lineHeight ?: 1.5, 1.0..2.2, "%.1f".format(preferences.lineHeight ?: 1.5)) {
            onChange(preferences.copy(lineHeight = it, publisherStyles = false))
        }
        LabeledSlider("Margins", preferences.pageMargins ?: 1.0, 0.5..2.5, "%.1f".format(preferences.pageMargins ?: 1.0)) {
            onChange(preferences.copy(pageMargins = it))
        }

        SwitchRow("Continuous scrolling", preferences.scroll ?: false) { onChange(preferences.copy(scroll = it)) }
        SwitchRow("Publisher styles", preferences.publisherStyles ?: true) { onChange(preferences.copy(publisherStyles = it)) }
        SwitchRow("Vertical text (Japanese)", preferences.verticalText ?: false) { onChange(preferences.copy(verticalText = it)) }

        HorizontalDivider()
        TextButton(onClick = { onChange(EpubPreferences()) }) { Text("Reset to defaults") }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Double, range: ClosedFloatingPointRange<Double>, display: String, onChange: (Double) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(Math.round(local * 10) / 10.0) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

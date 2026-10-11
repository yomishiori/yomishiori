package app.yomishiori.util

import java.text.Normalizer

fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().trim()

fun searchKeyOf(title: String, alternativeTitles: List<String>, author: String?): String =
    (listOf(title) + alternativeTitles + listOfNotNull(author))
        .joinToString("\n") { normalizeForSearch(it) }

fun splitList(text: String, separators: Regex = Regex("[\\n,、]")): List<String> =
    text.split(separators).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun formatPercent(progression: Double?): String? =
    progression?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" }

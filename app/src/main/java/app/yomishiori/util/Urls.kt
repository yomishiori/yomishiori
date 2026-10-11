package app.yomishiori.util

import java.net.URI

fun normalizeWebUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring("http://".length)
        trimmed.contains("://") -> return null
        else -> "https://$trimmed"
    }
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (!host.contains('.') && host != "localhost") return null
    return withScheme
}

package app.yomishiori.web

import java.net.URI

enum class RejectReason(val message: String) {
    NOT_WEB("Not a web page"),
    OTHER_SITE("Different site, not tracked"),
    EXCLUDED_PAGE("Login page, not tracked"),
    PAUSED("Tracking paused"),
    REDIRECTED("Redirected from your saved page, not saved"),
}

object UrlProgressPolicy {
    private val excludedSegments = setOf(
        "login", "signin", "sign-in", "signup", "sign-up", "register", "auth", "oauth", "oauth2",
        "account", "accounts", "logout", "signout", "sso", "cdn-cgi", "captcha",
    )

    fun rejectReason(url: String, originalUrl: String?, hostRule: String?, trackingEnabled: Boolean): RejectReason? {
        if (!trackingEnabled) return RejectReason.PAUSED
        val uri = parse(url) ?: return RejectReason.NOT_WEB
        val host = uri.host?.lowercase() ?: return RejectReason.NOT_WEB
        if (!matchesSite(host, originalUrl, hostRule)) return RejectReason.OTHER_SITE
        val segments = uri.rawPath.orEmpty().lowercase().split('/')
        if (segments.any { it in excludedSegments }) return RejectReason.EXCLUDED_PAGE
        return null
    }

    fun isWebUrl(url: String): Boolean = parse(url) != null

    private fun matchesSite(host: String, originalUrl: String?, hostRule: String?): Boolean {
        val ruleHosts = hostRule?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()
        if (ruleHosts.isNotEmpty()) return ruleHosts.any { host == it || host.endsWith(".$it") }
        val originalHost = originalUrl?.let(::parse)?.host?.lowercase() ?: return false
        return registrableDomain(host) == registrableDomain(originalHost)
    }

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()
        ?.takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }
        ?.takeIf { !it.host.isNullOrEmpty() }

    private val secondLevelLabels = setOf("co", "com", "net", "org", "ac", "ne", "or", "go", "gov", "edu", "ed", "gr", "lg")

    fun registrableDomain(host: String): String {
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) return host
        val labels = host.trimEnd('.').split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val take = if (tld.length == 2 && labels[labels.size - 2] in secondLevelLabels) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }
}

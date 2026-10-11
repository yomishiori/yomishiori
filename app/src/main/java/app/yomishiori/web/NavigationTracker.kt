package app.yomishiori.web

class NavigationTracker {
    private var loading = false
    private var failedUrl: String? = null

    fun onPageStarted(url: String) {
        loading = true
        if (url.withoutFragment() != failedUrl) failedUrl = null
    }

    fun onMainFrameError(url: String) {
        failedUrl = url.withoutFragment()
    }

    fun onHistoryUpdated(url: String): String? = if (!loading) confirm(url) else null

    fun onPageCommitVisible(url: String): String? {
        loading = false
        return confirm(url)
    }

    fun onPageFinished(url: String): String? {
        loading = false
        return confirm(url)
    }

    private fun confirm(url: String) = url.takeIf { it.withoutFragment() != failedUrl }

    private fun String.withoutFragment() = substringBefore('#')
}

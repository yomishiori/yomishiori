package app.yomishiori.web

class NavigationTracker {
    private var loading = false
    private var failedUrl: String? = null
    private var failedLoadDone = false

    fun onPageStarted(url: String) {
        loading = true
        if (failedUrl != null && (url.withoutFragment() != failedUrl || failedLoadDone)) failedUrl = null
    }

    fun onMainFrameError(url: String) {
        failedUrl = url.withoutFragment()
        failedLoadDone = false
    }

    fun onHistoryUpdated(url: String): String? = if (!loading) confirm(url) else null

    fun onPageCommitVisible(url: String): String? = loadDone(url)

    fun onPageFinished(url: String): String? = loadDone(url)

    private fun loadDone(url: String): String? {
        loading = false
        val confirmed = confirm(url)
        if (confirmed == null) failedLoadDone = true
        return confirmed
    }

    private fun confirm(url: String) = url.takeIf { it.withoutFragment() != failedUrl }

    private fun String.withoutFragment() = substringBefore('#')
}

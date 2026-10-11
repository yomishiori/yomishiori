package app.yomishiori.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WebProgressRecorder(
    private val timerScope: CoroutineScope,
    private val saveScope: CoroutineScope,
    initialSavedUrl: String?,
    private val debounceMillis: Long = 1_500,
    private val save: suspend (String) -> Unit,
) {
    var lastSavedUrl: String? = initialSavedUrl
        private set
    private var pending: String? = null
    private var timer: Job? = null

    fun offer(url: String) {
        timer?.cancel()
        if (url == lastSavedUrl) {
            pending = null
            return
        }
        pending = url
        timer = timerScope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    fun flush(): Job? {
        timer?.cancel()
        val url = pending ?: return null
        pending = null
        lastSavedUrl = url
        return saveScope.launch { save(url) }
    }

    fun saveNow(url: String): Job {
        timer?.cancel()
        pending = null
        lastSavedUrl = url
        return saveScope.launch { save(url) }
    }
}

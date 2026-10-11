package app.yomishiori.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomishiori.data.db.ReadingSource
import app.yomishiori.data.repo.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TrackingStatus {
    data object Saved : TrackingStatus
    data object Pending : TrackingStatus
    data class NotTracked(val reason: RejectReason) : TrackingStatus
}

data class WebReaderState(
    val source: ReadingSource? = null,
    val currentUrl: String = "",
    val pageTitle: String = "",
    val progress: Int = 100,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val tracking: TrackingStatus = TrackingStatus.Pending,
    val error: String? = null,
)

class WebReaderViewModel(
    private val sourceId: Long,
    private val repository: ProgressRepository,
    private val saveScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow(WebReaderState())
    val state: StateFlow<WebReaderState> = _state

    private val tracker = NavigationTracker()
    private var recorder: WebProgressRecorder? = null

    init {
        viewModelScope.launch {
            repository.observeSource(sourceId).collect { source ->
                if (source != null && recorder == null) {
                    recorder = WebProgressRecorder(viewModelScope, saveScope, source.lastUrl) {
                        repository.saveWebProgress(sourceId, it)
                        refreshTracking()
                    }
                }
                _state.update { it.copy(source = source) }
                refreshTracking()
            }
        }
    }

    suspend fun startUrl(): String? {
        val source = _state.map { it.source }.filterNotNull().first()
        restoringUrl = source.lastUrl
        return source.lastUrl ?: source.originalUrl
    }

    private var restoringUrl: String? = null
    private var redirectedUrl: String? = null

    private var errorUrl: String? = null

    fun onPageStarted(url: String) {
        tracker.onPageStarted(url)
        if (url != errorUrl) errorUrl = null
        _state.update { it.copy(currentUrl = url, error = if (errorUrl == null) null else it.error) }
        refreshTracking()
    }

    fun onMainFrameError(message: String, url: String) {
        tracker.onMainFrameError(url)
        errorUrl = url
        _state.update { it.copy(error = message) }
    }

    fun onHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        _state.update { it.copy(currentUrl = url, canGoBack = canGoBack, canGoForward = canGoForward) }
        tracker.onHistoryUpdated(url)?.let(::onConfirmed) ?: refreshTracking()
    }

    fun onPageCommitVisible(url: String) {
        tracker.onPageCommitVisible(url)?.let(::onConfirmed)
    }

    fun onPageFinished(url: String) {
        tracker.onPageFinished(url)?.let(::onConfirmed) ?: refreshTracking()
    }

    fun onProgress(progress: Int) = _state.update { it.copy(progress = progress) }
    fun onTitle(title: String) = _state.update { it.copy(pageTitle = title) }

    private fun onConfirmed(url: String) {
        _state.update { it.copy(currentUrl = url) }
        restoringUrl?.let { restoring ->
            restoringUrl = null
            if (url != restoring) redirectedUrl = url
        }
        if (rejectReason(url) == null) recorder?.offer(url)
        refreshTracking()
    }

    fun rejectReason(url: String = _state.value.currentUrl, source: ReadingSource? = _state.value.source): RejectReason? {
        source ?: return RejectReason.PAUSED
        if (url == redirectedUrl) return RejectReason.REDIRECTED
        return UrlProgressPolicy.rejectReason(url, source.originalUrl, source.trackedHostRule, source.trackingEnabled)
    }

    fun saveCurrentPosition() {
        val url = _state.value.currentUrl
        if (UrlProgressPolicy.isWebUrl(url)) recorder?.saveNow(url)
        refreshTracking()
    }

    fun setTrackingEnabled(enabled: Boolean) {
        saveScope.launch { repository.setTrackingEnabled(sourceId, enabled) }
    }

    fun setChapterAndNote(chapter: String, note: String) {
        saveScope.launch { repository.setManualPosition(sourceId, url = null, chapterLabel = chapter, note = note) }
    }

    fun flush() {
        recorder?.flush()
    }

    private fun refreshTracking() {
        _state.update { s ->
            val reason = if (s.error != null) null else rejectReason(s.currentUrl, s.source)
            val status = when {
                reason != null -> TrackingStatus.NotTracked(reason)
                s.currentUrl.isNotEmpty() && s.currentUrl == recorder?.lastSavedUrl -> TrackingStatus.Saved
                else -> TrackingStatus.Pending
            }
            s.copy(tracking = status)
        }
    }

    override fun onCleared() {
        flush()
    }
}

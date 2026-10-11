package app.yomishiori.epub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

class EpubSession(
    val sourceId: Long,
    val publication: Publication,
    var locator: Locator?,
    var preferences: EpubPreferences,
)

class EpubSessions(private val scope: CoroutineScope) {
    private val sessions = mutableMapOf<Long, EpubSession>()

    fun put(session: EpubSession) {
        sessions.put(session.sourceId, session)?.takeIf { it !== session }?.let { closeLater(it.publication) }
    }

    fun get(sourceId: Long): EpubSession? = sessions[sourceId]

    fun close(sourceId: Long) {
        sessions.remove(sourceId)?.let { closeLater(it.publication) }
    }

    private fun closeLater(publication: Publication) {
        scope.launch {
            delay(10_000)
            publication.close()
        }
    }
}

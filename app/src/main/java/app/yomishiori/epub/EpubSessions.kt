package app.yomishiori.epub

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

class EpubSession(
    val sourceId: Long,
    val publication: Publication,
    var locator: Locator?,
    var preferences: EpubPreferences,
)

class EpubSessions {
    private val sessions = mutableMapOf<Long, EpubSession>()

    fun put(session: EpubSession) {
        sessions.put(session.sourceId, session)?.takeIf { it !== session }?.publication?.close()
    }

    fun get(sourceId: Long): EpubSession? = sessions[sourceId]

    fun close(sourceId: Long) {
        sessions.remove(sourceId)?.publication?.close()
    }
}

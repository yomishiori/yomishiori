package app.yomishiori.data.repo

import androidx.room.withTransaction
import app.yomishiori.data.db.AppDatabase
import app.yomishiori.data.db.ReadingHistory
import java.time.Instant
import java.time.ZoneId

class ProgressRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sourceDao = db.sourceDao()

    suspend fun getSource(id: Long) = sourceDao.get(id)
    fun observeSource(id: Long) = sourceDao.observe(id)

    suspend fun saveWebProgress(sourceId: Long, url: String) = db.withTransaction {
        val source = sourceDao.get(sourceId) ?: return@withTransaction
        val now = clock()
        sourceDao.updateWebProgress(sourceId, url, now)
        recordRead(source.entryId, sourceId, now, lastUrl = url, chapterLabel = source.chapterLabel)
    }

    suspend fun saveEpubProgress(sourceId: Long, locatorJson: String, progression: Double?, chapterLabel: String?) =
        db.withTransaction {
            val source = sourceDao.get(sourceId) ?: return@withTransaction
            val now = clock()
            sourceDao.updateEpubProgress(sourceId, locatorJson, progression, chapterLabel, now)
            recordRead(source.entryId, sourceId, now, locatorJson = locatorJson, chapterLabel = chapterLabel)
        }

    suspend fun setManualPosition(sourceId: Long, url: String?, chapterLabel: String?, note: String?) =
        db.withTransaction {
            val source = sourceDao.get(sourceId) ?: return@withTransaction
            val now = clock()
            val updated = source.copy(
                lastUrl = url?.takeIf { it.isNotBlank() } ?: source.lastUrl,
                chapterLabel = chapterLabel?.let { it.ifBlank { null } } ?: source.chapterLabel,
                note = note?.let { it.ifBlank { null } } ?: source.note,
                lastReadAt = now,
                progressUpdatedAt = now,
            )
            sourceDao.update(updated)
            recordRead(source.entryId, sourceId, now, lastUrl = updated.lastUrl, chapterLabel = updated.chapterLabel)
        }

    suspend fun setTrackingEnabled(sourceId: Long, enabled: Boolean) = sourceDao.setTrackingEnabled(sourceId, enabled)

    private suspend fun recordRead(
        entryId: Long,
        sourceId: Long,
        now: Long,
        lastUrl: String? = null,
        locatorJson: String? = null,
        chapterLabel: String? = null,
    ) {
        db.libraryDao().markRead(entryId, sourceId, now)
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        db.historyDao().upsert(ReadingHistory(sourceId, day, entryId, now, lastUrl, locatorJson, chapterLabel))
    }
}

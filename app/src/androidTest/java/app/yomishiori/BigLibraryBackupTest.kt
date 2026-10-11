package app.yomishiori

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BigLibraryBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.getExternalFilesDir(null), "backup-report.txt")

    @Test
    fun backupAndRestoreWholeLibrary() = runBlocking<Unit> {
        val repo = testContainer.libraryRepository
        val before = repo.getAllEntries()
        assumeTrue("library is empty", before.size > 5)
        val sourcesBefore = testContainer.database.sourceDao().getAll()
        val zip = File(context.cacheDir, "big-backup.zip")

        var t = System.currentTimeMillis()
        testContainer.backupManager.exportZip(Uri.fromFile(zip))
        val exportMs = System.currentTimeMillis() - t

        repo.deleteEntries(before.map { it.id })
        assertTrue(repo.getAllEntries().isEmpty())

        t = System.currentTimeMillis()
        val restored = testContainer.backupManager.restore(Uri.fromFile(zip))
        val restoreMs = System.currentTimeMillis() - t

        val sourcesAfter = testContainer.database.sourceDao().getAll()
        val missing = sourcesAfter.filter { it.localFilePath != null && !File(it.localFilePath!!).exists() }
        out.writeText(
            "entries=${before.size} restored=$restored zip=${zip.length() / 1024 / 1024}MB export=${exportMs}ms restore=${restoreMs}ms " +
                "sources=${sourcesBefore.size}->${sourcesAfter.size} missingFiles=${missing.size}\n"
        )
        assertEquals(before.size, restored)
        assertEquals(sourcesBefore.map { it.locatorJson }.toSet(), sourcesAfter.map { it.locatorJson }.toSet())
        assertTrue("missing files: ${missing.map { it.name }}", missing.isEmpty())
        zip.delete()
    }
}

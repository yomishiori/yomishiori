package app.yomishiori.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomishiori.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun exportedSchemaMatchesEntitiesAndDataSurvivesReopen() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO library_entry (id, title, alternativeTitles, contentType, readingStatus, tags, searchKey, dateAdded) " +
                    "VALUES (1, 'Old', '[]', 'WEB_NOVEL', 'READING', '[]', 'old', 1)"
            )
            db.execSQL(
                "INSERT INTO reading_source (id, entryId, sourceType, name, sortOrder, lastUrl, trackingEnabled) " +
                    "VALUES (1, 1, 'WEB_URL', 'site', 0, 'https://example.com/ch5', 1)"
            )
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        kotlinx.coroutines.runBlocking {
            assertEquals("https://example.com/ch5", room.sourceDao().get(1)!!.lastUrl)
        }
        room.close()
        context.deleteDatabase(dbName)
    }
}

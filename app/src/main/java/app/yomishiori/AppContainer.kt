package app.yomishiori

import android.content.Context
import app.yomishiori.data.backup.BackupManager
import app.yomishiori.data.db.AppDatabase
import app.yomishiori.data.files.AppFiles
import app.yomishiori.data.prefs.AppPreferences
import app.yomishiori.data.repo.LibraryRepository
import app.yomishiori.data.repo.ProgressRepository
import app.yomishiori.epub.EpubSessions
import app.yomishiori.epub.ReadiumServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase = AppDatabase.build(context)
    val files = AppFiles(context)
    val preferences = AppPreferences(context)
    val libraryRepository = LibraryRepository(database, files)
    val progressRepository = ProgressRepository(database)
    val readium = ReadiumServices(context, files)
    val epubSessions = EpubSessions(applicationScope)
    val backupManager = BackupManager(context, database, files)
}

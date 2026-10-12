package app.yomishiori.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yomishiori.data.db.ContentType
import app.yomishiori.data.db.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class LibraryDisplay(val label: String) { GRID("Grid"), COMPACT_GRID("Compact grid"), LIST("List") }
enum class LibrarySort(val label: String) { TITLE("Title"), DATE_ADDED("Date added"), LAST_READ("Last read"), PROGRESS("Progress") }
enum class PageTurnDirection(val label: String) { AUTO("Follow the book"), LEFT_TO_RIGHT("Right = next"), RIGHT_TO_LEFT("Left = next") }

data class LibraryPrefs(
    val display: LibraryDisplay = LibraryDisplay.GRID,
    val gridColumns: Int = 0,
    val sort: LibrarySort = LibrarySort.LAST_READ,
    val sortAscending: Boolean = false,
    val statusFilter: Set<ReadingStatus> = emptySet(),
    val typeFilter: Set<ContentType> = emptySet(),
    val showResumeButton: Boolean = true,
    val showProgress: Boolean = true,
)

data class BrowserPrefs(
    val javaScript: Boolean = true,
    val textZoom: Int = 100,
    val darkenPages: Boolean = false,
    val thirdPartyCookies: Boolean = false,
    val immersiveByDefault: Boolean = false,
)

data class ReaderPrefs(
    val pageTurn: PageTurnDirection = PageTurnDirection.AUTO,
    val swipeToTurn: Boolean = false,
    val tapToZoom: Boolean = true,
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val library: LibraryPrefs = LibraryPrefs(),
    val browser: BrowserPrefs = BrowserPrefs(),
    val epubPreferencesJson: String? = null,
    val reader: ReaderPrefs = ReaderPrefs(),
    val tutorialDone: Boolean = false,
    val readerHintsShown: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> prefs.write(block(prefs.toSettings())) }
    }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[THEME], ThemeMode.SYSTEM),
        dynamicColor = this[DYNAMIC_COLOR] ?: true,
        library = LibraryPrefs(
            display = enumOr(this[LIB_DISPLAY], LibraryDisplay.GRID),
            gridColumns = this[LIB_COLUMNS] ?: 0,
            sort = enumOr(this[LIB_SORT], LibrarySort.LAST_READ),
            sortAscending = this[LIB_SORT_ASC] ?: false,
            statusFilter = this[LIB_STATUS_FILTER].orEmpty().mapNotNull { enumOrNull<ReadingStatus>(it) }.toSet(),
            typeFilter = this[LIB_TYPE_FILTER].orEmpty().mapNotNull { enumOrNull<ContentType>(it) }.toSet(),
            showResumeButton = this[LIB_RESUME] ?: true,
            showProgress = this[LIB_PROGRESS] ?: true,
        ),
        browser = BrowserPrefs(
            javaScript = this[WEB_JS] ?: true,
            textZoom = this[WEB_ZOOM] ?: 100,
            darkenPages = this[WEB_DARKEN] ?: false,
            thirdPartyCookies = this[WEB_3P_COOKIES] ?: false,
            immersiveByDefault = this[WEB_IMMERSIVE] ?: false,
        ),
        epubPreferencesJson = this[EPUB_PREFS],
        reader = ReaderPrefs(
            pageTurn = enumOr(this[READER_PAGE_TURN], PageTurnDirection.AUTO),
            swipeToTurn = this[READER_SWIPE] ?: false,
            tapToZoom = this[READER_ZOOM] ?: true,
        ),
        tutorialDone = this[TUTORIAL_DONE] ?: false,
        readerHintsShown = this[READER_HINTS] ?: false,
    )

    private fun MutablePreferences.write(s: AppSettings) {
        this[THEME] = s.theme.name
        this[DYNAMIC_COLOR] = s.dynamicColor
        this[LIB_DISPLAY] = s.library.display.name
        this[LIB_COLUMNS] = s.library.gridColumns
        this[LIB_SORT] = s.library.sort.name
        this[LIB_SORT_ASC] = s.library.sortAscending
        this[LIB_STATUS_FILTER] = s.library.statusFilter.map { it.name }.toSet()
        this[LIB_TYPE_FILTER] = s.library.typeFilter.map { it.name }.toSet()
        this[LIB_RESUME] = s.library.showResumeButton
        this[LIB_PROGRESS] = s.library.showProgress
        this[WEB_JS] = s.browser.javaScript
        this[WEB_ZOOM] = s.browser.textZoom
        this[WEB_DARKEN] = s.browser.darkenPages
        this[WEB_3P_COOKIES] = s.browser.thirdPartyCookies
        this[WEB_IMMERSIVE] = s.browser.immersiveByDefault
        if (s.epubPreferencesJson != null) this[EPUB_PREFS] = s.epubPreferencesJson else remove(EPUB_PREFS)
        this[READER_PAGE_TURN] = s.reader.pageTurn.name
        this[READER_SWIPE] = s.reader.swipeToTurn
        this[READER_ZOOM] = s.reader.tapToZoom
        this[TUTORIAL_DONE] = s.tutorialDone
        this[READER_HINTS] = s.readerHintsShown
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LIB_DISPLAY = stringPreferencesKey("library_display")
        val LIB_COLUMNS = intPreferencesKey("library_columns")
        val LIB_SORT = stringPreferencesKey("library_sort")
        val LIB_SORT_ASC = booleanPreferencesKey("library_sort_asc")
        val LIB_STATUS_FILTER = stringSetPreferencesKey("library_status_filter")
        val LIB_TYPE_FILTER = stringSetPreferencesKey("library_type_filter")
        val LIB_RESUME = booleanPreferencesKey("library_resume_button")
        val LIB_PROGRESS = booleanPreferencesKey("library_show_progress")
        val WEB_JS = booleanPreferencesKey("web_js")
        val WEB_ZOOM = intPreferencesKey("web_text_zoom")
        val WEB_DARKEN = booleanPreferencesKey("web_darken")
        val WEB_3P_COOKIES = booleanPreferencesKey("web_third_party_cookies")
        val WEB_IMMERSIVE = booleanPreferencesKey("web_immersive")
        val EPUB_PREFS = stringPreferencesKey("epub_preferences")
        val READER_PAGE_TURN = stringPreferencesKey("reader_page_turn")
        val READER_SWIPE = booleanPreferencesKey("reader_swipe_to_turn")
        val READER_ZOOM = booleanPreferencesKey("reader_tap_to_zoom")
        val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val READER_HINTS = booleanPreferencesKey("reader_hints_shown")
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T = enumOrNull<T>(name) ?: default

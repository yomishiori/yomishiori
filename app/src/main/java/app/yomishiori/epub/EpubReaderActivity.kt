package app.yomishiori.epub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.yomishiori.R
import app.yomishiori.container
import app.yomishiori.data.prefs.ReaderPrefs
import app.yomishiori.openReader
import app.yomishiori.ui.theme.YomishioriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.use

fun epubPreferencesOf(json: String?): EpubPreferences =
    json?.let { runCatching { EpubPreferencesSerializer().deserialize(it) }.getOrNull() } ?: EpubPreferences()

class EpubReaderActivity : AppCompatActivity() {
    private var session: EpubSession? = null
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var swipeLayout: SwipeBlockingLayout

    private var chromeVisible by mutableStateOf(false)
    private var locator by mutableStateOf<Locator?>(null)
    private var chapter by mutableStateOf<String?>(null)
    private var chapters: ChapterTitles? = null
    private var preferences by mutableStateOf(EpubPreferences())
    private var readerPrefs by mutableStateOf(ReaderPrefs())
    private var bookIsRtl by mutableStateOf(false)
    private var showHints by mutableStateOf(false)
    private var zoomed by mutableStateOf<Pair<Bitmap, String?>?>(null)

    private val tapHandler = Handler(Looper.getMainLooper())
    private var pendingTap: Runnable? = null
    private var lastTapLeft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1)
        val s = container.epubSessions.get(sourceId)
        if (s == null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            val app = applicationContext
            container.applicationScope.launch {
                try {
                    container.progressRepository.getSource(sourceId)?.let { openReader(app, it) }
                } catch (e: Exception) {
                    Toast.makeText(app, e.message ?: "Cannot open the book.", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        session = s
        chapters = ChapterTitles(s.publication)
        preferences = s.preferences
        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(s.publication).createFragmentFactory(
            initialLocator = s.locator,
            initialPreferences = s.preferences,
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) = openExternal(url.toString())
            },
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val root = FrameLayout(this)
        swipeLayout = SwipeBlockingLayout(this).apply {
            addView(FragmentContainerView(context).apply { id = R.id.reader_container }, MATCH_PARENT, MATCH_PARENT)
        }
        root.addView(swipeLayout, MATCH_PARENT, MATCH_PARENT)
        root.addView(
            ComposeView(this).apply {
                setContent {
                    YomishioriTheme {
                        EpubReaderChrome(
                            visible = chromeVisible,
                            title = s.publication.metadata.title.orEmpty(),
                            locator = locator,
                            chapter = chapter,
                            toc = s.publication.tableOfContents,
                            preferences = preferences,
                            reader = readerPrefs,
                            showHints = showHints,
                            nextPageIsLeft = nextPageIsLeft(readerPrefs.pageTurn, bookIsRtl),
                            onBack = ::finish,
                            onTocClick = ::goTo,
                            onSeek = ::seek,
                            onPreferencesChange = ::applyPreferences,
                            onReaderChange = ::applyReaderPrefs,
                            onHintsDismiss = ::dismissHints,
                        )
                        zoomed?.let { (bitmap, description) -> ImageViewer(bitmap, description) { zoomed = null } }
                    }
                }
            },
            MATCH_PARENT, MATCH_PARENT,
        )
        setContentView(root)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.reader_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        navigator.addInputListener(DirectionalNavigationAdapter(navigator, tapEdges = emptySet()))
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean = handleTap(event)
        })
        showChrome(false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect {
                    locator = it
                    s.locator = it
                    chapter = chapters?.titleFor(it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.debounce(1_000).collect(::save)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.overflow.collect { bookIsRtl = it.readingProgression == ReadingProgression.RTL }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.preferences.settings.collect {
                    readerPrefs = it.reader
                    swipeLayout.blockHorizontalSwipes = !it.reader.swipeToTurn
                    showHints = !it.readerHintsShown
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::navigator.isInitialized) save(navigator.currentLocator.value)
    }

    override fun onDestroy() {
        tapHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        if (isFinishing) session?.let { container.epubSessions.close(it.sourceId) }
    }

    private fun handleTap(event: TapEvent): Boolean {
        val width = navigator.view?.width?.takeIf { it > 0 } ?: window.decorView.width
        val left = event.point.x < width / 2f
        val pending = pendingTap
        if (pending != null) {
            tapHandler.removeCallbacks(pending)
            pendingTap = null
            if (left == lastTapLeft) {
                turnPage(left)
                return true
            }
        }
        lastTapLeft = left
        val image = (event.targetElement?.content as? Content.ImageElement)?.takeIf { readerPrefs.tapToZoom }
        val single = Runnable {
            pendingTap = null
            if (image != null) openImage(image) else showChrome(!chromeVisible)
        }
        pendingTap = single
        tapHandler.postDelayed(single, DOUBLE_TAP_MS)
        return true
    }

    private fun turnPage(tapLeft: Boolean) {
        if (isForward(tapLeft, readerPrefs.pageTurn, bookIsRtl)) navigator.goForward() else navigator.goBackward()
    }

    private fun openImage(image: Content.ImageElement) {
        val publication = session?.publication ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                publication.get(image.embeddedLink)?.use { it.read().getOrNull() }?.let { decodeForViewer(it) }
            }
            if (bitmap == null) {
                Toast.makeText(this@EpubReaderActivity, "This picture can't be zoomed.", Toast.LENGTH_SHORT).show()
            } else {
                zoomed = bitmap to image.caption
            }
        }
    }

    private fun save(locator: Locator) {
        val s = session ?: return
        val repo = container.progressRepository
        container.applicationScope.launch {
            repo.saveEpubProgress(
                sourceId = s.sourceId,
                locatorJson = locator.toJSON().toString(),
                progression = locator.locations.totalProgression,
                chapterLabel = chapters?.titleFor(locator),
            )
        }
    }

    private fun goTo(link: Link) {
        navigator.go(link)
        showChrome(false)
    }

    private fun seek(progression: Double) {
        val publication = session?.publication ?: return
        lifecycleScope.launch { publication.locateProgression(progression)?.let { navigator.go(it) } }
    }

    private fun applyPreferences(newPreferences: EpubPreferences) {
        preferences = newPreferences
        session?.preferences = newPreferences
        navigator.submitPreferences(newPreferences)
        val json = EpubPreferencesSerializer().serialize(newPreferences)
        container.applicationScope.launch { container.preferences.update { it.copy(epubPreferencesJson = json) } }
    }

    private fun applyReaderPrefs(newPrefs: ReaderPrefs) {
        readerPrefs = newPrefs
        container.applicationScope.launch { container.preferences.update { it.copy(reader = newPrefs) } }
    }

    private fun dismissHints() {
        showHints = false
        container.applicationScope.launch { container.preferences.update { it.copy(readerHintsShown = true) } }
    }

    private fun showChrome(visible: Boolean) {
        chromeVisible = visible
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openExternal(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
        }
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val NAVIGATOR_TAG = "navigator"
        private const val DOUBLE_TAP_MS = 300L

        fun intent(context: Context, sourceId: Long) =
            Intent(context, EpubReaderActivity::class.java).putExtra(EXTRA_SOURCE_ID, sourceId)
    }
}

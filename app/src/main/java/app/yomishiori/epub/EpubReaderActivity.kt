package app.yomishiori.epub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
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
import app.yomishiori.ui.theme.YomishioriTheme
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl

fun epubPreferencesOf(json: String?): EpubPreferences =
    json?.let { runCatching { EpubPreferencesSerializer().deserialize(it) }.getOrNull() } ?: EpubPreferences()

class EpubReaderActivity : AppCompatActivity() {
    private var session: EpubSession? = null
    private lateinit var navigator: EpubNavigatorFragment

    private var chromeVisible by mutableStateOf(false)
    private var locator by mutableStateOf<Locator?>(null)
    private var preferences by mutableStateOf(EpubPreferences())

    override fun onCreate(savedInstanceState: Bundle?) {
        val sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1)
        val s = container.epubSessions.get(sourceId)
        if (s == null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        session = s
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
        root.addView(FragmentContainerView(this).apply { id = R.id.reader_container }, MATCH_PARENT, MATCH_PARENT)
        root.addView(
            ComposeView(this).apply {
                setContent {
                    YomishioriTheme {
                        EpubReaderChrome(
                            visible = chromeVisible,
                            title = s.publication.metadata.title.orEmpty(),
                            locator = locator,
                            toc = s.publication.tableOfContents,
                            preferences = preferences,
                            onBack = ::finish,
                            onTocClick = ::goTo,
                            onSeek = ::seek,
                            onPreferencesChange = ::applyPreferences,
                        )
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
        navigator.addInputListener(DirectionalNavigationAdapter(navigator))
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                showChrome(!chromeVisible)
                return true
            }
        })
        showChrome(false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect {
                    locator = it
                    s.locator = it
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.debounce(1_000).collect(::save)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::navigator.isInitialized) save(navigator.currentLocator.value)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) session?.let { container.epubSessions.close(it.sourceId) }
    }

    private fun save(locator: Locator) {
        val s = session ?: return
        val repo = container.progressRepository
        container.applicationScope.launch {
            repo.saveEpubProgress(
                sourceId = s.sourceId,
                locatorJson = locator.toJSON().toString(),
                progression = locator.locations.totalProgression,
                chapterLabel = locator.title,
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

        fun intent(context: Context, sourceId: Long) =
            Intent(context, EpubReaderActivity::class.java).putExtra(EXTRA_SOURCE_ID, sourceId)
    }
}

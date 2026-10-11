package app.yomishiori.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.yomishiori.container
import app.yomishiori.data.prefs.BrowserPrefs
import app.yomishiori.ui.theme.YomishioriTheme
import kotlinx.coroutines.launch

class WebReaderActivity : ComponentActivity() {
    private val sourceId by lazy { intent.getLongExtra(EXTRA_SOURCE_ID, -1) }

    private val viewModel: WebReaderViewModel by viewModels {
        viewModelFactory {
            initializer { WebReaderViewModel(sourceId, container.progressRepository, container.applicationScope) }
        }
    }

    private lateinit var webView: WebView
    private var browserPrefs = BrowserPrefs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browserPrefs = kotlinx.coroutines.runBlocking { container.preferences.current().browser }
        webView = createWebView(browserPrefs)
        if (browserPrefs.immersiveByDefault) setSystemBarsHidden(true)

        setContent {
            YomishioriTheme {
                BackHandler { if (webView.canGoBack()) webView.goBack() else finish() }
                WebReaderScreen(
                    webView = webView,
                    viewModel = viewModel,
                    immersiveByDefault = browserPrefs.immersiveByDefault,
                    onClose = ::finish,
                    onOpenExternal = { openExternal(it.toUri()) },
                    onFullscreenChange = ::setSystemBarsHidden,
                )
            }
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch { viewModel.startUrl()?.let(webView::loadUrl) }
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        viewModel.flush()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(prefs: BrowserPrefs): WebView {
        val context = if (prefs.darkenPages) ContextThemeWrapper(this, android.R.style.Theme_Material_NoActionBar) else this
        return WebView(context).apply webView@{
            settings.apply {
                javaScriptEnabled = prefs.javaScript
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                textZoom = prefs.textZoom
                builtInZoomControls = true
                displayZoomControls = false
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, prefs.darkenPages)
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, prefs.thirdPartyCookies)
            }
            webViewClient = ReaderClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = viewModel.onProgress(newProgress)
                override fun onReceivedTitle(view: WebView, title: String?) = viewModel.onTitle(title.orEmpty())
            }
            setDownloadListener { url, _, _, _, _ -> openExternal(url.toUri()) }
        }
    }

    private inner class ReaderClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> false
                "intent", "market", "mailto", "tel", "sms" -> {
                    if (request.hasGesture()) openExternal(uri)
                    true
                }
                else -> true
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            debugLog("started", url)
            viewModel.onPageStarted(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            debugLog("history", url)
            viewModel.onHistoryUpdated(url, view.canGoBack(), view.canGoForward())
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            debugLog("visible", url)
            viewModel.onPageCommitVisible(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            debugLog("finished", url)
            viewModel.onPageFinished(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            debugLog("error main=${request.isForMainFrame}", request.url.toString())
            if (request.isForMainFrame) viewModel.onMainFrameError(error.description?.toString() ?: "Page failed to load", request.url.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            debugLog("http ${response.statusCode} main=${request.isForMainFrame}", request.url.toString())
            if (request.isForMainFrame && response.statusCode >= 400) viewModel.onMainFrameError("HTTP ${response.statusCode}", request.url.toString())
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            viewModel.flush()
            Toast.makeText(this@WebReaderActivity, "The page stopped working. Reopen the novel to continue.", Toast.LENGTH_LONG).show()
            finish()
            return true
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            viewModel.onMainFrameError("Secure connection failed, page blocked", error.url)
        }
    }

    private fun openExternal(uri: Uri) {
        val intent = if (uri.scheme.equals("intent", true)) {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()?.apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            } ?: return
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"

        fun intent(context: Context, sourceId: Long) =
            Intent(context, WebReaderActivity::class.java).putExtra(EXTRA_SOURCE_ID, sourceId)
    }
}

private fun debugLog(event: String, url: String) {
    if (app.yomishiori.BuildConfig.DEBUG) android.util.Log.d("YomishioriWeb", "$event $url")
}

package dev.listder.curriculum

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val prefsName = "WebViewStorage"
    private val keySessionData = "session_storage_data"

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullScreen()
        swipeRefreshLayout = SwipeRefreshLayout(this)
        webView = WebView(this)
        swipeRefreshLayout.addView(webView)
        setContentView(swipeRefreshLayout)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.addJavascriptInterface(SessionBridge(), "AndroidBridge")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
            val sessionData = prefs.getString(keySessionData, "{}")
            val script = """
                (function() {
                    var data = $sessionData;
                    for (var key in data) {
                        if (sessionStorage.getItem(key) === null) {
                            sessionStorage.setItem(key, data[key]);
                        }
                    }
                    var oldSet = sessionStorage.setItem;
                    sessionStorage.setItem = function(k, v) {
                        oldSet.apply(this, arguments);
                        var current = {};
                        for (var i = 0; i < sessionStorage.length; i++) {
                            var key = sessionStorage.key(i);
                            current[key] = sessionStorage.getItem(key);
                        }
                        AndroidBridge.saveSession(JSON.stringify(current));
                    };
                })();
            """.trimIndent()
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        WebView.setWebContentsDebuggingEnabled(true)
        webView.loadUrl("https://jwm.mzwu.edu.cn/#/new/Home")
    }

    private fun setupFullScreen() {
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    inner class SessionBridge {
        @Keep
        @JavascriptInterface
        fun saveSession(data: String) {
            val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
            prefs.edit {
                putString(keySessionData, data)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}

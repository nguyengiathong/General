package com.adblock.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var blockedText: TextView

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val io = Executors.newSingleThreadExecutor()

    private var adBlockEnabled: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(v) = prefs.edit().putBoolean("adblock", v).apply()

    private var whitelist: Set<String>
        get() = prefs.getStringSet("whitelist", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("whitelist", v).apply()

    /** Host của trang chính đang mở (dùng cho whitelist). Được đọc từ thread của WebView. */
    @Volatile
    private var currentPageHost: String? = null
    private var pageBlocked = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        urlBar = findViewById(R.id.url_bar)
        progress = findViewById(R.id.progress)
        blockedText = findViewById(R.id.blocked_count)

        io.execute {
            AdBlocker.load(applicationContext)
            runOnUiThread { updateBlockedLabel() }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // Chặn popup / tab mới tự mở
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = AdBlockWebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadInput(urlBar.text.toString())
                true
            } else false
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<ImageButton>(R.id.btn_forward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btn_home).setOnClickListener { webView.loadUrl(HOME_URL) }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(intent?.data?.toString() ?: HOME_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { webView.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun loadInput(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            text.contains('.') && !text.contains(' ') -> "https://$text"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(text)
        }
        webView.loadUrl(url)
        webView.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun isWhitelisted(host: String?): Boolean {
        if (host == null) return false
        return whitelist.any { host == it || host.endsWith(".$it") }
    }

    private fun updateBlockedLabel() {
        val status = when {
            !adBlockEnabled -> getString(R.string.status_off)
            isWhitelisted(currentPageHost) -> getString(R.string.status_whitelisted)
            else -> getString(R.string.status_on, pageBlocked, AdBlocker.blockedCount.get())
        }
        blockedText.text = status
    }

    private inner class AdBlockWebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!adBlockEnabled || isWhitelisted(currentPageHost)) return null
            if (request.isForMainFrame) return null
            if (AdBlocker.isAd(request.url)) {
                AdBlocker.blockedCount.incrementAndGet()
                runOnUiThread {
                    pageBlocked++
                    updateBlockedLabel()
                }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme ?: return false
            if (scheme == "http" || scheme == "https") {
                // Chặn điều hướng tới trang quảng cáo (redirect / click-jacking)
                if (adBlockEnabled && !isWhitelisted(currentPageHost) && AdBlocker.isAd(uri)) {
                    Toast.makeText(this@MainActivity, R.string.redirect_blocked, Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }
            // intent://, market://, tel: ... mở bằng ứng dụng khác
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (e: Exception) {
                true
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            currentPageHost = Uri.parse(url).host?.lowercase()
            pageBlocked = 0
            urlBar.setText(url)
            updateBlockedLabel()
        }

        override fun onPageFinished(view: WebView, url: String) {
            urlBar.setText(url)
            if (adBlockEnabled && !isWhitelisted(currentPageHost)) {
                view.evaluateJavascript(AdBlocker.HIDE_ADS_JS, null)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_toggle).isChecked = adBlockEnabled
        menu.findItem(R.id.action_whitelist).isChecked = isWhitelisted(currentPageHost)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_toggle -> {
                adBlockEnabled = !adBlockEnabled
                webView.reload()
            }
            R.id.action_whitelist -> {
                val host = currentPageHost?.removePrefix("www.") ?: return true
                whitelist = if (isWhitelisted(host)) {
                    whitelist.filterNot { host == it || host.endsWith(".$it") || it.endsWith(".$host") }.toSet()
                } else {
                    whitelist + host
                }
                webView.reload()
            }
            R.id.action_update -> updateList()
            R.id.action_clear -> {
                webView.clearCache(true)
                webView.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        updateBlockedLabel()
        return true
    }

    private fun updateList() {
        Toast.makeText(this, R.string.updating, Toast.LENGTH_SHORT).show()
        io.execute {
            val msg = try {
                val n = AdBlocker.updateFromInternet(applicationContext)
                getString(R.string.update_ok, n)
            } catch (e: Exception) {
                getString(R.string.update_fail, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        io.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://duckduckgo.com"
    }
}

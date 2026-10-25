package com.example.freebrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

const val DEFAULT_SEARCH = "https://searxng.site"
const val INTERNAL = "https://mysearch.local/"

class BrowserTab(ctx: Context, private val prefs: Prefs, private val ext: ExtensionManager) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var progress by mutableStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    val web = WebView(ctx)

    init { configure() }

    private val api = OkHttpClient()
    private var label = ""

    private fun asUrl(t: String): String? = when {
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*").matches(t) || t.startsWith("about:") -> t
        !t.contains(' ') && (t.contains('.') || t.startsWith("localhost")) -> "https://$t"
        else -> null
    }

    fun showStart(note: String = "") {
        label = ""; title = prefs.appName
        web.loadDataWithBaseURL(INTERNAL, Pages.start(prefs.appName, note), "text/html", "utf-8", null)
    }

    /** Address-bar input: empty/home -> start page, URL -> open it, anything else -> search your server. */
    fun load(input: String) {
        val t = input.trim()
        if (t.isEmpty() || t == "mysearch:home") { showStart(); return }
        val u = asUrl(t)
        if (u != null) web.loadUrl(u, if (prefs.bypass) emptyMap() else prefs.customHeaders())
        else search(t, 1)
    }

    /** Routes a text query to a public SearXNG instance (Settings can override the base URL). */
    fun search(q: String, page: Int) {
        val base = prefs.searchServer.trim().trimEnd('/').ifEmpty { DEFAULT_SEARCH }
        val encoded = URLEncoder.encode(q, "UTF-8")
        web.loadUrl("$base/search?q=$encoded" + if (page > 1) "&pageno=$page" else "")
    }

    inner class Bridge {
        @JavascriptInterface fun search(q: String, page: Int) { web.post { if (page <= 1) load(q) else search(q, page) } }
    }

    private fun sync() { canBack = web.canGoBack(); canForward = web.canGoForward() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { progress = p }
            override fun onReceivedTitle(v: WebView, t: String?) { title = t ?: "" }
        }
        web.addJavascriptInterface(Bridge(), "App")
        web.webViewClient = Client()
    }

    private inner class Client : WebViewClient() {
        private val http = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
        private val stripped = setOf(
            "x-frame-options", "content-security-policy", "content-security-policy-report-only",
            "cross-origin-opener-policy", "cross-origin-embedder-policy", "cross-origin-resource-policy",
            "set-cookie", "content-encoding", "content-length", "transfer-encoding"
        )

        override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
            url = if (u.startsWith(INTERNAL)) label else u; sync()
            ext.scriptsFor(u, "document_start").forEach { v.evaluateJavascript(it, null) }
        }

        override fun onPageFinished(v: WebView, u: String) {
            url = if (u.startsWith(INTERNAL)) label else u; sync()
            ext.scriptsFor(u, "document_end").forEach { v.evaluateJavascript(it, null) }
        }

        override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) { url = if (u.startsWith(INTERNAL)) label else u; sync() }

        /**
         * Re-fetches GET requests with OkHttp so response headers can be rewritten before
         * the WebView sees them. POST bodies are not visible here, so those load unmodified.
         */
        override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest): WebResourceResponse? {
            if (!prefs.bypass || req.method != "GET") return null
            val scheme = req.url.scheme
            if (scheme != "http" && scheme != "https") return null
            return try {
                val u = req.url.toString()
                val rb = Request.Builder().url(u)
                req.requestHeaders.forEach { (k, val_) ->
                    // Let OkHttp negotiate gzip so the body we return is already decoded.
                    if (!k.equals("Accept-Encoding", true)) rb.header(k, val_)
                }
                CookieManager.getInstance().getCookie(u)?.let { rb.header("Cookie", it) }
                prefs.customHeaders().forEach { (k, val_) -> rb.header(k, val_) }
                val resp = http.newCall(rb.build()).execute()
                resp.headers("Set-Cookie").forEach { CookieManager.getInstance().setCookie(u, it) }

                val h = linkedMapOf<String, String>()
                for (name in resp.headers.names()) {
                    if (name.lowercase() !in stripped) h[name] = resp.headers.values(name).joinToString(", ")
                }
                val origin = req.requestHeaders["Origin"]
                h["Access-Control-Allow-Origin"] = origin ?: "*"
                if (origin != null) h["Access-Control-Allow-Credentials"] = "true"
                h["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
                h["Access-Control-Allow-Headers"] = "*"

                val ct = resp.body?.contentType()
                val mime = ct?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream"
                WebResourceResponse(
                    mime, ct?.charset()?.name(), resp.code,
                    resp.message.ifEmpty { "OK" }, h, resp.body?.byteStream()
                )
            } catch (e: Exception) {
                null // fall back to the WebView's normal network stack
            }
        }
    }
}

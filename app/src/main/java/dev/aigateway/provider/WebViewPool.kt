package dev.aigateway.provider

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.aigateway.core.Log
import dev.aigateway.core.ProviderDef
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Manages a pool of hidden WebViews, one per provider.
 * Each WebView loads the provider's login page, captures its cookies,
 * and serves as the execution context for the injected fetch() hook.
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
object WebViewPool {

    private val handler = Handler(Looper.getMainLooper())
    private val webViews = ConcurrentHashMap<String, WebView>()
    private val readyFlags = ConcurrentHashMap<String, Boolean>()
    private val pendingChunks = ConcurrentHashMap<String, ArrayBlockingQueue<String>>()
    private var windowManager: WindowManager? = null

    /** Called from JS: window.aigatewayHost.onChunk(requestId, data) */
    class JsBridge {
        @JavascriptInterface
        fun onChunk(requestId: String, data: String) {
            pendingChunks[requestId]?.offer(data)
        }
    }

    fun start(context: Context) {
        for (def in Providers.ALL) {
            if (webViews.containsKey(def.id)) continue
            createWebView(context.applicationContext, def)
        }
    }

    fun stop() {
        handler.post {
            for ((id, wv) in webViews) {
                try {
                    (wv.parent as? WindowManager)?.removeView(wv)
                } catch (_: Exception) {}
                wv.destroy()
                Log.i("WebView $id destroyed")
            }
            webViews.clear()
            readyFlags.clear()
            pendingChunks.clear()
        }
    }

    fun isReady(providerId: String): Boolean = readyFlags[providerId] == true

    fun isLoggedIn(def: ProviderDef): Boolean {
        val cookie = getCookie(def) ?: return false
        for (name in def.sessionCookieNames) {
            if (cookie.contains("$name=")) {
                val idx = cookie.indexOf("$name=")
                val end = cookie.indexOf(';', idx)
                val value = if (end > 0) cookie.substring(idx + name.length + 1, end)
                            else cookie.substring(idx + name.length + 1)
                if (value.isNotEmpty()) return true
            }
        }
        return false
    }

    fun getCookie(def: ProviderDef): String? {
        return CookieManager.getInstance().getCookie(def.loginUrl)
    }

    /**
     * Sends a request through the provider's WebView.
     * Returns chunks via the callback; blocks until '__DONE__' or timeout.
     */
    fun sendRequest(def: ProviderDef, payloadJson: String, timeoutMs: Long = 300_000L): List<String> {
        val wv = webViews[def.id] ?: return listOf("__ERROR__:WebView not started for ${def.id}")
        val requestId = "req_${System.nanoTime()}"
        val queue = ArrayBlockingQueue<String>(8192)
        pendingChunks[requestId] = queue

        // Inject hook if needed
        if (readyFlags[def.id] != true) {
            pendingChunks.remove(requestId)
            return listOf("__ERROR__:WebView not ready for ${def.id}")
        }

        // Call JS
        val js = "window.__aigwSend && __aigwSend(${org.json.JSONObject.quote(def.apiUrl)}, ${org.json.JSONObject.quote(payloadJson)}, ${org.json.JSONObject.quote(requestId)})"
        handler.post {
            try {
                wv.evaluateJavascript(js, null)
            } catch (e: Exception) {
                queue.offer("__ERROR__:${e.message}")
            }
        }

        val chunks = mutableListOf<String>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val chunk = queue.poll(5, TimeUnit.SECONDS) ?: continue
            when {
                chunk == "__DONE__" -> { pendingChunks.remove(requestId); return chunks }
                chunk.startsWith("__ERROR__:") -> { pendingChunks.remove(requestId); return listOf(chunk) }
                chunk.startsWith("__HTTP_ERROR__:") -> { pendingChunks.remove(requestId); return listOf(chunk) }
                else -> chunks.add(chunk)
            }
        }
        pendingChunks.remove(requestId)
        return listOf("__ERROR__:timeout waiting for ${def.id}")
    }

    fun cancelRequest(requestId: String) {
        pendingChunks.remove(requestId)?.offer("__DONE__")
    }

    private fun createWebView(context: Context, def: ProviderDef) {
        handler.post {
            try {
                val wv = WebView(context)
                val settings = wv.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = settings.userAgentString

                CookieManager.getInstance().setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
                }

                wv.addJavascriptInterface(JsBridge(), "aigatewayHost")

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // Inject the provider's hook script
                        view.evaluateJavascript(def.hookScript) { _ ->
                            readyFlags[def.id] = true
                            Log.i("${def.label} WebView ready (loggedIn=${isLoggedIn(def)})")
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return false
                    }
                }

                wv.webChromeClient = WebChromeClient()
                wv.loadUrl(def.loginUrl)

                // Attach as invisible overlay
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm
                val params = WindowManager.LayoutParams(
                    1, 1,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.alpha = 0.01f
                params.x = 0
                params.y = 0
                wm.addView(wv, params)
                webViews[def.id] = wv
                Log.i("${def.label} WebView created: ${def.loginUrl}")
            } catch (e: Exception) {
                Log.e("Failed to create WebView for ${def.id}: ${e.message}")
            }
        }
    }

    fun reload(def: ProviderDef) {
        handler.post {
            webViews[def.id]?.loadUrl(def.loginUrl)
        }
    }
}

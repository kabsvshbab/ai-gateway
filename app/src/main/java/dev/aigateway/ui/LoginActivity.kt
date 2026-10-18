package dev.aigateway.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import dev.aigateway.core.Log

class LoginActivity : AppCompatActivity() {

    companion object {
        var currentProviderId: String = ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerId = intent.getStringExtra("provider") ?: currentProviderId
        val def = dev.aigateway.provider.Providers.byId[providerId]

        if (def == null) {
            finish()
            return
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.i("Login page finished: $url")
                }
            }
            loadUrl(def.loginUrl)
        }

        setContentView(webView)
    }
}

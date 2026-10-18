package dev.aigateway.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.aigateway.core.Log
import dev.aigateway.provider.Providers
import dev.aigateway.provider.WebViewPool
import dev.aigateway.service.GatewayService

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "AI Gateway"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        statusText = TextView(this).apply {
            text = "Status: Starting..."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        logView = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }

        val logScroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(logScroll)

        setContentView(root)

        startService(Intent(this, GatewayService::class.java))

        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        setupProviders()

        Log.listener = {
            runOnUiThread {
                val sb = StringBuilder()
                for (e in Log.entries.takeLast(50)) {
                    sb.append("[" + e.time + "] " + e.level + ": " + e.msg + "
")
                }
                logView.text = sb.toString()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val sb = StringBuilder()
        sb.append("HTTP: " + dev.aigateway.core.Settings.bindAddress + ":" + dev.aigateway.core.Settings.port + "
")
        sb.append("Providers: " + Providers.ALL.size + "

")
        for (def in Providers.ALL) {
            val loggedIn = WebViewPool.isLoggedIn(def)
            val status = if (loggedIn) "Logged In" else "Not Logged In"
            sb.append(def.label + ": " + status + "
")
        }
        statusText.text = sb.toString()
    }

    private fun setupProviders() {
        for (def in Providers.ALL) {
            Log.i("Provider " + def.label + ": " + def.loginUrl)
        }
    }
}

package dev.aigateway.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.aigateway.core.Log
import dev.aigateway.core.Settings as AppSettings
import dev.aigateway.provider.Providers
import dev.aigateway.provider.WebViewPool
import dev.aigateway.service.GatewayService

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var logView: TextView
    private lateinit var providerButtons: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "AI Gateway"
            textSize = 24f
            setPadding(0, 0, 0, 16)
        }

        statusText = TextView(this).apply {
            text = "Status: Starting..."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        providerButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val logLabel = TextView(this).apply {
            text = "Logs:"
            textSize = 12f
            setPadding(0, 16, 0, 8)
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
        root.addView(providerButtons)
        root.addView(logLabel)
        root.addView(logScroll)

        setContentView(root)

        startService(Intent(this, GatewayService::class.java))

        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        setupProviderButtons()

        Log.listener = {
            runOnUiThread {
                val sb = StringBuilder()
                for (e in Log.entries.takeLast(50)) {
                    sb.append("[").append(e.time).append("] ").append(e.level).append(": ").append(e.msg).appendLine()
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
        sb.append("HTTP: ").append(AppSettings.bindAddress).append(":").append(AppSettings.port).appendLine()
        sb.append("Providers: ").append(Providers.ALL.size).appendLine().appendLine()
        statusText.text = sb.toString()

        // Update button states
        for (i in 0 until providerButtons.childCount) {
            val btn = providerButtons.getChildAt(i) as? Button ?: continue
            val providerId = btn.tag as? String ?: continue
            val def = Providers.byId[providerId] ?: continue
            val loggedIn = WebViewPool.isLoggedIn(def)
            btn.text = def.label + if (loggedIn) " (Logged In)" else " (Login)"
            btn.setTextColor(if (loggedIn) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
        }
    }

    private fun setupProviderButtons() {
        for (def in Providers.ALL) {
            val btn = Button(this).apply {
                tag = def.id
                text = def.label + " (Login)"
                setOnClickListener {
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.putExtra("provider", def.id)
                    startActivity(intent)
                }
            }
            providerButtons.addView(btn)
        }
    }
}

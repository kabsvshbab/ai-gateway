package dev.aigateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.aigateway.core.ChatHandler
import dev.aigateway.core.HttpServer
import dev.aigateway.core.Log
import dev.aigateway.core.Settings
import dev.aigateway.provider.WebViewPool
import dev.aigateway.ui.MainActivity

class GatewayService : Service() {

    private var httpServer: HttpServer? = null
    private val CHANNEL_ID = "gateway"
    private val NOTIFICATION_ID = 1

    companion object {
        var running = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        Log.i("GatewayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "dev.aigateway.action.STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        WebViewPool.start(this)
        startHttpServer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        running = false
        httpServer?.stop()
        WebViewPool.stop()
        Log.i("GatewayService destroyed")
    }

    private fun startHttpServer() {
        if (httpServer != null) return
        val port = Settings.port
        httpServer = HttpServer(port) { method, path, headers, body, out ->
            try {
                ChatHandler.handle(method, path, headers, body, out)
            } catch (e: Exception) {
                Log.e("Handler error: ${e.message}")
            }
        }
        httpServer?.start()
        Log.i("HTTP server started on ${Settings.bindAddress}:$port")
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Gateway", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GatewayService::class.java).apply {
            action = "dev.aigateway.action.STOP"
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Gateway")
            .setContentText("Listening on ${Settings.bindAddress}:${Settings.port}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }
}

package dev.aigateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.aigateway.core.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("Boot completed, starting gateway")
            val svc = Intent(context, GatewayService::class.java)
            context.startForegroundService(svc)
        }
    }
}

package dev.aigateway.core

import android.content.Context
import android.content.SharedPreferences

object Settings {
    private val sp: SharedPreferences
        get() = App.instance.getSharedPreferences("aigateway", Context.MODE_PRIVATE)

    var port: Int
        get() = sp.getInt("port", 8787)
        set(v) = sp.edit().putInt("port", v).apply()

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v).apply()

    var bindAddress: String
        get() = sp.getString("bindAddress", "127.0.0.1") ?: "127.0.0.1"
        set(v) = sp.edit().putString("bindAddress", v).apply()
}

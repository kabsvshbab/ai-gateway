package dev.aigateway.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object Log {
    data class Entry(val time: String, val level: String, val msg: String)
    val entries = CopyOnWriteArrayList<Entry>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    var listener: (() -> Unit)? = null

    @Synchronized
    fun add(level: String, msg: String) {
        entries.add(Entry(fmt.format(Date()), level, msg))
        if (entries.size > 500) entries.removeAt(0)
        listener?.invoke()
    }
    fun i(msg: String) = add("I", msg)
    fun w(msg: String) = add("W", msg)
    fun e(msg: String) = add("E", msg)
}

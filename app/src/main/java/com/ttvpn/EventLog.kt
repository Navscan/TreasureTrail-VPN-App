package com.ttvpn

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventLog {

    data class Entry(val timestamp: String, val tag: String, val message: String)

    private val lock = Any()
    private val entries = mutableListOf<Entry>()
    private var initialised = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        if (initialised) return
        synchronized(lock) {
            if (initialised) return
            entries.clear()
            val raw = readRaw(context)
            if (raw.isNotBlank()) {
                raw.split("\n").forEach { line ->
                    parseLine(line)?.let { entries.add(it) }
                }
            }
            initialised = true
        }
    }

    fun write(context: Context, tag: String, message: String) {
        if (!ConfigStore.isLogEnabled(context)) return
        synchronized(lock) {
            if (!initialised) {
                val raw = readRaw(context)
                if (raw.isNotBlank()) {
                    raw.split("\n").forEach { line ->
                        parseLine(line)?.let { entries.add(it) }
                    }
                }
                initialised = true
            }
            val line = "${timeFormat.format(Date())}  $tag  $message"
            val entry = parseLine(line) ?: return
            entries.add(entry)
            while (entries.size > Config.LOG_MAX_ENTRIES) {
                entries.removeAt(0)
            }
            persist(context)
        }
    }

    fun readAll(context: Context): List<Entry> {
        if (!initialised) init(context)
        return synchronized(lock) { entries.toList() }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            entries.clear()
            try {
                context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(Config.PREF_EVENT_LOG).commit()
            } catch (_: Exception) {
            }
        }
    }

    fun maskVoucher(raw: String): String {
        val clean = raw.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        return if (clean.length == 16 && clean.startsWith("TVPN")) {
            "TVPN-****-****-${clean.substring(12)}"
        } else {
            "(voucher)"
        }
    }

    private fun readRaw(context: Context): String {
        return try {
            context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Config.PREF_EVENT_LOG, "").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun persist(context: Context) {
        try {
            val text = entries.joinToString("\n") {
                "${it.timestamp}  ${it.tag}  ${it.message}"
            }
            context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(Config.PREF_EVENT_LOG, text).commit()
        } catch (_: Exception) {
        }
    }

    private fun parseLine(line: String): Entry? {
        val first = line.indexOf("  ")
        if (first < 0) return null
        val second = line.indexOf("  ", first + 2)
        if (second < 0) return null
        return Entry(
            timestamp = line.substring(0, first),
            tag = line.substring(first + 2, second),
            message = line.substring(second + 2)
        )
    }
}

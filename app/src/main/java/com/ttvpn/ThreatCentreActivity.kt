package com.ttvpn

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class ThreatCentreActivity : AppCompatActivity() {

    private lateinit var logsContainer: LinearLayout
    private lateinit var logsScroll: View
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_threat_centre)

        logsContainer = findViewById(R.id.logsContainer)
        logsScroll = findViewById(R.id.logsScroll)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnRefresh).setOnClickListener { fetchLogs() }

        fetchLogs()
    }

    private fun fetchLogs() {
        logsContainer.removeAllViews()
        val loading = TextView(this).apply {
            text = "> CONNECTING TO SECURE FEED..."
            setTextColor(Color.parseColor("#00FFCC"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 16, 0, 16)
        }
        logsContainer.addView(loading)

        executor.execute {
            val result = CerberusClient.fetchLogs()
            handler.post {
                logsContainer.removeAllViews()
                when (result) {
                    is CerberusLogsResult.Success -> renderLogs(result.logs)
                    is CerberusLogsResult.Failure -> renderError("Feed error: ${result.reason}")
                    CerberusLogsResult.NetworkError -> renderError("Network unreachable.")
                }
            }
        }
    }

    private fun renderLogs(logs: List<CerberusLog>) {
        if (logs.isEmpty()) {
            renderError("No recent alerts.")
            return
        }

        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val outputFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        outputFormat.timeZone = TimeZone.getDefault()

        for (log in logs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // Format timestamp
            var timeStr = "--:--:--"
            try {
                val date = inputFormat.parse(log.timestamp)
                if (date != null) timeStr = outputFormat.format(date)
            } catch (_: Exception) {}

            // Determine colors based on action
            val (actionColor, actionText) = when {
                log.action.startsWith("BANNED") -> Pair(Color.parseColor("#FF4545"), "BANNED")
                log.action.startsWith("UNBANNED") -> Pair(Color.parseColor("#4ADE80"), "UNBANNED")
                log.action.startsWith("REPORT") -> Pair(Color.parseColor("#D4AF37"), "SYSTEM")
                else -> Pair(Color.parseColor("#AAB"), log.action)
            }

            // Line 1: Time + Action + IP
            val line1 = TextView(this).apply {
                text = "[$timeStr]  $actionText  ${if (log.ip == "SYSTEM") "" else log.ip}"
                setTextColor(actionColor)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 12f
            }

            // Line 2: Geo Info or System Report
            val line2 = TextView(this).apply {
                if (log.ip == "SYSTEM") {
                    text = "> ${log.action}"
                    setTextColor(Color.parseColor("#D4AF37"))
                } else {
                    text = "${log.flag} ${log.country} · ${log.isp} [${log.jail}]"
                    setTextColor(Color.parseColor("#888888"))
                }
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(0, 2, 0, 0)
            }

            row.addView(line1)
            row.addView(line2)
            logsContainer.addView(row)

            // Divider
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#1A1A22"))
                setPadding(0, 4, 0, 4)
            }
            logsContainer.addView(divider)
        }
    }

    private fun renderError(msg: String) {
        val tv = TextView(this).apply {
            text = "> ERROR: $msg"
            setTextColor(Color.parseColor("#FF4545"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 16, 0, 16)
        }
        logsContainer.addView(tv)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}

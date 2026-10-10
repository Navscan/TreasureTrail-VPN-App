package com.ttvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import java.io.ByteArrayInputStream

class VpnService : GoBackend.VpnService() {
    private lateinit var backend: GoBackend
    private var currentTunnel: Tunnel? = null

    private val statsHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val statsRunnable = object : Runnable {
        override fun run() {
            updateStats()
            statsHandler.postDelayed(this, Config.STATS_POLL_INTERVAL_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (ConfigStore.isLockEnabled(this@VpnService) && currentTunnel == null) {
                mainHandler.postDelayed({
                    if (currentTunnel == null && ConfigStore.isLockEnabled(this@VpnService)) {
                        startVpn()
                    }
                }, Config.RECONNECT_DELAY_MS)
            }
        }
    }

    companion object {
        const val ACTION_START = "com.ttvpn.action.START"
        const val ACTION_STOP = "com.ttvpn.action.STOP"

        @Volatile var isRunning = false

        fun start(context: Context) {
            val intent = Intent(context, VpnService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (currentTunnel == null) {
                    startVpn()
                }
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        stopVpn()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun startVpn() {
        val configString = ConfigStore.getConfig(this)
        if (configString.isNullOrEmpty()) {
            stopSelf()
            return
        }

        try {
            val parsed = com.wireguard.config.Config.parse(
                ByteArrayInputStream(configString.toByteArray())
            )
            val tunnel = object : Tunnel {
                override fun getName(): String = Config.TUNNEL_NAME
                override fun onStateChange(state: Tunnel.State) {
                    val up = (state == Tunnel.State.UP)
                    ConfigStore.setVpnRunning(this@VpnService, up)
                    if (up) {
                        ConfigStore.setTunnelStartedAt(this@VpnService, System.currentTimeMillis())
                    }
                }
            }
            currentTunnel = tunnel

            try {
                showNotification()
            } catch (_: Exception) {
            }

            backend.setState(tunnel, Tunnel.State.UP, parsed)
            startStatsPolling()
        } catch (e: Exception) {
            ConfigStore.setVpnRunning(this, false)
            currentTunnel = null
            stopVpn()
        }
    }

    private fun stopVpn() {
        stopStatsPolling()
        try {
            currentTunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
        } catch (_: Exception) {
        }
        ConfigStore.setVpnRunning(this, false)
        currentTunnel = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startStatsPolling() {
        statsHandler.removeCallbacks(statsRunnable)
        statsHandler.post(statsRunnable)
    }

    private fun stopStatsPolling() {
        statsHandler.removeCallbacks(statsRunnable)
        Stats.clear()
    }

    private fun updateStats() {
        val tunnel = currentTunnel ?: return
        try {
            val stats = backend.getStatistics(tunnel)
            Stats.rx = stats.totalRx()
            Stats.tx = stats.totalTx()
        } catch (_: Exception) {
        }
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Config.NOTIFICATION_CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TTVPN™")
            .setContentText("Protected by PayBridge (Pty) Ltd")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Config.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Config.NOTIFICATION_ID, notification)
        }
    }
}

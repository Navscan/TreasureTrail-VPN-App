package com.ttvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class VpnService : VpnService() {
    private lateinit var backend: GoBackend
    private var currentTunnel: Tunnel? = null

    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        try {
            val configString = """
[Interface]
PrivateKey = YEN/1f7rjenidQPCIj5CbudBs2c/xRmw81LWl0CIO24=
Address = 10.0.0.3/24
DNS = 1.1.1.1

[Peer]
PublicKey = 8bL+3Y2M1uaERIWMp/g2+Xk7246cvJX22qKNzSiejHQ=
Endpoint = 154.66.198.207:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
            """.trimIndent()
            val config = Config.parse(ByteArrayInputStream(configString.toByteArray()))
            val tunnel = object : Tunnel {
                override fun getName(): String = "TTVPN"
                override fun onStateChange(state: Tunnel.State) {}
            }
            currentTunnel = tunnel
            backend.setState(tunnel, Tunnel.State.UP, config)
            showNotification("TTVPN™", "Protected by PayBridge (Pty) Ltd")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        currentTunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
        stopForeground(true)
        stopSelf()
    }

    private fun showNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("vpn", "VPN Status", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "vpn")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
    }
}

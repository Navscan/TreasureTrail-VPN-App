package com.ttvpn

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var btnToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnToggle = findViewById(R.id.btnToggle)
        btnToggle.setOnClickListener {
            val intent = Intent(this, VpnService::class.java)
            if (VpnService.isRunning) {
                stopService(intent)
                VpnService.isRunning = false
                btnToggle.text = "Start VPN"
            } else {
                startService(intent)
                VpnService.isRunning = true
                btnToggle.text = "Stop VPN"
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            when (intent.data?.toString()) {
                "treasuretrailvpn://start" -> startVpn()
                "treasuretrailvpn://stop" -> stopVpn()
            }
        }
    }

    private fun startVpn() {
        val intent = Intent(this, VpnService::class.java)
        startService(intent)
        VpnService.isRunning = true
        btnToggle.text = "Stop VPN"
    }

    private fun stopVpn() {
        val intent = Intent(this, VpnService::class.java)
        stopService(intent)
        VpnService.isRunning = false
        btnToggle.text = "Start VPN"
    }
}

package com.ttvpn

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var txtState: TextView
    private lateinit var cardMeta: LinearLayout
    private lateinit var txtEndpoint: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtEntitlement: TextView
    private lateinit var rowEntitlement: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var pendingStart = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            doStartVpnService()
        } else {
            pendingStart = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, Config.STATE_POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        txtState = findViewById(R.id.txtState)
        cardMeta = findViewById(R.id.cardMeta)
        txtEndpoint = findViewById(R.id.txtEndpoint)
        txtDuration = findViewById(R.id.txtDuration)
        txtEntitlement = findViewById(R.id.txtEntitlement)
        rowEntitlement = findViewById(R.id.rowEntitlement)

        btnToggle.setOnClickListener { onToggleTapped() }

        requestNotificationPermissionIfNeeded()
        refreshUi()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            when (intent.data?.toString()) {
                "treasuretrailvpn://start" -> onToggleTapped()
                "treasuretrailvpn://stop" -> stopVpnService()
            }
        }
    }

    private fun onToggleTapped() {
        if (!ConfigStore.hasConfig(this)) {
            showVoucherDialog()
            return
        }
        if (ConfigStore.isVpnRunning(this)) {
            stopVpnService()
        } else {
            pendingStart = true
            requestConsentThenStart()
        }
    }

    private fun requestConsentThenStart() {
        val prepareIntent = android.net.VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            doStartVpnService()
        }
    }

    private fun doStartVpnService() {
        startService(Intent(this, VpnService::class.java))
        pendingStart = false
    }

    private fun stopVpnService() {
        stopService(Intent(this, VpnService::class.java))
    }

    private fun showVoucherDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.voucher, null)
        val editVoucher = view.findViewById<EditText>(R.id.editVoucher)
        val txtError = view.findViewById<TextView>(R.id.txtError)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val code = editVoucher.text.toString().trim()
            if (code.isEmpty()) {
                txtError.text = getString(R.string.error_empty_voucher)
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            btnSubmit.isEnabled = false
            btnCancel.isEnabled = false
            txtError.visibility = View.GONE

            executor.execute {
                val result = ApiClient.register(code)
                handler.post {
                    btnSubmit.isEnabled = true
                    btnCancel.isEnabled = true
                    when (result) {
                        is RegisterResult.Success -> {
                            ConfigStore.saveConfig(
                                this,
                                result.config,
                                result.assignedIp,
                                result.subscriptionExpiresAt
                            )
                            dialog.dismiss()
                            pendingStart = true
                            requestConsentThenStart()
                            refreshUi()
                        }
                        is RegisterResult.Failure -> {
                            txtError.text = reasonToMessage(result.reason)
                            txtError.visibility = View.VISIBLE
                        }
                        RegisterResult.NetworkError -> {
                            txtError.text = getString(R.string.error_network)
                            txtError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun reasonToMessage(reason: String): String = when (reason) {
        "voucher_not_found" -> getString(R.string.error_voucher_not_found)
        "voucher_expired" -> getString(R.string.error_voucher_expired)
        "voucher_used" -> getString(R.string.error_voucher_used)
        "subscription_inactive" -> getString(R.string.error_subscription_inactive)
        "user_not_found" -> getString(R.string.error_user_not_found)
        else -> getString(R.string.error_unknown)
    }

    private fun refreshUi() {
        val hasConfig = ConfigStore.hasConfig(this)
        val running = ConfigStore.isVpnRunning(this)
        val connecting = pendingStart && !running

        when {
            !hasConfig -> {
                txtState.text = getString(R.string.state_not_setup)
                txtState.setTextColor(ContextCompat.getColor(this, R.color.muted_label))
                btnToggle.text = getString(R.string.action_enter_voucher)
                btnToggle.isEnabled = true
                cardMeta.visibility = View.GONE
            }
            connecting -> {
                txtState.text = getString(R.string.state_connecting)
                txtState.setTextColor(ContextCompat.getColor(this, R.color.state_amber))
                btnToggle.text = getString(R.string.action_disconnect)
                btnToggle.isEnabled = true
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
            running -> {
                txtState.text = getString(R.string.state_connected)
                txtState.setTextColor(ContextCompat.getColor(this, R.color.brand_gold))
                btnToggle.text = getString(R.string.action_disconnect)
                btnToggle.isEnabled = true
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = true)
            }
            else -> {
                txtState.text = getString(R.string.state_disconnected)
                txtState.setTextColor(ContextCompat.getColor(this, R.color.muted_label))
                btnToggle.text = getString(R.string.action_connect)
                btnToggle.isEnabled = true
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
        }
    }

    private fun updateMeta(running: Boolean) {
        txtEndpoint.text = Config.BASE_URL

        if (running) {
            val startedAt = ConfigStore.getTunnelStartedAt(this)
            if (startedAt > 0L) {
                val elapsed = System.currentTimeMillis() - startedAt
                txtDuration.text = formatDuration(elapsed)
            } else {
                txtDuration.text = getString(R.string.duration_placeholder)
            }
        } else {
            txtDuration.text = getString(R.string.duration_placeholder)
        }

        val expires = ConfigStore.getSubscriptionExpiresAt(this)
        if (expires.isNullOrEmpty()) {
            rowEntitlement.visibility = View.VISIBLE
            txtEntitlement.text = getString(R.string.entitlement_active)
            txtEntitlement.setTextColor(ContextCompat.getColor(this, R.color.brand_teal))
        } else {
            rowEntitlement.visibility = View.VISIBLE
            txtEntitlement.text = getString(R.string.entitlement_expires, expires)
            txtEntitlement.setTextColor(ContextCompat.getColor(this, R.color.text_main))
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

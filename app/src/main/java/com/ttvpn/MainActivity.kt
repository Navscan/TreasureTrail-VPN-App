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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var txtState: TextView
    private lateinit var txtDot: TextView
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
        txtDot = findViewById(R.id.txtDot)
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
        val seg1 = view.findViewById<EditText>(R.id.editSeg1)
        val seg2 = view.findViewById<EditText>(R.id.editSeg2)
        val seg3 = view.findViewById<EditText>(R.id.editSeg3)
        val txtError = view.findViewById<TextView>(R.id.txtError)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)

        wireSegment(seg1, seg2)
        wireSegment(seg2, seg3)
        wireSegment(seg3, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val part1 = seg1.text.toString().trim().uppercase(Locale.US)
            val part2 = seg2.text.toString().trim().uppercase(Locale.US)
            val part3 = seg3.text.toString().trim().uppercase(Locale.US)

            if (part1.length < 4 || part2.length < 4 || part3.length < 4) {
                txtError.text = getString(R.string.error_empty_voucher)
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val code = "TVPN-$part1-$part2-$part3"

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

    private fun wireSegment(current: EditText, next: EditText?) {
        current.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length == 4 && next != null && !next.hasFocus()) {
                    next.requestFocus()
                }
            }
        })
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
                applyState(
                    text = getString(R.string.state_not_setup),
                    colour = R.color.muted_label,
                    buttonText = getString(R.string.action_enter_voucher),
                    buttonEnabled = true
                )
                cardMeta.visibility = View.GONE
            }
            connecting -> {
                applyState(
                    text = getString(R.string.state_connecting),
                    colour = R.color.state_amber,
                    buttonText = getString(R.string.action_disconnect),
                    buttonEnabled = true
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
            running -> {
                applyState(
                    text = getString(R.string.state_connected),
                    colour = R.color.brand_gold,
                    buttonText = getString(R.string.action_disconnect),
                    buttonEnabled = true
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = true)
            }
            else -> {
                applyState(
                    text = getString(R.string.state_disconnected),
                    colour = R.color.muted_label,
                    buttonText = getString(R.string.action_connect),
                    buttonEnabled = true
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
        }
    }

    private fun applyState(text: String, colour: Int, buttonText: String, buttonEnabled: Boolean) {
        val c = ContextCompat.getColor(this, colour)
        txtState.text = text
        txtState.setTextColor(c)
        txtDot.setTextColor(c)
        btnToggle.text = buttonText
        btnToggle.isEnabled = buttonEnabled
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
        rowEntitlement.visibility = View.VISIBLE
        if (expires.isNullOrEmpty()) {
            txtEntitlement.text = getString(R.string.entitlement_active)
            txtEntitlement.setTextColor(ContextCompat.getColor(this, R.color.brand_teal))
        } else {
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

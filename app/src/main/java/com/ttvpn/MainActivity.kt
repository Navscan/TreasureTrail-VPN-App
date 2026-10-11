package com.ttvpn

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnExit: Button
    private lateinit var checkLock: CheckBox
    private lateinit var txtState: TextView
    private lateinit var txtSubstate: TextView
    private lateinit var dotStatus: View
    private lateinit var cardMeta: MaterialCardView
    private lateinit var txtEndpoint: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtData: TextView
    private lateinit var txtEntitlement: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var pendingStart = false
    private var isFormattingVoucher = false
    private var suppressLockChange = false

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
        btnExit = findViewById(R.id.btnExit)
        checkLock = findViewById(R.id.checkLock)
        txtState = findViewById(R.id.txtState)
        txtSubstate = findViewById(R.id.txtSubstate)
        dotStatus = findViewById(R.id.dotStatus)
        cardMeta = findViewById(R.id.cardMeta)
        txtEndpoint = findViewById(R.id.txtEndpoint)
        txtDuration = findViewById(R.id.txtDuration)
        txtData = findViewById(R.id.txtData)
        txtEntitlement = findViewById(R.id.txtEntitlement)

        btnToggle.setOnClickListener { onToggleTapped() }
        btnExit.setOnClickListener { onExitTapped() }

        checkLock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressLockChange) return@setOnCheckedChangeListener
            ConfigStore.setLockEnabled(this, isChecked)
            if (isChecked && ConfigStore.hasConfig(this) && !ConfigStore.isVpnRunning(this)) {
                pendingStart = true
                requestConsentThenStart()
            }
            refreshUi()
        }

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
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != "treasuretrailvpn") return

        when (data.host) {
            "start" -> onToggleTapped()
            "stop" -> stopVpnService()
            "activate" -> {
                val code = data.getQueryParameter("code")
                if (!code.isNullOrEmpty()) {
                    registerWithCode(code.trim().uppercase(Locale.US))
                }
            }
        }
    }

    private fun onToggleTapped() {
        if (!ConfigStore.hasConfig(this)) {
            showVoucherDialog(null)
            return
        }
        if (ConfigStore.isVpnRunning(this)) {
            if (ConfigStore.isLockEnabled(this)) {
                showLockedDialog()
                return
            }
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
        VpnService.start(this)
        pendingStart = false
    }

    private fun stopVpnService() {
        VpnService.stop(this)
    }

    private fun onExitTapped() {
        finishAndRemoveTask()
    }

    private fun showLockedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_locked_title)
            .setMessage(R.string.dialog_locked_message)
            .setPositiveButton(R.string.dialog_locked_ok, null)
            .show()
    }

    private fun showVoucherDialog(prefill: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.voucher, null)
        val editVoucher = view.findViewById<EditText>(R.id.editVoucher)
        val txtError = view.findViewById<TextView>(R.id.txtError)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)

        editVoucher.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormattingVoucher) return
                isFormattingVoucher = true
                val formatted = formatVoucher(s?.toString().orEmpty())
                editVoucher.setText(formatted)
                editVoucher.setSelection(formatted.length)
                isFormattingVoucher = false
            }
        })

        val initial = prefill ?: readClipboardIfVoucher()
        if (!initial.isNullOrEmpty()) {
            editVoucher.setText(formatVoucher(initial))
            editVoucher.setSelection(editVoucher.text.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val code = editVoucher.text.toString().trim()
            if (!isVoucherComplete(code)) {
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

    private fun registerWithCode(code: String) {
        if (!isVoucherComplete(code)) return

        executor.execute {
            val result = ApiClient.register(code)
            handler.post {
                when (result) {
                    is RegisterResult.Success -> {
                        ConfigStore.saveConfig(
                            this,
                            result.config,
                            result.assignedIp,
                            result.subscriptionExpiresAt
                        )
                        pendingStart = true
                        requestConsentThenStart()
                        refreshUi()
                    }
                    is RegisterResult.Failure -> {
                        showErrorDialog(reasonToMessage(result.reason))
                    }
                    RegisterResult.NetworkError -> {
                        showErrorDialog(getString(R.string.error_network))
                    }
                }
            }
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun formatVoucher(raw: String): String {
        val clean = raw.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        val body = if (clean.startsWith("TVPN")) clean.substring(4) else clean
        val trimmed = body.take(12)
        val parts = trimmed.chunked(4)
        return if (parts.isEmpty()) "TVPN-"
        else "TVPN-" + parts.joinToString("-")
    }

    private fun isVoucherComplete(code: String): Boolean {
        val clean = code.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        val body = if (clean.startsWith("TVPN")) clean.substring(4) else clean
        return body.length == 12 && body.all { it.isLetterOrDigit() }
    }

    private fun readClipboardIfVoucher(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
            val clean = text.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
            val body = if (clean.startsWith("TVPN")) clean.substring(4) else clean
            if (body.length == 12) text else null
        } catch (_: Exception) {
            null
        }
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
        val lockOn = ConfigStore.isLockEnabled(this)

        suppressLockChange = true
        checkLock.isChecked = lockOn
        suppressLockChange = false

        val lockPrefix = if (lockOn) getString(R.string.lock_prefix) else ""

        when {
            !hasConfig -> {
                applyState(
                    text = getString(R.string.state_not_setup),
                    subtext = getString(R.string.substate_not_setup),
                    wordColour = R.color.muted_label,
                    dotColour = R.color.state_red,
                    buttonText = getString(R.string.action_enter_voucher)
                )
                cardMeta.visibility = View.GONE
            }
            connecting -> {
                applyState(
                    text = getString(R.string.state_connecting),
                    subtext = lockPrefix + getString(R.string.substate_connecting),
                    wordColour = R.color.state_amber,
                    dotColour = R.color.state_amber,
                    buttonText = getString(R.string.action_disconnect)
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
            running -> {
                val networkType = Stats.networkType
                val networkPresent = networkType != "NONE"

                if (networkPresent) {
                    val sub = when (networkType) {
                        "WIFI" -> getString(R.string.substate_secured_wifi)
                        "CELLULAR" -> getString(R.string.substate_secured_cellular)
                        "ETHERNET" -> getString(R.string.substate_secured_ethernet)
                        else -> getString(R.string.substate_secured_other)
                    }
                    applyState(
                        text = getString(R.string.state_secured),
                        subtext = lockPrefix + sub,
                        wordColour = R.color.brand_gold,
                        dotColour = R.color.state_green,
                        buttonText = getString(R.string.action_disconnect)
                    )
                } else {
                    applyState(
                        text = getString(R.string.state_standing_by),
                        subtext = lockPrefix + getString(R.string.substate_standing_by),
                        wordColour = R.color.state_amber,
                        dotColour = R.color.state_amber,
                        buttonText = getString(R.string.action_disconnect)
                    )
                }
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = true)
            }
            else -> {
                applyState(
                    text = getString(R.string.state_disconnected),
                    subtext = lockPrefix + getString(R.string.substate_disconnected),
                    wordColour = R.color.muted_label,
                    dotColour = R.color.state_red,
                    buttonText = getString(R.string.action_connect)
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
        }
    }

    private fun applyState(
        text: String,
        subtext: String,
        wordColour: Int,
        dotColour: Int,
        buttonText: String
    ) {
        val wordC = ContextCompat.getColor(this, wordColour)
        val dotC = ContextCompat.getColor(this, dotColour)

        txtState.text = text
        txtState.setTextColor(wordC)
        txtSubstate.text = subtext
        dotStatus.background?.setTint(dotC)
        btnToggle.text = buttonText
        btnToggle.isEnabled = true
    }

    private fun updateMeta(running: Boolean) {
        txtEndpoint.text = "${Config.WG_SERVER_FLAG}  ${Config.WG_ENDPOINT_DISPLAY}"

        if (running) {
            val startedAt = ConfigStore.getTunnelStartedAt(this)
            txtDuration.text = if (startedAt > 0L) {
                formatDuration(System.currentTimeMillis() - startedAt)
            } else {
                getString(R.string.duration_placeholder)
            }
            txtData.text = formatData(Stats.rx, Stats.tx)
        } else {
            txtDuration.text = getString(R.string.duration_placeholder)
            txtData.text = getString(R.string.data_placeholder)
        }

        val expires = ConfigStore.getSubscriptionExpiresAt(this)
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
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun formatData(rx: Long, tx: Long): String {
        val mb = 1024.0 * 1024.0
        return String.format(Locale.US, "↓%.1f ↑%.1f MB", rx / mb, tx / mb)
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

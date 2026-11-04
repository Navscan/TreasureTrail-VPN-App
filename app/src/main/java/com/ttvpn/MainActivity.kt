package com.ttvpn

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var txtFooterVersion: TextView
    private lateinit var btnUpdateReady: TextView
    private lateinit var txtFooterDot2: TextView
    private lateinit var logEntriesContainer: LinearLayout
    private lateinit var logsScroll: ScrollView
    private lateinit var txtLoggingState: TextView
    private lateinit var btnClearLog: TextView
    private lateinit var chkKeepLog: CheckBox
    private lateinit var btnEnterprise: TextView

    private lateinit var cardUpgrade: MaterialCardView
    private lateinit var btnUpgrade: Button

    private lateinit var cardCerberus: MaterialCardView
    private lateinit var btnCerberusRefresh: TextView
    private lateinit var txtCerberusStatus: TextView
    private lateinit var txtCerberusVitals: TextView
    private lateinit var txtCerberusFeedLabel: TextView
    private lateinit var cerberusThreatFeed: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var awaitingPayment = false
    private var pendingStart = false
    private var isFormattingVoucher = false
    private var suppressLockChange = false
    private var suppressLogToggle = false
    private var lastLogFingerprint: String = ""
    private var logUserScrolledAway: Boolean = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            EventLog.write(this, "OK", "Consent granted")
            doStartVpnService()
        } else {
            EventLog.write(this, "WARN", "Consent denied")
            pendingStart = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (hasInstallPermission()) {
            EventLog.write(this, "EVENT", "Update install fired")
            Updater.installStagedUpdate(this)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, Config.STATE_POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ConfigStore.getAuthToken(this) == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

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
        txtFooterVersion = findViewById(R.id.txtFooterVersion)
        btnUpdateReady = findViewById(R.id.btnUpdateReady)
        txtFooterDot2 = findViewById(R.id.txtFooterDot2)
        logEntriesContainer = findViewById(R.id.logEntriesContainer)
        logsScroll = findViewById(R.id.logsScroll)
        txtLoggingState = findViewById(R.id.txtLoggingState)
        btnClearLog = findViewById(R.id.btnClearLog)
        chkKeepLog = findViewById(R.id.chkKeepLog)
        btnEnterprise = findViewById(R.id.btnEnterprise)

        cardUpgrade = findViewById(R.id.cardUpgrade)
        btnUpgrade = findViewById(R.id.btnUpgrade)

        cardCerberus = findViewById(R.id.cardCerberus)
        btnCerberusRefresh = findViewById(R.id.btnCerberusRefresh)
        txtCerberusStatus = findViewById(R.id.txtCerberusStatus)
        txtCerberusVitals = findViewById(R.id.txtCerberusVitals)
        txtCerberusFeedLabel = findViewById(R.id.txtCerberusFeedLabel)
        cerberusThreatFeed = findViewById(R.id.cerberusThreatFeed)

        btnToggle.setOnClickListener { onToggleTapped() }
        btnUpgrade.setOnClickListener { showUpgradeDialog() }
        btnEnterprise.setOnClickListener { showVoucherDialog(null) }
        
        btnExit.setOnClickListener {
            ConfigStore.clearAuth(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        btnUpdateReady.setOnClickListener { onUpdateReadyTapped() }
        btnCerberusRefresh.setOnClickListener { refreshCerberus() }

        cardMeta.setOnLongClickListener {
            if (ConfigStore.hasConfig(this)) {
                showDnsDialog()
            }
            true
        }

        checkLock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressLockChange) return@setOnCheckedChangeListener
            ConfigStore.setLockEnabled(this, isChecked)
            EventLog.write(this, "EVENT", if (isChecked) "Lock toggled on" else "Lock toggled off")
            if (isChecked && ConfigStore.hasConfig(this) && !ConfigStore.isVpnRunning(this)) {
                pendingStart = true
                requestConsentThenStart()
            }
            refreshUi()
        }

        btnClearLog.setOnClickListener {
            EventLog.clear(this)
            renderLogPanel(force = true)
        }

        logsScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            logUserScrolledAway = !isLogsScrollAtBottom()
        }

        suppressLogToggle = true
        chkKeepLog.isChecked = ConfigStore.isLogEnabled(this)
        suppressLogToggle = false
        setLoggingStateText(ConfigStore.isLogEnabled(this))

        chkKeepLog.setOnCheckedChangeListener { _, isChecked ->
            if (suppressLogToggle) return@setOnCheckedChangeListener
            if (isChecked) {
                ConfigStore.setLogEnabled(this, true)
                EventLog.write(this, "EVENT", "Logging: enabled")
            } else {
                EventLog.write(this, "EVENT", "Logging: disabled")
                ConfigStore.setLogEnabled(this, false)
            }
            setLoggingStateText(isChecked)
            renderLogPanel(force = true)
        }

        requestNotificationPermissionIfNeeded()
        cleanupAfterInstall()
        EventLog.init(this)
        EventLog.write(this, "EVENT", "App opened")
        refreshUi()
        txtFooterVersion.text = "v" + BuildConfig.VERSION_NAME + " ©PayBridge(Pty) Ltd 2026"
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
        maybeCheckForUpdate()

        if (awaitingPayment) {
            awaitingPayment = false
            checkLicenseAndRefresh()
        } else {
            val tier = ConfigStore.getUserTier(this)
            if (tier == "enterprise" && ConfigStore.hasConfig(this)) {
                cardCerberus.visibility = View.VISIBLE
                refreshCerberus()
            } else {
                cardCerberus.visibility = View.GONE
            }
        }
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

    private fun checkLicenseAndRefresh() {
        val token = ConfigStore.getAuthToken(this) ?: return
        executor.execute {
            val result = AuthClient.checkLicense(token)
            handler.post {
                if (result.isActive) {
                    ConfigStore.saveAuth(this, token, result.tier)
                    EventLog.write(this, "OK", "License verified: ${result.tier}")
                    refreshUi()
                }
            }
        }
    }

    private fun showUpgradeDialog() {
        val plans = arrayOf("Pro - R99/month", "Enterprise - R299/month")
        AlertDialog.Builder(this)
            .setTitle("Select Plan")
            .setItems(plans) { _, which ->
                val plan = if (which == 0) "pro" else "enterprise"
                initiatePayment(plan)
            }
            .show()
    }

    private fun initiatePayment(plan: String) {
        val token = ConfigStore.getAuthToken(this) ?: return
        EventLog.write(this, "INFO", "Initiating $plan payment...")
        executor.execute {
            val url = ApiClient.initiatePayment(token, plan)
            handler.post {
                if (url != null) {
                    awaitingPayment = true
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } else {
                    EventLog.write(this, "ERR", "Payment initiation failed")
                    showErrorDialog("Could not initiate payment.")
                }
            }
        }
    }

    private fun refreshCerberus() {
        if (cardCerberus.visibility != View.VISIBLE) return

        btnCerberusRefresh.isEnabled = false
        txtCerberusStatus.text = "Loading…"
        txtCerberusStatus.setTextColor(ContextCompat.getColor(this, R.color.muted_label))

        executor.execute {
            val result = CerberusClient.fetch()
            handler.post {
                btnCerberusRefresh.isEnabled = true
                when (result) {
                    is CerberusResult.Success -> renderCerberus(result.status)
                    is CerberusResult.Failure -> {
                        txtCerberusStatus.text = "Error: ${result.reason}"
                        txtCerberusStatus.setTextColor(ContextCompat.getColor(this, R.color.state_red))
                        hideCerberusDetails()
                    }
                    CerberusResult.NetworkError -> {
                        txtCerberusStatus.text = "Network error — cannot reach VPS"
                        txtCerberusStatus.setTextColor(ContextCompat.getColor(this, R.color.state_red))
                        hideCerberusDetails()
                    }
                }
            }
        }
    }

    private fun hideCerberusDetails() {
        txtCerberusVitals.visibility = View.GONE
        txtCerberusFeedLabel.visibility = View.GONE
        cerberusThreatFeed.visibility = View.GONE
    }

    private fun renderCerberus(s: CerberusStatus) {
        val jailCount = s.jails.size
        txtCerberusStatus.text = "LIVE · $jailCount jail${if (jailCount == 1) "" else "s"} monitored"
        txtCerberusStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_teal))

        val sb = StringBuilder()
        sb.append("Uptime   ").append(formatUptimeHuman(s.uptimeSeconds)).append('\n')
        sb.append("Mem      ").append(s.memPercent).append("%   ")
            .append(formatBytes(s.memUsedBytes)).append(" / ").append(formatBytes(s.memTotalBytes)).append('\n')
        sb.append("Disk     ").append(s.diskPercent).append("%   ")
            .append(formatBytes(s.diskUsedBytes)).append(" / ").append(formatBytes(s.diskTotalBytes)).append('\n')
        sb.append("Fail2ban ").append(s.bannedTotal).append(" banned across ")
            .append(jailCount).append(" jail").append(if (jailCount == 1) "" else "s").append('\n')
        if (s.failedServices.isEmpty()) {
            sb.append("Failed   none")
        } else {
            sb.append("Failed   ").append(s.failedServices.joinToString(", "))
        }
        txtCerberusVitals.text = sb.toString()
        txtCerberusVitals.setTextColor(
            ContextCompat.getColor(
                this,
                if (s.failedServices.isEmpty() && s.bannedTotal == 0) R.color.text_main else R.color.state_amber
            )
        )
        txtCerberusVitals.visibility = View.VISIBLE

        cerberusThreatFeed.removeAllViews()
        txtCerberusFeedLabel.visibility = View.VISIBLE
        cerberusThreatFeed.visibility = View.VISIBLE

        if (s.recentBanned.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No active bans"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_label))
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                textSize = 10f
            }
            cerberusThreatFeed.addView(tv)
        } else {
            s.recentBanned.take(10).forEach { b ->
                val tv = TextView(this).apply {
                    text = "${b.ip}   [${b.jail}]"
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.state_amber))
                    typeface = Typeface.MONOSPACE
                    textSize = 10f
                    setPadding(0, 2, 0, 2)
                }
                cerberusThreatFeed.addView(tv)
            }
        }
    }

    private fun formatUptimeHuman(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h ${m}m"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    private fun formatBytes(b: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            b >= gb -> String.format(Locale.US, "%.1f GB", b / gb)
            b >= mb -> String.format(Locale.US, "%.0f MB", b / mb)
            else -> String.format(Locale.US, "%.0f KB", b / kb)
        }
    }

    private fun onUpdateReadyTapped() {
        if (hasInstallPermission()) {
            EventLog.write(this, "EVENT", "Update install fired")
            Updater.installStagedUpdate(this)
        } else {
            try {
                installPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun cleanupAfterInstall() {
        try {
            val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
            val stagedVersion = prefs.getInt(Config.PREF_STAGED_UPDATE_VERSION, 0)
            if (stagedVersion > 0 && stagedVersion <= BuildConfig.VERSION_CODE) {
                Updater.clearStagedUpdate(this)
            }
        } catch (_: Exception) {
        }
    }

    private fun maybeCheckForUpdate() {
        executor.execute {
            try {
                val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                val stagedUrl = prefs.getString(Config.PREF_STAGED_UPDATE_URL, null)
                val stagedVersion = prefs.getInt(Config.PREF_STAGED_UPDATE_VERSION, 0)
                val retryDownload = !stagedUrl.isNullOrEmpty() &&
                    stagedVersion > BuildConfig.VERSION_CODE &&
                    !Updater.isUpdateStaged(this)

                if (retryDownload) {
                    Updater.downloadApk(this)
                } else {
                    val remote = Updater.checkForUpdate(this)
                    if (remote != null) {
                        Updater.downloadApk(this)
                    }
                }
            } catch (_: Exception) {
            }
            handler.post { refreshUi() }
        }
    }

    private fun setLoggingStateText(enabled: Boolean) {
        val label = "Logging: "
        val word = if (enabled) "ON" else "OFF"
        val full = label + word
        val sp = SpannableString(full)
        val labelColor = ContextCompat.getColor(this, R.color.muted_label)
        val wordColor = ContextCompat.getColor(
            this, if (enabled) R.color.brand_teal else R.color.state_red
        )
        sp.setSpan(ForegroundColorSpan(labelColor), 0, label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(wordColor), label.length, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.BOLD), label.length, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        txtLoggingState.text = sp
    }

    private fun isLogsScrollAtBottom(): Boolean {
        val child = logsScroll.getChildAt(0) ?: return true
        return logsScroll.scrollY + logsScroll.height >= child.height - 8
    }

    private fun tagColourFor(tag: String): Int = when (tag) {
        "OK" -> R.color.brand_teal
        "INFO" -> R.color.info_blue
        "WARN" -> R.color.state_amber
        "ERR" -> R.color.state_red
        else -> R.color.text_dim
    }

    private fun renderLogPanel(force: Boolean = false) {
        val entries = EventLog.readAll(this)
        val fingerprint = "${entries.size}:${entries.lastOrNull()?.timestamp.orEmpty()}"
        if (!force && fingerprint == lastLogFingerprint) return
        lastLogFingerprint = fingerprint

        val density = resources.displayMetrics.density
        val padV = (3 * density).toInt()
        logEntriesContainer.removeAllViews()

        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.dialog_logs_empty)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_label))
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                setPadding(0, padV, 0, padV)
            }
            logEntriesContainer.addView(empty)
        } else {
            entries.forEachIndexed { index, entry ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, padV, 0, padV)
                }

                val time = TextView(this).apply {
                    text = entry.timestamp
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_label))
                    typeface = Typeface.MONOSPACE
                    textSize = 10f
                    layoutParams = LinearLayout.LayoutParams((60 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                val tag = TextView(this).apply {
                    text = entry.tag
                    setTextColor(ContextCompat.getColor(this@MainActivity, tagColourFor(entry.tag)))
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = 10f
                    layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                val msg = TextView(this).apply {
                    text = entry.message
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
                    typeface = Typeface.MONOSPACE
                    textSize = 11f
                    ellipsize = TextUtils.TruncateAt.END
                    maxLines = 2
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                row.addView(time)
                row.addView(tag)
                row.addView(msg)
                logEntriesContainer.addView(row)

                if (index < entries.size - 1) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.border_inner))
                    }
                    logEntriesContainer.addView(divider)
                }
            }
        }

        if (!logUserScrolledAway) {
            logsScroll.post { logsScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        val scheme = data.scheme ?: return
        if (scheme != "treasuretrailvpn" && scheme != "cpvpn") return

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
        val token = ConfigStore.getAuthToken(this)
        if (token == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        executor.execute {
            val result = AuthClient.checkLicense(token)
            handler.post {
                if (!result.isActive) {
                    AlertDialog.Builder(this)
                        .setTitle("Subscription Inactive")
                        .setMessage("Your subscription is not active. Please upgrade to Pro or Enterprise.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@post
                }

                if (!ConfigStore.hasConfig(this)) {
                    EventLog.write(this, "INFO", "Provisioning VPN config...")
                    btnToggle.isEnabled = false

                    executor.execute {
                        val provResult = ApiClient.provisionConfig(token)
                        handler.post {
                            btnToggle.isEnabled = true
                            when (provResult) {
                                is RegisterResult.Success -> {
                                    ConfigStore.saveConfig(this, provResult.config, provResult.assignedIp, null)
                                    EventLog.write(this, "OK", "VPN config provisioned")
                                    pendingStart = true
                                    requestConsentThenStart()
                                    refreshUi()
                                }
                                is RegisterResult.Failure -> {
                                    EventLog.write(this, "ERR", "Provision failed: ${provResult.reason}")
                                    showErrorDialog("Failed to get VPN config: ${provResult.reason}")
                                }
                                RegisterResult.NetworkError -> {
                                    EventLog.write(this, "ERR", "Provision network error")
                                    showErrorDialog("Network error. Could not reach backend.")
                                }
                            }
                        }
                    }
                    return@post
                }

                if (ConfigStore.isVpnRunning(this)) {
                    if (ConfigStore.isLockEnabled(this)) {
                        showLockedDialog()
                        return@post
                    }
                    EventLog.write(this, "EVENT", "Disconnect tapped")
                    stopVpnService()
                } else {
                    EventLog.write(this, "EVENT", "Connect tapped")
                    pendingStart = true
                    requestConsentThenStart()
                }
            }
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

    private fun showLockedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_locked_title)
            .setMessage(R.string.dialog_locked_message)
            .setPositiveButton(R.string.dialog_locked_ok, null)
            .show()
    }

    private fun showDnsDialog() {
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        val label = TextView(this).apply {
            text = getString(R.string.dialog_dns_label)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            textSize = 14f
        }

        val input = EditText(this).apply {
            hint = getString(R.string.dialog_dns_placeholder)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(ConfigStore.getCustomDns(this@MainActivity) ?: "")
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_label))
            textSize = 16f
        }

        val hint = TextView(this).apply {
            text = getString(R.string.dialog_dns_hint)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted_label))
            textSize = 12f
            setPadding(0, pad / 2, 0, 0)
        }

        container.addView(label)
        container.addView(input)
        container.addView(hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_dns_title)
            .setView(container)
            .setPositiveButton(R.string.dialog_dns_save) { _, _ ->
                val value = input.text.toString().trim()
                applyDns(value.ifBlank { null })
            }
            .setNeutralButton(R.string.dialog_dns_reset) { _, _ ->
                applyDns(null)
            }
            .setNegativeButton(R.string.dialog_dns_cancel, null)
            .show()
    }

    private fun applyDns(dns: String?) {
        val wasRunning = ConfigStore.isVpnRunning(this)
        if (!ConfigStore.setCustomDns(this, dns)) return

        EventLog.write(this, "EVENT", if (dns.isNullOrBlank()) "DNS reset" else "DNS set to $dns")

        if (wasRunning) {
            VpnService.stop(this)
            handler.postDelayed({
                pendingStart = true
                requestConsentThenStart()
            }, Config.DNS_RESTART_DELAY_MS)
        }
        refreshUi()
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

            EventLog.write(this, "EVENT", "Voucher submitted  ${EventLog.maskVoucher(code)}")

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
                            ConfigStore.saveConfig(this, result.config, result.assignedIp, result.subscriptionExpiresAt)
                            EventLog.write(this, "OK", "Voucher accepted")
                            dialog.dismiss()
                            pendingStart = true
                            requestConsentThenStart()
                            refreshUi()
                        }
                        is RegisterResult.Failure -> {
                            EventLog.write(this, "ERR", "Voucher rejected  ${result.reason}")
                            txtError.text = reasonToMessage(result.reason)
                            txtError.visibility = View.VISIBLE
                        }
                        RegisterResult.NetworkError -> {
                            EventLog.write(this, "ERR", "Voucher rejected  network")
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
                        ConfigStore.saveConfig(this, result.config, result.assignedIp, result.subscriptionExpiresAt)
                        EventLog.write(this, "OK", "Voucher accepted")
                        pendingStart = true
                        requestConsentThenStart()
                        refreshUi()
                    }
                    is RegisterResult.Failure -> {
                        EventLog.write(this, "ERR", "Voucher rejected  ${result.reason}")
                        showErrorDialog(reasonToMessage(result.reason))
                    }
                    RegisterResult.NetworkError -> {
                        EventLog.write(this, "ERR", "Voucher rejected  network")
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
        val trimmed = clean.take(16)
        val parts = trimmed.chunked(4)
        return parts.joinToString("-")
    }

    private fun isVoucherComplete(code: String): Boolean {
        val clean = code.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        return clean.length == 16 && clean.all { it.isLetterOrDigit() }
    }

    private fun readClipboardIfVoucher(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
            val clean = text.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
            if (clean.length == 16) text else null
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
        val customDns = ConfigStore.getCustomDns(this)
        val tier = ConfigStore.getUserTier(this)

        suppressLockChange = true
        checkLock.isChecked = lockOn
        suppressLockChange = false

        if (tier == "free") {
            cardUpgrade.visibility = View.VISIBLE
        } else {
            cardUpgrade.visibility = View.GONE
        }
        
        if (tier == "enterprise" && hasConfig) {
            cardCerberus.visibility = View.VISIBLE
        } else {
            cardCerberus.visibility = View.GONE
        }

        val updateStaged = Updater.isUpdateStaged(this)
        btnUpdateReady.visibility = if (updateStaged) View.VISIBLE else View.GONE
        txtFooterDot2.visibility = if (updateStaged) View.VISIBLE else View.GONE

        renderLogPanel()

        val lockPrefix = if (lockOn) getString(R.string.lock_prefix) else ""
        val dnsSuffix = if (!customDns.isNullOrBlank()) getString(R.string.substate_dns_suffix, customDns) else ""

        when {
            !hasConfig -> {
                applyState(
                    text = getString(R.string.state_not_setup),
                    subtext = getString(R.string.substate_not_setup),
                    wordColour = R.color.muted_label,
                    dotColour = R.color.state_red,
                    buttonText = getString(R.string.action_connect)
                )
                cardMeta.visibility = View.GONE
            }
            connecting -> {
                applyState(
                    text = getString(R.string.state_connecting),
                    subtext = lockPrefix + getString(R.string.substate_connecting) + dnsSuffix,
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
                        subtext = lockPrefix + sub + dnsSuffix,
                        wordColour = R.color.brand_gold,
                        dotColour = R.color.state_green,
                        buttonText = getString(R.string.action_disconnect)
                    )
                } else {
                    applyState(
                        text = getString(R.string.state_standing_by),
                        subtext = lockPrefix + getString(R.string.substate_standing_by) + dnsSuffix,
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
                    subtext = lockPrefix + getString(R.string.substate_disconnected) + dnsSuffix,
                    wordColour = R.color.muted_label,
                    dotColour = R.color.state_red,
                    buttonText = getString(R.string.action_connect)
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
        }
    }

    private fun applyState(text: String, subtext: String, wordColour: Int, dotColour: Int, buttonText: String) {
        txtState.text = text
        txtState.setTextColor(ContextCompat.getColor(this, wordColour))
        txtSubstate.text = subtext
        dotStatus.background?.setTint(ContextCompat.getColor(this, dotColour))
        btnToggle.text = buttonText
        btnToggle.isEnabled = true
    }

    private fun updateMeta(running: Boolean) {
        val displayFlag = ConfigStore.getServerFlag(this)
        val displayEndpoint = ConfigStore.getEndpoint(this)
        txtEndpoint.text = "$displayFlag  $displayEndpoint"

        if (running) {
            val startedAt = ConfigStore.getTunnelStartedAt(this)
            txtDuration.text = if (startedAt > 0L) formatDuration(System.currentTimeMillis() - startedAt) else getString(R.string.duration_placeholder)
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
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

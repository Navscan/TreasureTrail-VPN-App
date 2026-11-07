package com.ttvpn

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.text.SpannableStringBuilder
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
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnExit: TextView
    private lateinit var btnLogout: TextView
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
    private lateinit var btnTopRightStatus: TextView

    private lateinit var tabLogs: TextView
    private lateinit var tabThreat: TextView
    private lateinit var cardLogs: MaterialCardView

    private lateinit var cardUpgrade: MaterialCardView
    private lateinit var btnUpgrade: Button

    private lateinit var cardCerberus: MaterialCardView
    private lateinit var btnCerberusRefresh: TextView
    private lateinit var txtCerberusStatus: TextView
    private lateinit var txtCerberusVitals: TextView
    private lateinit var txtCerberusFeedLabel: TextView
    private lateinit var cerberusThreatFeed: LinearLayout
    private lateinit var btnOpenThreatCentre: Button

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var awaitingPayment = false
    private var pendingStart = false
    private var isFormattingVoucher = false
    private var suppressLockChange = false
    private var suppressLogToggle = false
    private var lastLogFingerprint: String = ""
    private var logUserScrolledAway: Boolean = false
    private var isThreatTabActive = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            EventLog.write(this, "OK", "Consent granted")
            doStartVpnService()
        } else {
            EventLog.write(this, "WARN", "Consent denied")
            pendingStart = false
            refreshUi()
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
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnExit = findViewById(R.id.btnExit)
        btnLogout = findViewById(R.id.btnLogout)
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
        btnTopRightStatus = findViewById(R.id.btnTopRightStatus)

        tabLogs = findViewById(R.id.tabLogs)
        tabThreat = findViewById(R.id.tabThreat)
        cardLogs = findViewById(R.id.cardLogs)

        cardUpgrade = findViewById(R.id.cardUpgrade)
        btnUpgrade = findViewById(R.id.btnUpgrade)

        cardCerberus = findViewById(R.id.cardCerberus)
        btnCerberusRefresh = findViewById(R.id.btnCerberusRefresh)
        txtCerberusStatus = findViewById(R.id.txtCerberusStatus)
        txtCerberusVitals = findViewById(R.id.txtCerberusVitals)
        txtCerberusFeedLabel = findViewById(R.id.txtCerberusFeedLabel)
        cerberusThreatFeed = findViewById(R.id.cerberusThreatFeed)
        btnOpenThreatCentre = findViewById(R.id.btnOpenThreatCentre)

        tabLogs.setOnClickListener { switchTab(false) }
        tabThreat.setOnClickListener { switchTab(true) }

        btnToggle.setOnClickListener { onToggleTapped() }
        btnUpgrade.setOnClickListener { showUpgradeDialog() }
        btnTopRightStatus.setOnClickListener { onTopRightTapped() }
        btnOpenThreatCentre.setOnClickListener {
            startActivity(Intent(this, ThreatCentreActivity::class.java))
        }
        
        btnExit.setOnClickListener { finishAffinity() }

        btnLogout.setOnClickListener {
            VpnService.stop(this)
            ConfigStore.clearAuth(this) 
            finishAffinity()
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
        
        if (ConfigStore.getAuthToken(this) == null) {
            silentGuestRegister()
        }

        switchTab(false)
        refreshUi()
        txtFooterVersion.text = "v" + BuildConfig.VERSION_NAME + " ©PayBridge(Pty) Ltd 2026"
        handleIntent(intent)
    }

    private fun silentGuestRegister() {
        btnToggle.isEnabled = false
        btnToggle.text = "INITIALIZING..."
        executor.execute {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
            val result = AuthClient.guestLogin(deviceId)
            handler.post {
                btnToggle.isEnabled = true
                if (result is AuthResult.Success) {
                    ConfigStore.saveAuth(this, result.token, result.tier)
                    EventLog.write(this, "OK", "Guest session initialized")
                }
                refreshUi()
            }
        }
    }

    private fun onTopRightTapped() {
        val tier = ConfigStore.getUserTier(this)
        when (tier) {
            "enterprise" -> {
                val savedVoucher = ConfigStore.getVoucherCode(this)
                showVoucherDialog(savedVoucher)
            }
            "pro" -> {
                AlertDialog.Builder(this)
                    .setTitle("Pro Plan Active")
                    .setMessage("Your Pro subscription is managed via FlipStack.store. Thank you for your support!")
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> {
                val options = arrayOf("Login as Enterprise User", "Upgrade to Pro")
                AlertDialog.Builder(this)
                    .setTitle("Open Tier")
                    .setItems(options) { _, which ->
                        if (which == 0) {
                            startActivity(Intent(this, LoginActivity::class.java))
                        } else {
                            showUpgradeDialog()
                        }
                    }
                    .show()
            }
        }
    }

    private fun switchTab(threatActive: Boolean) {
        isThreatTabActive = threatActive
        if (threatActive) {
            tabThreat.setTextColor(Color.parseColor("#f5c842"))
            tabThreat.setBackgroundColor(Color.parseColor("#1e2d3d"))
            tabLogs.setTextColor(Color.parseColor("#8a9bb0"))
            tabLogs.setBackgroundColor(Color.TRANSPARENT)

            cardLogs.visibility = View.GONE
            cardCerberus.visibility = View.VISIBLE
            refreshCerberus()
        } else {
            tabLogs.setTextColor(Color.parseColor("#f5c842"))
            tabLogs.setBackgroundColor(Color.parseColor("#1e2d3d"))
            tabThreat.setTextColor(Color.parseColor("#8a9bb0"))
            tabThreat.setBackgroundColor(Color.TRANSPARENT)

            cardLogs.visibility = View.VISIBLE
            cardCerberus.visibility = View.GONE
            renderLogPanel(force = true)
        }
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
            if (tier != "enterprise") {
                if (isThreatTabActive) {
                    switchTab(false)
                }
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
        val plans = arrayOf("Pro - R99/month (US & Global Access)")
        AlertDialog.Builder(this)
            .setTitle("Upgrade to Pro")
            .setItems(plans) { _, _ ->
                initiatePayment("pro")
            }
            .show()
    }

    private fun initiatePayment(plan: String) {
        val token = ConfigStore.getAuthToken(this) ?: return
        val currentEmail = ConfigStore.getUserEmail(this)
        
        // If the user is a Guest (or email failed to parse), prompt for a real email
        if (currentEmail.endsWith("@cpvpn.local") || currentEmail.isBlank()) {
            promptForEmail(plan)
            return
        }

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

    private fun promptForEmail(plan: String) {
        val input = EditText(this).apply {
            hint = "you@example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        
        AlertDialog.Builder(this)
            .setTitle("Enter Email Address")
            .setMessage("Please enter your email address to receive your Pro receipt and manage your account.")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->
                val newEmail = input.text.toString().trim()
                if (newEmail.isBlank() || !newEmail.contains("@")) {
                    showErrorDialog("Please enter a valid email address.")
                    return@setPositiveButton
                }
                
                val token = ConfigStore.getAuthToken(this) ?: return@setPositiveButton
                executor.execute {
                    val updateResult = AuthClient.updateEmail(token, newEmail)
                    handler.post {
                        if (updateResult is AuthResult.Success) {
                            ConfigStore.saveAuth(this, updateResult.token, "free")
                            ConfigStore.saveUserEmail(this, newEmail)
                            initiatePayment(plan)
                        } else {
                            showErrorDialog("Failed to update email. It might already be in use.")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshCerberus() {
        if (!isThreatTabActive) return

        btnCerberusRefresh.isEnabled = false
        txtCerberusStatus.text = "Loading…"
        txtCerberusStatus.setTextColor(Color.parseColor("#8a9bb0"))

        executor.execute {
            val result = CerberusClient.fetch()
            handler.post {
                btnCerberusRefresh.isEnabled = true
                when (result) {
                    is CerberusResult.Success -> renderCerberus(result.status)
                    is CerberusResult.Failure -> {
                        txtCerberusStatus.text = "Error: ${result.reason}"
                        txtCerberusStatus.setTextColor(Color.parseColor("#ff6b6b"))
                        hideCerberusDetails()
                    }
                    CerberusResult.NetworkError -> {
                        txtCerberusStatus.text = "Network error — cannot reach VPS"
                        txtCerberusStatus.setTextColor(Color.parseColor("#ff6b6b"))
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
        btnOpenThreatCentre.visibility = View.GONE
    }

    private fun renderCerberus(s: CerberusStatus) {
        val jailCount = s.jails.size
        txtCerberusStatus.text = "LIVE · $jailCount jail${if (jailCount == 1) "" else "s"} monitored"
        txtCerberusStatus.setTextColor(Color.parseColor("#4cd9b2"))

        val sb = SpannableStringBuilder()
        fun appendLine(label: String, value: String) {
            val start = sb.length
            sb.append(label.padEnd(9))
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#8a9bb0")), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val valStart = sb.length
            sb.append(value)
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#4cd9b2")), valStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n")
        }

        appendLine("Uptime", formatUptimeHuman(s.uptimeSeconds))
        appendLine("Mem", "${s.memPercent}%  ${formatBytes(s.memUsedBytes)} / ${formatBytes(s.memTotalBytes)}")
        appendLine("Disk", "${s.diskPercent}%  ${formatBytes(s.diskUsedBytes)} / ${formatBytes(s.diskTotalBytes)}")
        appendLine("Fail2ban", "${s.bannedTotal} banned across $jailCount jail${if (jailCount == 1) "" else "s"}")
        if (s.failedServices.isEmpty()) appendLine("Failed", "none") else appendLine("Failed", s.failedServices.joinToString(", "))

        txtCerberusVitals.text = sb
        txtCerberusVitals.visibility = View.VISIBLE

        cerberusThreatFeed.removeAllViews()
        txtCerberusFeedLabel.visibility = View.VISIBLE
        cerberusThreatFeed.visibility = View.VISIBLE

        if (s.recentBanned.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No active bans"
                setTextColor(Color.parseColor("#8a9bb0"))
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                textSize = 10f
            }
            cerberusThreatFeed.addView(tv)
        } else {
            s.recentBanned.take(10).forEach { b ->
                val tv = TextView(this).apply {
                    text = "${b.ip}   [${b.jail}]"
                    setTextColor(Color.parseColor("#ff6b6b"))
                    typeface = Typeface.MONOSPACE
                    textSize = 10f
                    setPadding(0, 2, 0, 2)
                }
                cerberusThreatFeed.addView(tv)
            }
        }

        btnOpenThreatCentre.visibility = View.VISIBLE
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
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
        }
    }

    private fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) packageManager.canRequestPackageInstalls() else true
    }

    private fun cleanupAfterInstall() {
        try {
            val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
            val stagedVersion = prefs.getInt(Config.PREF_STAGED_UPDATE_VERSION, 0)
            if (stagedVersion > 0 && stagedVersion <= BuildConfig.VERSION_CODE) {
                Updater.clearStagedUpdate(this)
            }
        } catch (_: Exception) {}
    }

    private fun maybeCheckForUpdate() {
        executor.execute {
            try {
                val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                val stagedUrl = prefs.getString(Config.PREF_STAGED_UPDATE_URL, null)
                val stagedVersion = prefs.getInt(Config.PREF_STAGED_UPDATE_VERSION, 0)
                val retryDownload = !stagedUrl.isNullOrEmpty() && stagedVersion > BuildConfig.VERSION_CODE && !Updater.isUpdateStaged(this)

                if (retryDownload) {
                    Updater.downloadApk(this)
                } else {
                    val remote = Updater.checkForUpdate(this)
                    if (remote != null) {
                        Updater.downloadApk(this)
                    }
                }
            } catch (_: Exception) {}
            handler.post { refreshUi() }
        }
    }

    private fun setLoggingStateText(enabled: Boolean) {
        val label = "Logging: "
        val word = if (enabled) "ON" else "OFF"
        val full = label + word
        val sp = SpannableString(full)
        val labelColor = Color.parseColor("#8a9bb0")
        val wordColor = Color.parseColor(if (enabled) "#4cd9b2" else "#ff6b6b")
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
        "OK" -> Color.parseColor("#4cd9b2")
        "INFO" -> Color.parseColor("#6bc9ff")
        "WARN" -> Color.parseColor("#f5b342")
        "ERR" -> Color.parseColor("#ff6b6b")
        else -> Color.parseColor("#8a9bb0")
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
                text = "No events logged"
                setTextColor(Color.parseColor("#8a9bb0"))
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
                    setTextColor(Color.parseColor("#5a6f82"))
                    typeface = Typeface.MONOSPACE
                    textSize = 10f
                    layoutParams = LinearLayout.LayoutParams((60 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val tag = TextView(this).apply {
                    text = entry.tag
                    setTextColor(tagColourFor(entry.tag))
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = 10f
                    layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val msg = TextView(this).apply {
                    text = entry.message
                    setTextColor(Color.parseColor("#c8d0e0"))
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
                        setBackgroundColor(Color.parseColor("#1e2d3d"))
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
        if (ConfigStore.isVpnRunning(this)) {
            if (ConfigStore.isLockEnabled(this)) {
                showLockedDialog()
                return
            }
            EventLog.write(this, "EVENT", "Disconnect tapped")
            stopVpnService()
            return
        }

        val token = ConfigStore.getAuthToken(this)
        val tier = ConfigStore.getUserTier(this)

        if (token == null) {
            if (ConfigStore.hasConfig(this)) {
                EventLog.write(this, "EVENT", "Connect tapped")
                pendingStart = true
                requestConsentThenStart()
                return
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Subscription Required")
                    .setMessage("Please upgrade to Pro or Enterprise to provision a VPN config.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
        }

        if (tier == "enterprise" && ConfigStore.getVoucherCode(this) == null) {
            showErrorDialog("No active voucher token found. Please tap Enterprise to enter your voucher.")
            return
        }

        btnToggle.isEnabled = false
        btnToggle.text = "CONNECTING..."
        EventLog.write(this, "EVENT", "Connect tapped")

        executor.execute {
            val result = AuthClient.checkLicense(token)
            handler.post {
                if (!result.isActive) {
                    btnToggle.isEnabled = true
                    btnToggle.text = "CONNECT"
                    AlertDialog.Builder(this)
                        .setTitle("Subscription Inactive")
                        .setMessage("Your subscription is not active. Please upgrade to Pro or Enterprise.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@post
                }

                if (!ConfigStore.hasConfig(this)) {
                    EventLog.write(this, "INFO", "Provisioning VPN config...")
                    executor.execute {
                        val provResult = ApiClient.provisionConfig(token)
                        handler.post {
                            btnToggle.isEnabled = true
                            btnToggle.text = "CONNECT"
                            when (provResult) {
                                is RegisterResult.Success -> {
                                    ConfigStore.saveConfig(this, provResult.config, provResult.assignedIp, null)
                                    EventLog.write(this, "OK", "VPN config provisioned")
                                    pendingStart = true
                                    requestConsentThenStart()
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

                btnToggle.isEnabled = true
                pendingStart = true
                requestConsentThenStart()
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
        btnToggle.isEnabled = false
        btnToggle.text = "DISCONNECT"
    }

    private fun stopVpnService() {
        VpnService.stop(this)
        btnToggle.isEnabled = false
        btnToggle.text = "CONNECT"
    }

    private fun showLockedDialog() {
        AlertDialog.Builder(this)
            .setTitle("VPN Locked")
            .setMessage("Disable the VPN lock first to disconnect.")
            .setPositiveButton("OK", null)
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
            text = "Custom DNS (leave blank to reset)"
            setTextColor(Color.parseColor("#e8edf5"))
            textSize = 14f
        }

        val input = EditText(this).apply {
            hint = "1.1.1.1"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(ConfigStore.getCustomDns(this@MainActivity) ?: "")
            setSelection(text.length)
            setTextColor(Color.parseColor("#e8edf5"))
            setHintTextColor(Color.parseColor("#8a9bb0"))
            textSize = 16f
        }

        container.addView(label)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Configure DNS")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                applyDns(value.ifBlank { null })
            }
            .setNeutralButton("Reset") { _, _ ->
                applyDns(null)
            }
            .setNegativeButton("Cancel", null)
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

        val initial = prefill ?: ConfigStore.getVoucherCode(this) ?: readClipboardIfVoucher()
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
                txtError.text = "Please enter a valid 16-character code."
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val token = ConfigStore.getAuthToken(this) ?: return@setOnClickListener
            EventLog.write(this, "EVENT", "Voucher submitted  ${EventLog.maskVoucher(code)}")

            btnSubmit.isEnabled = false
            btnCancel.isEnabled = false
            txtError.visibility = View.GONE

            executor.execute {
                val voucherResult = ApiClient.redeemVoucher(token, code)
                handler.post {
                    when (voucherResult) {
                        is RegisterResult.Success -> {
                            ConfigStore.saveVoucherCode(this, code)
                            EventLog.write(this, "OK", "Voucher accepted")
                            txtError.text = "Provisioning VPN..."
                            txtError.setTextColor(Color.parseColor("#f5c842"))
                            txtError.visibility = View.VISIBLE
                            
                            executor.execute {
                                val provResult = ApiClient.provisionConfig(token)
                                handler.post {
                                    btnSubmit.isEnabled = true
                                    btnCancel.isEnabled = true
                                    txtError.visibility = View.GONE
                                    when (provResult) {
                                        is RegisterResult.Success -> {
                                            ConfigStore.saveConfig(this, provResult.config, provResult.assignedIp, voucherResult.subscriptionExpiresAt)
                                            EventLog.write(this, "OK", "VPN config provisioned")
                                            dialog.dismiss()
                                            pendingStart = true
                                            requestConsentThenStart()
                                        }
                                        is RegisterResult.Failure -> {
                                            EventLog.write(this, "ERR", "Provision failed: ${provResult.reason}")
                                            txtError.text = "Voucher ok, but config failed: ${provResult.reason}"
                                            txtError.visibility = View.VISIBLE
                                        }
                                        RegisterResult.NetworkError -> {
                                            EventLog.write(this, "ERR", "Provision network error")
                                            txtError.text = "Provision network error"
                                            txtError.visibility = View.VISIBLE
                                        }
                                    }
                                }
                            }
                        }
                        is RegisterResult.Failure -> {
                            EventLog.write(this, "ERR", "Voucher rejected  ${voucherResult.reason}")
                            txtError.text = reasonToMessage(voucherResult.reason)
                            txtError.visibility = View.VISIBLE
                            btnSubmit.isEnabled = true
                            btnCancel.isEnabled = true
                        }
                        RegisterResult.NetworkError -> {
                            EventLog.write(this, "ERR", "Voucher rejected  network")
                            txtError.text = "Network error. Could not reach backend."
                            txtError.visibility = View.VISIBLE
                            btnSubmit.isEnabled = true
                            btnCancel.isEnabled = true
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun registerWithCode(code: String) {
        if (!isVoucherComplete(code)) return
        val token = ConfigStore.getAuthToken(this) ?: return
        
        executor.execute {
            val voucherResult = ApiClient.redeemVoucher(token, code)
            handler.post {
                when (voucherResult) {
                    is RegisterResult.Success -> {
                        ConfigStore.saveVoucherCode(this, code)
                        EventLog.write(this, "OK", "Voucher accepted")
                        executor.execute {
                            val provResult = ApiClient.provisionConfig(token)
                            handler.post {
                                when (provResult) {
                                    is RegisterResult.Success -> {
                                        ConfigStore.saveConfig(this, provResult.config, provResult.assignedIp, voucherResult.subscriptionExpiresAt)
                                        EventLog.write(this, "OK", "VPN config provisioned")
                                        pendingStart = true
                                        requestConsentThenStart()
                                    }
                                    is RegisterResult.Failure -> {
                                        showErrorDialog("Config failed: ${provResult.reason}")
                                    }
                                    RegisterResult.NetworkError -> {
                                        showErrorDialog("Provision network error")
                                    }
                                }
                            }
                        }
                    }
                    is RegisterResult.Failure -> {
                        EventLog.write(this, "ERR", "Voucher rejected  ${voucherResult.reason}")
                        showErrorDialog(reasonToMessage(voucherResult.reason))
                    }
                    RegisterResult.NetworkError -> {
                        EventLog.write(this, "ERR", "Voucher rejected  network")
                        showErrorDialog("Network error. Could not reach backend.")
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
        "voucher_not_found" -> "Voucher not found."
        "voucher_bound_to_different_user" -> "This voucher belongs to a different email."
        "voucher_already_used" -> "This voucher has already been used."
        "subscription_inactive" -> "Subscription is inactive."
        "user_not_found" -> "User not found."
        else -> "Unknown error."
    }

    private fun refreshUi() {
        val hasConfig = ConfigStore.hasConfig(this)
        val running = ConfigStore.isVpnRunning(this)
        val connecting = pendingStart && !running
        val lockOn = ConfigStore.isLockEnabled(this)
        val customDns = ConfigStore.getCustomDns(this)
        val tier = ConfigStore.getUserTier(this)

        if (tier == "enterprise") {
            btnLogout.visibility = View.VISIBLE
        } else {
            btnLogout.visibility = View.GONE
        }

        btnTopRightStatus.text = when (tier) {
            "enterprise" -> "Enterprise"
            "pro" -> "Pro"
            else -> "Open"
        }

        suppressLockChange = true
        checkLock.isChecked = lockOn
        suppressLockChange = false

        if (tier == "free") {
            cardUpgrade.visibility = View.VISIBLE
        } else {
            cardUpgrade.visibility = View.GONE
        }
        
        if (tier != "enterprise") {
            tabThreat.visibility = View.GONE
            if (isThreatTabActive) switchTab(false)
        } else {
            tabThreat.visibility = View.VISIBLE
        }

        val updateStaged = Updater.isUpdateStaged(this)
        btnUpdateReady.visibility = if (updateStaged) View.VISIBLE else View.GONE
        txtFooterDot2.visibility = if (updateStaged) View.VISIBLE else View.GONE

        renderLogPanel()

        val lockPrefix = if (lockOn) "[LOCK] " else ""
        val dnsSuffix = if (!customDns.isNullOrBlank()) " · DNS: $customDns" else ""

        when {
            !hasConfig -> {
                applyState(
                    text = "NOT SET UP",
                    subtext = "Tap connect to start",
                    wordColour = Color.parseColor("#ff6b6b"),
                    dotColour = Color.parseColor("#ff6b6b"),
                    buttonText = "CONNECT"
                )
                cardMeta.visibility = View.GONE
            }
            connecting -> {
                applyState(
                    text = "CONNECTING",
                    subtext = lockPrefix + "Establishing secure tunnel..." + dnsSuffix,
                    wordColour = Color.parseColor("#f5b342"),
                    dotColour = Color.parseColor("#f5b342"),
                    buttonText = "DISCONNECT"
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
            running -> {
                val networkType = Stats.networkType
                val networkPresent = networkType != "NONE"

                if (networkPresent) {
                    val sub = when (networkType) {
                        "WIFI" -> "Secured · WiFi"
                        "CELLULAR" -> "Secured · Cellular"
                        "ETHERNET" -> "Secured · Ethernet"
                        else -> "Secured · Connected"
                    }
                    applyState(
                        text = "SECURED",
                        subtext = lockPrefix + sub + dnsSuffix,
                        wordColour = Color.parseColor("#f5c842"),
                        dotColour = Color.parseColor("#4cd9b2"),
                        buttonText = "DISCONNECT"
                    )
                } else {
                    applyState(
                        text = "STANDING BY",
                        subtext = lockPrefix + "Tunnel open · No network" + dnsSuffix,
                        wordColour = Color.parseColor("#f5b342"),
                        dotColour = Color.parseColor("#f5b342"),
                        buttonText = "DISCONNECT"
                    )
                }
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = true)
            }
            else -> {
                applyState(
                    text = "DISCONNECTED",
                    subtext = lockPrefix + "Tap connect to start your tunnel" + dnsSuffix,
                    wordColour = Color.parseColor("#8a9bb0"),
                    dotColour = Color.parseColor("#ff6b6b"),
                    buttonText = "CONNECT"
                )
                cardMeta.visibility = View.VISIBLE
                updateMeta(running = false)
            }
        }
    }

    private fun applyState(text: String, subtext: String, wordColour: Int, dotColour: Int, buttonText: String) {
        txtState.text = text
        txtState.setTextColor(wordColour)
        txtSubstate.text = subtext
        dotStatus.background?.setTint(dotColour)
        btnToggle.text = buttonText
        btnToggle.isEnabled = true
    }

    private fun updateMeta(running: Boolean) {
        val displayFlag = ConfigStore.getServerFlag(this)
        val displayEndpoint = ConfigStore.getEndpoint(this)
        txtEndpoint.text = "$displayFlag  $displayEndpoint"

        if (running) {
            val startedAt = ConfigStore.getTunnelStartedAt(this)
            txtDuration.text = if (startedAt > 0L) formatDuration(System.currentTimeMillis() - startedAt) else "--:--"
            txtData.text = formatData(Stats.rx, Stats.tx)
        } else {
            txtDuration.text = "--:--"
            txtData.text = "↓0.0 ↑0.0 MB"
        }

        val expires = ConfigStore.getSubscriptionExpiresAt(this)
        if (expires.isNullOrEmpty()) {
            txtEntitlement.text = "Active"
            txtEntitlement.setTextColor(Color.parseColor("#4cd9b2"))
        } else {
            txtEntitlement.text = "Expires $expires"
            txtEntitlement.setTextColor(Color.parseColor("#e8edf5"))
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

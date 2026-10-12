package com.ttvpn

import android.content.Context

object ConfigStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)

    fun saveConfig(
        context: Context,
        wgConfig: String,
        assignedIp: String,
        subscriptionExpiresAt: String?
    ) {
        prefs(context).edit()
            .putString(Config.PREF_WG_CONFIG, wgConfig)
            .putString(Config.PREF_WG_CONFIG_ORIGINAL, wgConfig)
            .putString(Config.PREF_ASSIGNED_IP, assignedIp)
            .putString(Config.PREF_SUBSCRIPTION_EXPIRES, subscriptionExpiresAt)
            .putLong(Config.PREF_CONFIGURED_AT, System.currentTimeMillis())
            .remove(Config.PREF_CUSTOM_DNS)
            .apply()
    }

    fun getConfig(context: Context): String? =
        prefs(context).getString(Config.PREF_WG_CONFIG, null)

    fun getOriginalConfig(context: Context): String? =
        prefs(context).getString(Config.PREF_WG_CONFIG_ORIGINAL, null)

    fun getAssignedIp(context: Context): String? =
        prefs(context).getString(Config.PREF_ASSIGNED_IP, null)

    fun getSubscriptionExpiresAt(context: Context): String? =
        prefs(context).getString(Config.PREF_SUBSCRIPTION_EXPIRES, null)

    fun hasConfig(context: Context): Boolean = getConfig(context) != null

    fun setVpnRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(Config.PREF_VPN_RUNNING, running).apply()
    }

    fun isVpnRunning(context: Context): Boolean =
        prefs(context).getBoolean(Config.PREF_VPN_RUNNING, false)

    fun setTunnelStartedAt(context: Context, ts: Long) {
        prefs(context).edit().putLong(Config.PREF_TUNNEL_STARTED_AT, ts).apply()
    }

    fun getTunnelStartedAt(context: Context): Long =
        prefs(context).getLong(Config.PREF_TUNNEL_STARTED_AT, 0L)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(Config.PREF_LOCK_ENABLED, enabled).apply()
    }

    fun isLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(Config.PREF_LOCK_ENABLED, false)

    fun getCustomDns(context: Context): String? =
        prefs(context).getString(Config.PREF_CUSTOM_DNS, null)

    fun hasCustomDns(context: Context): Boolean =
        !getCustomDns(context).isNullOrBlank()

    /**
     * Set (or clear) the custom DNS override. Rewrites the stored WireGuard
     * config in place. Returns true on success.
     *
     * - If [dns] is null or blank, restores the original server-issued config
     *   and clears the custom DNS preference.
     * - Otherwise, rewrites the DNS line in the original config with [dns].
     */
    fun setCustomDns(context: Context, dns: String?): Boolean {
        val original = getOriginalConfig(context)
            ?: getConfig(context)
            ?: return false

        if (getOriginalConfig(context) == null) {
            prefs(context).edit()
                .putString(Config.PREF_WG_CONFIG_ORIGINAL, original)
                .apply()
        }

        val normalised = dns?.trim()?.takeIf { it.isNotEmpty() }

        val newConfig = if (normalised == null) {
            prefs(context).edit().remove(Config.PREF_CUSTOM_DNS).apply()
            original
        } else {
            prefs(context).edit().putString(Config.PREF_CUSTOM_DNS, normalised).apply()
            rewriteDns(original, normalised)
        }

        prefs(context).edit()
            .putString(Config.PREF_WG_CONFIG, newConfig)
            .apply()

        return true
    }

    private fun rewriteDns(config: String, dns: String): String {
        val lines = config.lines().toMutableList()
        var replaced = false

        for (i in lines.indices) {
            val trimmed = lines[i].trimStart()
            if (trimmed.startsWith("DNS") && trimmed.contains("=")) {
                lines[i] = "DNS = $dns"
                replaced = true
                break
            }
        }

        if (!replaced) {
            val addrIndex = lines.indexOfFirst {
                it.trimStart().startsWith("Address") && it.contains("=")
            }
            if (addrIndex >= 0) {
                lines.add(addrIndex + 1, "DNS = $dns")
            } else {
                val ifaceIndex = lines.indexOfFirst { it.trim() == "[Interface]" }
                if (ifaceIndex >= 0) {
                    lines.add(ifaceIndex + 1, "DNS = $dns")
                }
            }
        }

        return lines.joinToString("\n")
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun setLogEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(Config.PREF_LOG_ENABLED, enabled).apply()
    }

    fun isLogEnabled(context: Context): Boolean =
        prefs(context).getBoolean(Config.PREF_LOG_ENABLED, true)
}

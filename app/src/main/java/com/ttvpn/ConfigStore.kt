package com.ttvpn

import android.content.Context

object ConfigStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)

    // --- Auth (JWT) ---
    fun saveAuth(context: Context, token: String, tier: String) {
        prefs(context).edit()
            .putString(Config.PREF_AUTH_TOKEN, token)
            .putString(Config.PREF_USER_TIER, tier)
            .apply()
    }

    fun getAuthToken(context: Context): String? =
        prefs(context).getString(Config.PREF_AUTH_TOKEN, null)

    fun getUserTier(context: Context): String =
        prefs(context).getString(Config.PREF_USER_TIER, "free") ?: "free"

    fun clearAuth(context: Context) {
        prefs(context).edit()
            .remove(Config.PREF_AUTH_TOKEN)
            .remove(Config.PREF_USER_TIER)
            .apply()
    }

    // --- Enterprise Voucher Token ---
    fun saveVoucherCode(context: Context, code: String) {
        prefs(context).edit().putString(Config.PREF_VOUCHER_CODE, code).apply()
    }

    fun getVoucherCode(context: Context): String? =
        prefs(context).getString(Config.PREF_VOUCHER_CODE, null)

    // --- VPN Config ---
    fun saveConfig(
        context: Context,
        wgConfig: String,
        assignedIp: String,
        subscriptionExpiresAt: String?
    ) {
        var endpoint = Config.WG_ENDPOINT_DISPLAY
        var flag = Config.WG_SERVER_FLAG
        var country = Config.WG_SERVER_COUNTRY

        wgConfig.lines().forEach { line ->
            if (line.trimStart().startsWith("Endpoint") && line.contains("=")) {
                endpoint = line.substringAfter("=").trim()
                if (endpoint != "154.66.198.207:51820") {
                    flag = "🇪🇺"
                    country = "Europe"
                }
            }
        }

        prefs(context).edit()
            .putString(Config.PREF_WG_CONFIG, wgConfig)
            .putString(Config.PREF_WG_CONFIG_ORIGINAL, wgConfig)
            .putString(Config.PREF_ASSIGNED_IP, assignedIp)
            .putString(Config.PREF_SUBSCRIPTION_EXPIRES, subscriptionExpiresAt)
            .putString(Config.PREF_WG_ENDPOINT, endpoint)
            .putString(Config.PREF_WG_SERVER_FLAG, flag)
            .putString(Config.PREF_WG_SERVER_COUNTRY, country)
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

    fun getEndpoint(context: Context): String =
        prefs(context).getString(Config.PREF_WG_ENDPOINT, Config.WG_ENDPOINT_DISPLAY) ?: Config.WG_ENDPOINT_DISPLAY

    fun getServerFlag(context: Context): String =
        prefs(context).getString(Config.PREF_WG_SERVER_FLAG, Config.WG_SERVER_FLAG) ?: Config.WG_SERVER_FLAG

    fun getServerCountry(context: Context): String =
        prefs(context).getString(Config.PREF_WG_SERVER_COUNTRY, Config.WG_SERVER_COUNTRY) ?: Config.WG_SERVER_COUNTRY

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

    fun setCustomDns(context: Context, dns: String?): Boolean {
        val original = getOriginalConfig(context) ?: getConfig(context) ?: return false

        if (getOriginalConfig(context) == null) {
            prefs(context).edit().putString(Config.PREF_WG_CONFIG_ORIGINAL, original).apply()
        }

        val normalised = dns?.trim()?.takeIf { it.isNotEmpty() }

        val newConfig = if (normalised == null) {
            prefs(context).edit().remove(Config.PREF_CUSTOM_DNS).apply()
            original
        } else {
            prefs(context).edit().putString(Config.PREF_CUSTOM_DNS, normalised).apply()
            rewriteDns(original, normalised)
        }

        prefs(context).edit().putString(Config.PREF_WG_CONFIG, newConfig).apply()
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
            val addrIndex = lines.indexOfFirst { it.trimStart().startsWith("Address") && it.contains("=") }
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

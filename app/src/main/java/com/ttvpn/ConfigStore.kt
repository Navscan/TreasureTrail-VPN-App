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
            .putString(Config.PREF_ASSIGNED_IP, assignedIp)
            .putString(Config.PREF_SUBSCRIPTION_EXPIRES, subscriptionExpiresAt)
            .putLong(Config.PREF_CONFIGURED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getConfig(context: Context): String? =
        prefs(context).getString(Config.PREF_WG_CONFIG, null)

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

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

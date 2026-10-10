package com.ttvpn

object Config {
    const val BASE_URL = "https://treasuretrail.africa/ttvpn"
    const val ENDPOINT_REGISTER = "$BASE_URL/api/register"
    const val ENDPOINT_HEALTH = "$BASE_URL/api/health"

    const val WG_ENDPOINT_DISPLAY = "154.66.198.207:51820"

    const val HTTP_CONNECT_TIMEOUT_MS = 10_000
    const val HTTP_READ_TIMEOUT_MS = 20_000

    const val PREFS_NAME = "ttvpn_prefs"
    const val PREF_WG_CONFIG = "wg_config"
    const val PREF_ASSIGNED_IP = "assigned_ip"
    const val PREF_CONFIGURED_AT = "configured_at"
    const val PREF_SUBSCRIPTION_EXPIRES = "subscription_expires"
    const val PREF_VPN_RUNNING = "vpn_running"
    const val PREF_TUNNEL_STARTED_AT = "tunnel_started_at"

    const val TUNNEL_NAME = "TTVPN"
    const val NOTIFICATION_CHANNEL_ID = "vpn"
    const val NOTIFICATION_ID = 1

    const val STATE_POLL_INTERVAL_MS = 1_000L
    const val STATS_POLL_INTERVAL_MS = 1_000L
}

package com.ttvpn

object Config {
    const val BASE_URL = "https://treasuretrail.africa/ttvpn"
    const val ENDPOINT_REGISTER = "$BASE_URL/api/register"
    const val ENDPOINT_HEALTH = "$BASE_URL/api/health"

    const val WG_ENDPOINT_DISPLAY = "154.66.198.207:51820"
    const val WG_SERVER_FLAG = "🇿🇦"
    const val WG_SERVER_COUNTRY = "South Africa"

    const val HTTP_CONNECT_TIMEOUT_MS = 10_000
    const val HTTP_READ_TIMEOUT_MS = 20_000

    const val PREFS_NAME = "ttvpn_prefs"
    const val PREF_WG_CONFIG = "wg_config"
    const val PREF_WG_CONFIG_ORIGINAL = "wg_config_original"
    const val PREF_ASSIGNED_IP = "assigned_ip"
    const val PREF_CONFIGURED_AT = "configured_at"
    const val PREF_SUBSCRIPTION_EXPIRES = "subscription_expires"
    const val PREF_VPN_RUNNING = "vpn_running"
    const val PREF_TUNNEL_STARTED_AT = "tunnel_started_at"
    const val PREF_LOCK_ENABLED = "lock_enabled"
    const val PREF_CUSTOM_DNS = "custom_dns"

    const val TUNNEL_NAME = "TTVPN"
    const val NOTIFICATION_CHANNEL_ID = "vpn"
    const val NOTIFICATION_ID = 1

    const val STATE_POLL_INTERVAL_MS = 1_000L
    const val STATS_POLL_INTERVAL_MS = 1_000L
    const val RECONNECT_DELAY_MS = 1_500L
    const val DNS_RESTART_DELAY_MS = 800L

    // -------- in-app auto-updater --------
    const val GITHUB_API_LATEST_RELEASE =
        "https://api.github.com/repos/Navscan/TreasureTrail-VPN-App/releases/latest"
    const val UPDATER_USER_AGENT = "TTVPN-Android/1.4"
    const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    const val PREF_UPDATE_CHECKED_AT = "update_checked_at"
    const val PREF_STAGED_UPDATE_URL = "staged_update_url"
    const val PREF_STAGED_UPDATE_PATH = "staged_update_path"
    const val PREF_STAGED_UPDATE_VERSION = "staged_update_version"
    const val UPDATER_SUBDIR = "updates"
    const val UPDATER_APK_NAME = "TTVPN.apk"
    const val UPDATER_FILEPROVIDER_AUTHORITY = "com.ttvpn.fileprovider"
}

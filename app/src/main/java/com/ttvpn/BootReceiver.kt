package com.ttvpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ConfigStore.isLockEnabled(context)) return
        if (!ConfigStore.hasConfig(context)) return

        // If consent has been granted previously, prepare() returns null.
        // If not, the user must open the app once — we cannot force a
        // consent dialog from a boot receiver.
        val prepareIntent = android.net.VpnService.prepare(context)
        if (prepareIntent != null) return

        VpnService.start(context)
    }
}

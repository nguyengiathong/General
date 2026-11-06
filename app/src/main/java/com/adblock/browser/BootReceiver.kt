package com.adblock.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/** Tự bật lại chặn quảng cáo sau khi khởi động máy nếu trước đó đang bật. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("vpn_enabled", false)
        if (enabled && VpnService.prepare(context) == null) {
            AdBlockVpnService.start(context)
        }
    }
}

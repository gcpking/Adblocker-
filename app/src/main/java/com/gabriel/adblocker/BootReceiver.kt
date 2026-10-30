package com.gabriel.adblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.wanted(context) && VpnService.prepare(context) == null) {
            context.startService(Intent(context, BlockerService::class.java))
        }
    }
}

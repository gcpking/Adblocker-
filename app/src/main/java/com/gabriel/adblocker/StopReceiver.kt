package com.gabriel.adblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startService(Intent(context, BlockerService::class.java).setAction(BlockerService.ACTION_STOP))
    }
}

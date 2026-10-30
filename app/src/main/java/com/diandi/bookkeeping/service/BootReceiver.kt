package com.diandi.bookkeeping.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.diandi.bookkeeping.util.AppPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AppPrefs.isNotifyEnabled(context)) {
                KeepAliveService.start(context.applicationContext)
            }
        }
    }
}

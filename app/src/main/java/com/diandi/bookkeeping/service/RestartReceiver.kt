package com.diandi.bookkeeping.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.diandi.bookkeeping.util.AppPrefs

/** 被划掉 / Alarm 唤醒后重新拉起常驻通知服务 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!AppPrefs.isNotifyEnabled(context)) return
        KeepAliveService.start(context.applicationContext)
    }

    companion object {
        const val ACTION_RESTART = "com.diandi.bookkeeping.action.RESTART_NOTIFY"
    }
}

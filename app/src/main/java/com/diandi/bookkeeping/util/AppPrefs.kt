package com.diandi.bookkeeping.util

import android.content.Context

/** 应用偏好（多进程可读：MODE_MULTI_PROCESS 已废弃，改用同一文件名 + 通知进程只读） */
object AppPrefs {
    private const val NAME = "diandi_prefs"
    private const val KEY_NOTIFY = "keepalive_notify_enabled"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 默认开启常驻通知 */
    fun isNotifyEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_NOTIFY, true)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_NOTIFY, enabled).apply()
    }
}

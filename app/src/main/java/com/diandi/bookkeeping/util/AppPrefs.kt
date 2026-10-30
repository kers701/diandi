package com.diandi.bookkeeping.util

import android.content.Context
import java.io.File

/** 多进程安全：通知在 :notify 进程，用 filesDir 标记文件共享开关状态 */
object AppPrefs {
    private const val FILE_NOTIFY = "keepalive_notify_enabled"

    private fun flagFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NOTIFY)

    fun isNotifyEnabled(context: Context): Boolean {
        val f = flagFile(context)
        if (!f.exists()) return true
        return f.readText().trim() != "0"
    }

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        flagFile(context).writeText(if (enabled) "1" else "0")
    }
}

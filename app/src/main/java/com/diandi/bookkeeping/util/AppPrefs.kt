package com.diandi.bookkeeping.util

import android.content.Context
import java.io.File

/** 多进程安全：通知在 :notify 进程，用 filesDir 标记文件共享开关状态 */
object AppPrefs {
    private const val FILE_NOTIFY = "keepalive_notify_enabled"
    private const val FILE_AUTO_BILL = "auto_bill_enabled"
    private const val FILE_AUTO_BILL_SEEN = "auto_bill_seen"
    private const val MAX_SEEN = 240

    private fun flagFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NOTIFY)
    private fun autoBillFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_AUTO_BILL)
    private fun seenFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_AUTO_BILL_SEEN)

    fun isNotifyEnabled(context: Context): Boolean {
        val f = flagFile(context)
        if (!f.exists()) return true
        return f.readText().trim() != "0"
    }

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        flagFile(context).writeText(if (enabled) "1" else "0")
    }

    fun isAutoBillEnabled(context: Context): Boolean =
        autoBillFile(context).takeIf { it.exists() }?.readText()?.trim() == "1"

    fun setAutoBillEnabled(context: Context, enabled: Boolean) {
        autoBillFile(context).writeText(if (enabled) "1" else "0")
    }

    @Synchronized
    fun rememberAutoBillNotification(context: Context, fingerprint: String): Boolean {
        val file = seenFile(context)
        val now = System.currentTimeMillis()
        val entries = file.takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { line ->
                val split = line.indexOf('|')
                if (split <= 0) return@mapNotNull null
                val timestamp = line.substring(0, split).toLongOrNull() ?: return@mapNotNull null
                timestamp to line.substring(split + 1)
            }
            ?.filter { now - it.first < 7 * 24 * 60 * 60 * 1000L }
            ?.toMutableList()
            ?: mutableListOf()
        if (entries.any { it.second == fingerprint }) return false
        entries += now to fingerprint
        file.writeText(entries.takeLast(MAX_SEEN).joinToString("\n") { "${it.first}|${it.second}" })
        return true
    }
}

package com.diandi.bookkeeping.util

import android.content.Context
import java.io.File

/** 多进程安全：通知在 :notify 进程，用 filesDir 标记文件共享开关状态 */
object AppPrefs {
    private const val FILE_NOTIFY = "keepalive_notify_enabled"
    private const val FILE_AUTO_BILL = "auto_bill_enabled"
    private const val FILE_AUTO_BILL_SEEN = "auto_bill_seen"
    private const val MAX_SEEN = 240
    private const val DEDUP_WINDOW_MS = 3 * 60 * 1000L

    private data class SeenAutoBill(
        val timestamp: Long,
        val amount: Double,
        val type: String,
        val sourcePackage: String,
        val fingerprint: String
    )

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
    fun rememberAutoBillNotification(
        context: Context,
        fingerprint: String,
        amount: Double,
        type: String,
        sourcePackage: String
    ): Boolean {
        val file = seenFile(context)
        val now = System.currentTimeMillis()
        val entries = file.takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull { line ->
                val fields = line.split('|', limit = 5)
                if (fields.size != 5) return@mapNotNull null
                SeenAutoBill(
                    timestamp = fields[0].toLongOrNull() ?: return@mapNotNull null,
                    amount = fields[1].toDoubleOrNull() ?: return@mapNotNull null,
                    type = fields[2],
                    sourcePackage = fields[3],
                    fingerprint = fields[4]
                )
            }
            ?.filter { now - it.timestamp < 7 * 24 * 60 * 60 * 1000L }
            ?.toMutableList()
            ?: mutableListOf()
        if (entries.any { it.fingerprint == fingerprint }) return false

        // 微信支付和银行卡短信可能分别推送同一笔交易，只保留短时间内先到的一条。
        val sameAmountFromAnotherSource = entries.any {
            now - it.timestamp <= DEDUP_WINDOW_MS &&
                it.amount == amount &&
                it.type == type &&
                it.sourcePackage != sourcePackage
        }
        if (sameAmountFromAnotherSource) return false

        entries += SeenAutoBill(now, amount, type, sourcePackage, fingerprint)
        file.writeText(
            entries.takeLast(MAX_SEEN).joinToString("\n") {
                "${it.timestamp}|${it.amount}|${it.type}|${it.sourcePackage}|${it.fingerprint}"
            }
        )
        return true
    }
}

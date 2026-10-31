package com.diandi.bookkeeping.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.util.AppPrefs
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 读取用户授权的支付/收入通知，并将可识别的金额写入本地账单。
 * 只处理包含明确收支关键词且能解析金额的通知，其他通知一律忽略。
 */
class AutoBillNotificationService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!AppPrefs.isAutoBillEnabled(this) || sbn.packageName == packageName) return

        val content = notificationText(sbn) ?: return
        val parsed = AutoBillParser.parse(content) ?: return
        val fingerprint = AutoBillParser.fingerprint(sbn, content, parsed.amount)
        if (!AppPrefs.rememberAutoBillNotification(this, fingerprint)) return

        serviceScope.launch {
            try {
                val now = LocalDate.now()
                AppDatabase.getInstance(applicationContext).transactionDao().insert(
                    Transaction(
                        amount = parsed.amount,
                        type = parsed.type,
                        category = parsed.category,
                        tags = "自动记账",
                        note = parsed.note,
                        year = now.year,
                        month = now.monthValue,
                        day = now.dayOfMonth
                    )
                )
            } catch (_: Exception) {
                // 单条通知解析或数据库异常不应影响监听服务继续工作。
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel(CancellationException("notification listener stopped"))
        super.onDestroy()
    }

    private fun notificationText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null
        val values = listOf(
            extras.getCharSequence("android.title"),
            extras.getCharSequence("android.text"),
            extras.getCharSequence("android.bigText"),
            extras.getCharSequence("android.subText")
        ).filterNot { TextUtils.isEmpty(it) }
        return values.joinToString(" ").trim().takeIf { it.isNotBlank() }
    }
}

data class ParsedAutoBill(
    val amount: Double,
    val type: String,
    val category: String,
    val note: String
)

object AutoBillParser {
    private val amountRegex = Regex(
        "(?<![\\d.])(?:¥|￥|RMB|人民币)?\\s*([0-9]{1,9}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![\\d.])",
        RegexOption.IGNORE_CASE
    )
    private val expenseKeywords = listOf("消费", "支付", "付款", "扣款", "支出", "已花", "买入")
    private val incomeKeywords = listOf("收款", "收入", "到账", "转入", "入账", "红包", "提现")
    private val paymentKeywords = expenseKeywords + incomeKeywords

    fun parse(content: String): ParsedAutoBill? {
        val text = content.replace('\n', ' ').trim()
        val lower = text.lowercase(Locale.ROOT)
        if (paymentKeywords.none { text.contains(it) } && !lower.contains("payment") && !lower.contains("received")) {
            return null
        }
        val amount = amountRegex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() }
            .firstOrNull { it > 0.0 && it < 100_000_000.0 }
            ?: return null

        val isIncome = incomeKeywords.any { text.contains(it) } ||
            lower.contains("received") || lower.contains("income")
        val type = if (isIncome) "INCOME" else "EXPENSE"
        val category = inferCategory(text, isIncome)
        return ParsedAutoBill(
            amount = amount,
            type = type,
            category = category,
            note = "通知自动记账：${text.take(160)}"
        )
    }

    fun fingerprint(sbn: StatusBarNotification, content: String, amount: Double): String {
        val stableKey = sbn.key.ifBlank { "${sbn.packageName}:${sbn.id}" }
        return "$stableKey|${amount}|${content.take(240)}"
    }

    private fun inferCategory(text: String, isIncome: Boolean): String {
        if (isIncome) {
            return when {
                text.contains("工资") || text.contains("薪资") -> "工资"
                text.contains("红包") -> "红包"
                text.contains("转账") || text.contains("转入") -> "转账"
                else -> "其他收入"
            }
        }
        return when {
            listOf("餐饮", "吃饭", "美团", "饿了么", "外卖", "餐厅").any { text.contains(it) } -> "餐饮"
            listOf("淘宝", "天猫", "京东", "购物", "商品", "拼多多").any { text.contains(it) } -> "购物"
            listOf("滴滴", "打车", "地铁", "公交", "交通", "加油").any { text.contains(it) } -> "交通"
            listOf("水费", "电费", "燃气", "话费", "充值").any { text.contains(it) } -> "生活缴费"
            else -> "其他支出"
        }
    }
}

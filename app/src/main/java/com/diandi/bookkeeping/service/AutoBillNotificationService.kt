package com.diandi.bookkeeping.service

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.util.Categories
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.math.abs
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
        if (!AppPrefs.isAutoBillEnabled(this) ||
            sbn.packageName == packageName ||
            !AppPrefs.isAutoBillAppAllowed(this, sbn.packageName)
        ) return

        val content = notificationText(sbn) ?: return
        val parsed = AutoBillParser.parse(content, this) ?: return
        val fingerprint = AutoBillParser.fingerprint(sbn, content, parsed.amount)
        if (!AppPrefs.rememberAutoBillNotification(
                this,
                fingerprint,
                parsed.amount,
                parsed.type,
                sbn.packageName
            )
        ) return

        serviceScope.launch {
            try {
                val now = LocalDate.now()
                val insertedId = AppDatabase.getInstance(applicationContext).transactionDao().insert(
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
                if (insertedId > 0L) AiAnalysisScheduler.onBillInserted(applicationContext)
                val amountText = if (parsed.amount == parsed.amount.toLong().toDouble()) {
                    parsed.amount.toLong().toString()
                } else {
                    String.format(Locale.CHINA, "%.2f", parsed.amount)
                }
                val msg = "已自动记账，金额" + amountText
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
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
    /** 优先匹配「数字+元」，避免把时间、卡号当成金额 */
    private val yuanAmountRegex = Regex(
        "(?<![\\d.])([0-9]{1,9}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元"
    )
    private val currencyAmountRegex = Regex(
        "(?:¥|￥|RMB|人民币)\\s*([0-9]{1,9}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    /**
     * 账户间互转 / 充值提现（不改变真实消费，不记账）。
     * 例：银行卡支出(充值财付通-微信零钱充值账户)
     */
    private val skipTransferHints = listOf(
        "充值财付通", "财付通-微信", "微信零钱充值", "零钱充值账户", "零钱充值",
        "充值到微信", "充值到支付宝", "充值支付宝", "支付宝充值", "微信充值",
        "转入零钱", "转入微信", "转入支付宝", "转到零钱", "转到微信",
        "余额宝转入", "余额宝转出", "理财转入", "理财转出",
        "信用卡还款", "还信用卡", "还款成功",
        "本人账户", "关联账户", "资金划转", "内部转账",
        "快捷支付充值", "支付账户充值"
    )

    fun parse(content: String, context: android.content.Context): ParsedAutoBill? {
        val text = normalizeNotificationText(content)
        val lower = text.lowercase(Locale.ROOT)
        val expenseKeywords = AutoBillKeywords.expense + AppPrefs.getAutoBillAllowedKeywords(context)
        val incomeKeywords = AutoBillKeywords.income
        val paymentKeywords = expenseKeywords + incomeKeywords
        val blockedKeywords = AutoBillKeywords.noise + AppPrefs.getAutoBillBlockedKeywords(context)

        if (blockedKeywords.any { text.contains(it, ignoreCase = true) }) return null
        // 银行卡↔微信/支付宝等互转、充值提现：不记收支
        if (skipTransferHints.any { text.contains(it, ignoreCase = true) }) return null
        if (text.length > 220) return null
        if (paymentKeywords.none { text.contains(it, ignoreCase = true) } &&
            !lower.contains("payment") && !lower.contains("received")
        ) {
            return null
        }

        val amount = pickAmount(text, paymentKeywords) ?: return null

        val isIncome = incomeKeywords.any { text.contains(it, ignoreCase = true) } ||
            lower.contains("received") || lower.contains("income")
        // 「支出」优先于模糊的「转入」等（银行短信里支出括号内常有充值描述）
        val isExpenseStrong = AutoBillKeywords.expense.any { text.contains(it) }
        val type = when {
            isExpenseStrong && !incomeKeywords.any { text.contains(it) } -> "EXPENSE"
            isIncome && !isExpenseStrong -> "INCOME"
            isIncome -> "INCOME"
            else -> "EXPENSE"
        }
        val category = inferCategory(context, text, type == "INCOME")
        return ParsedAutoBill(
            amount = amount,
            type = type,
            category = category,
            note = "通知自动记账：${text.take(160)}"
        )
    }

    /**
     * 金额选取规则：
     * 1. 只要带「元」或货币符号的数字
     * 2. 排除「余额」「可用余额」「账户余额」后的数字
     * 3. 排除像时间（03:21）、日期、尾号卡号中的片段（无「元」时本来就不会进候选）
     * 4. 优先靠近「支出/收入/消费…」且带「元」的金额
     */
    private fun pickAmount(text: String, paymentKeywords: List<String>): Double? {
        data class Cand(val value: Double, val start: Int, val end: Int, val score: Int)

        val cands = mutableListOf<Cand>()
        fun addMatch(match: MatchResult, base: Int) {
            val raw = match.groupValues.getOrNull(1)?.replace(",", "") ?: return
            val value = raw.toDoubleOrNull() ?: return
            if (value <= 0.0 || value >= 100_000_000.0) return
            val start = match.range.first
            val end = match.range.last
            // 落在「余额」关键词之后的一小段 → 账户余额，不是交易额
            if (isAfterBalanceKeyword(text, start)) return
            // 像 03:21 里的数字：前一位是冒号或后一位是冒号
            if (looksLikeTimeFragment(text, match.range, raw)) return

            var score = base
            val nearbyStart = (start - 36).coerceAtLeast(0)
            val nearbyEnd = (end + 8).coerceAtMost(text.length)
            val nearby = text.substring(nearbyStart, nearbyEnd)
            val distance = paymentKeywords.mapNotNull { keyword ->
                val index = text.indexOf(keyword, nearbyStart, ignoreCase = true)
                if (index in nearbyStart until nearbyEnd) abs(index - start) else null
            }.minOrNull()
            if (distance != null) score += 40 - distance.coerceAtMost(40)
            // 紧跟在 支出/消费 等后面的「xx元」加分
            if (Regex("""(?:支出|消费|支付|付款|扣款|收入|到账|收款)[^元]{0,48}$""").containsMatchIn(
                    text.substring(0, start.coerceAtMost(text.length))
                )
            ) {
                score += 25
            }
            cands += Cand(value, start, end, score)
        }

        yuanAmountRegex.findAll(text).forEach { addMatch(it, 50) }
        currencyAmountRegex.findAll(text).forEach { addMatch(it, 45) }

        return cands.maxByOrNull { it.score }?.value
    }

    private fun isAfterBalanceKeyword(text: String, amountStart: Int): Boolean {
        val prefix = text.substring(0, amountStart)
        val keys = listOf("余额", "可用余额", "账户余额", "卡内余额", "活期余额")
        for (k in keys) {
            val i = prefix.lastIndexOf(k)
            if (i >= 0 && amountStart - i <= 12) return true
        }
        return false
    }

    private fun looksLikeTimeFragment(text: String, range: IntRange, raw: String): Boolean {
        // 仅纯整数且长度 1–2 时检查是否落在 HH:MM
        if (raw.contains('.') || raw.length > 2) return false
        val before = if (range.first > 0) text[range.first - 1] else ' '
        val after = if (range.last + 1 < text.length) text[range.last + 1] else ' '
        if (before == ':' || after == ':') return true
        // 11月1日 中的日、月数字无「元」不会进入 yuan 候选；此处兜底
        if (after == '月' || after == '日' || before == '月') return true
        return false
    }

    fun fingerprint(sbn: StatusBarNotification, content: String, amount: Double): String {
        val stableKey = sbn.key.ifBlank { "${sbn.packageName}:${sbn.id}" }
        return "$stableKey|${amount}|${content.take(240)}"
    }

    private fun inferCategory(
        context: android.content.Context,
        text: String,
        isIncome: Boolean
    ): String {
        val categories = if (isIncome) {
            Categories.income(context)
        } else {
            Categories.expense(context)
        }
        categories.firstOrNull { text.contains(it, ignoreCase = true) }?.let { return it }

        val fallback = if (isIncome) {
            when {
                text.contains("工资") || text.contains("薪资") -> "工资"
                text.contains("红包") -> "红包"
                text.contains("转账") || text.contains("转入") -> "转账"
                else -> "其他收入"
            }
        } else {
            when {
                listOf("餐饮", "吃饭", "美团", "饿了么", "外卖", "餐厅").any { text.contains(it) } -> "餐饮"
                listOf("淘宝", "天猫", "京东", "购物", "商品", "拼多多").any { text.contains(it) } -> "购物"
                listOf("滴滴", "打车", "地铁", "公交", "交通", "加油").any { text.contains(it) } -> "交通"
                listOf("水费", "电费", "燃气", "话费").any { text.contains(it) } -> "生活缴费"
                else -> "其他支出"
            }
        }

        return fallback.takeIf { it in categories }
            ?: categories.firstOrNull()
            ?: fallback
    }

    private fun normalizeNotificationText(content: String): String {
        val countBadge = Regex("""[\[【(（]\s*\d+\s*条\s*[\]】)）]""")
        val duplicateTitle = Regex("""^(.{1,24}?)\s+\1(?=\s*[:：])""")
        val withoutCount = content
            .replace('\n', ' ')
            .replace(countBadge, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return duplicateTitle.replaceFirst(withoutCount, "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

object AutoBillKeywords {
    val expense = listOf("消费", "支付", "付款", "扣款", "支出", "已花", "买入")
    val income = listOf("收款", "收入", "到账", "转入", "入账", "红包", "提现")
    val noise = listOf(
        "活动", "优惠", "广告", "推广", "新闻", "资讯", "文章", "法院", "案例",
        "拒绝", "返还", "概不负责", "点击", "详情", "领取", "中奖", "抽奖", "招聘",
        "报名", "分享", "推送", "促销", "贷款广告"
    )
}

package com.diandi.bookkeeping.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * 金额工具：**所有金额一律以「分」为单位的 Long 存储与计算**。
 *
 * 为什么不用 Double：0.1 + 0.2 != 0.3，多笔小数累加、按权重分配余额时会
 * 逐步产生误差，对记账应用是不可接受的。现在只在三个边界做换算：
 * - 用户输入（[parseToCents]）
 * - 界面展示（[format] / [formatYuan]）
 * - 备份 JSON / AI 提示词（[toYuanString]）
 *
 * 数据库里旧数据是 REAL 元，迁移时统一 ROUND(amount * 100) 转成分。
 */
object Money {

    /** 输入框文本 → 分；非法输入返回 null。支持负号、¥/￥、千分位、小数最多两位。 */
    fun parseToCents(raw: String): Long? {
        val text = raw.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
            .replace("，", "")
        if (text.isEmpty()) return null
        if (text == "-" || text == "+" || text == "." || text == "-.") return null
        val value = text.toBigDecimalOrNull() ?: return null
        return try {
            value.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** 元（Double，来自旧数据/图表）→ 分，四舍五入到分。 */
    fun fromYuan(amount: Double): Long =
        if (amount.isNaN() || amount.isInfinite()) 0L
        else BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValue()

    /** 分 → 元（只在图表坐标等需要 Double 的地方使用）。 */
    fun toYuan(cents: Long): Double = cents / 100.0

    /** 1234 → "12.34"；-1234 → "-12.34"；支持千分位。 */
    fun format(cents: Long, grouping: Boolean = true): String {
        val negative = cents < 0
        val absCents = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else abs(cents)
        val yuanPart = absCents / 100
        val centPart = absCents % 100
        val yuanText = if (grouping) groupDigits(yuanPart) else yuanPart.toString()
        return buildString {
            if (negative) append('-')
            append(yuanText)
            append('.')
            append(centPart.toString().padStart(2, '0'))
        }
    }

    /** 1234 → "¥12.34" */
    fun formatYuan(cents: Long, grouping: Boolean = true): String = "¥" + format(cents, grouping)

    /** 1234 → "+¥12.34"；-1234 → "-¥12.34" */
    fun formatSignedYuan(cents: Long, grouping: Boolean = true): String =
        if (cents > 0) "+" + formatYuan(cents, grouping) else formatYuan(cents, grouping)

    /** 输入框回显用：1234 → "12.34"；0 显示空串（便于直接输入） */
    fun toInputText(cents: Long): String = if (cents == 0L) "" else format(cents, grouping = false)

    /** 占比 [0, 1]；总额 <= 0 时返回 0 */
    fun ratio(usedCents: Long, totalCents: Long): Double =
        if (totalCents <= 0L) 0.0 else usedCents.toDouble() / totalCents.toDouble()

    /** 用于备份 JSON / AI 提示词的「元」字符串，保留两位小数。 */
    fun toYuanString(cents: Long): String = format(cents, grouping = false)

    /** 千分位分组；负数已在外层处理 */
    private fun groupDigits(value: Long): String {
        val raw = value.toString()
        if (raw.length <= 3) return raw
        val out = StringBuilder()
        var count = 0
        for (i in raw.length - 1 downTo 0) {
            out.append(raw[i])
            count++
            if (count % 3 == 0 && i != 0) out.append(',')
        }
        return out.reverse().toString()
    }
}

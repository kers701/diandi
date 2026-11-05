package com.diandi.bookkeeping.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * 资金月周期：从每月 startDay 开始，到次月 startDay 前一天结束。
 *
 * 为什么要自定义：多数公司的发薪日不是 1 号（常见 10/15/20 号），
 * 按自然月统计预算会把"月中发工资前的那笔开销"拆到两个周期里。
 *
 * startDay 限制在 1–28：29/30/31 在部分月份不存在，容易产生歧义。
 */
data class BillingCycle(val start: LocalDate, val end: LocalDate) {
    /** 例：10月15日–11月14日 */
    val label: String
        get() = "${start.monthValue}月${start.dayOfMonth}日–${end.monthValue}月${end.dayOfMonth}日"

    val startDate: String get() = start.toString()
    val endDate: String get() = end.toString()

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

object BillingCycleCalculator {
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28

    fun clampStartDay(day: Int): Int = day.coerceIn(MIN_START_DAY, MAX_START_DAY)

    /** 今天所在的周期 */
    fun of(today: LocalDate, startDay: Int): BillingCycle {
        val day = clampStartDay(startDay)
        val startMonth = if (today.dayOfMonth >= day) {
            YearMonth.from(today)
        } else {
            YearMonth.from(today).minusMonths(1)
        }
        return cycleOf(startMonth, day)
    }

    /** 上一个周期（用于"参考上个周期实际支出"） */
    fun previous(today: LocalDate, startDay: Int): BillingCycle {
        val day = clampStartDay(startDay)
        val currentStart = of(today, startDay).start
        val startMonth = YearMonth.from(currentStart).minusMonths(1)
        return cycleOf(startMonth, day)
    }

    private fun cycleOf(startMonth: YearMonth, day: Int): BillingCycle {
        val start = startMonth.atDay(day.coerceAtMost(startMonth.lengthOfMonth()))
        val nextStartMonth = startMonth.plusMonths(1)
        val nextStart = nextStartMonth.atDay(day.coerceAtMost(nextStartMonth.lengthOfMonth()))
        return BillingCycle(start, nextStart.minusDays(1))
    }
}

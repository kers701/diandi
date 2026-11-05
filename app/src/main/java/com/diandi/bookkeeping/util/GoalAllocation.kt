package com.diandi.bookkeeping.util

import com.diandi.bookkeeping.data.entity.SavingsGoal
import kotlin.math.floor

/**
 * 按各目标权重分配当前正余额；负余额不参与分配，异常权重按 1 处理。
 *
 * 输入输出一律为「分」，并保证 **各目标分配额之和恰好等于可分配余额**
 * （按小数部分从大到小补足余数），不会因为四舍五入凭空多出或少掉几分钱。
 */
fun allocateBalanceByWeight(balanceCents: Long, goals: List<SavingsGoal>): Map<Long, Long> {
    if (goals.isEmpty()) return emptyMap()
    val distributable = balanceCents.coerceAtLeast(0L)
    val weights = goals.map { goal ->
        goal.allocationWeight.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    }
    val totalWeight = weights.sum()
    if (!totalWeight.isFinite() || totalWeight <= 0.0) {
        return distributeEvenly(distributable, goals)
    }
    val raw = goals.indices.map { index ->
        distributable.toDouble() * weights[index] / totalWeight
    }
    val shares = raw.map { floor(it).toLong() }.toMutableList()
    var remainder = distributable - shares.sum()
    if (remainder > 0L) {
        // 小数部分越大的越先补 1 分，保证总额精确
        val order = raw.indices.sortedByDescending { raw[it] - floor(raw[it]) }
        var i = 0
        while (remainder > 0L && order.isNotEmpty()) {
            shares[order[i % order.size]] += 1L
            remainder -= 1L
            i++
        }
    }
    return goals.mapIndexed { index, goal -> goal.id to shares[index] }.toMap()
}

/** 平均分配；除不尽的余数依次补给前面的目标 */
private fun distributeEvenly(totalCents: Long, goals: List<SavingsGoal>): Map<Long, Long> {
    val count = goals.size
    if (count == 0) return emptyMap()
    val base = totalCents / count
    var remainder = totalCents % count
    return goals.associate { goal ->
        val extra = if (remainder > 0L) {
            remainder -= 1L
            1L
        } else {
            0L
        }
        goal.id to base + extra
    }
}

package com.diandi.bookkeeping.util

import com.diandi.bookkeeping.data.entity.SavingsGoal
import kotlin.math.floor

/**
 * 按各目标权重分配当前正余额。
 *
 * **关键规则：每个目标只分配到「还差多少到目标」为止。**
 * 进度满 100% 的目标不再参与分配，剩余的钱继续按权重分给还没满的目标；
 * 所有目标都满时，多出来的余额不再分配（留作可自由支配）。
 *
 * 输入输出一律为「分」；同一轮内按小数部分从大到小补足余数，
 * 不会因为四舍五入凭空多出或少掉几分钱。负余额不参与分配，异常权重按 1 处理。
 */
fun allocateBalanceByWeight(balanceCents: Long, goals: List<SavingsGoal>): Map<Long, Long> {
    if (goals.isEmpty()) return emptyMap()

    val result = LinkedHashMap<Long, Long>()
    goals.forEach { result[it.id] = 0L }

    var pool = balanceCents.coerceAtLeast(0L)
    if (pool == 0L) return result

    // 每个目标还差多少才到 100%
    val need = goals.associate { goal ->
        goal.id to (goal.targetAmountCents - goal.currentAmountCents).coerceAtLeast(0L)
    }
    val active = goals.filter { (need[it.id] ?: 0L) > 0L }.toMutableList()

    while (pool > 0L && active.isNotEmpty()) {
        val weights = active.map { goal ->
            goal.allocationWeight.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        }
        val totalWeight = weights.sum().takeIf { it.isFinite() && it > 0.0 }
        val raw = active.mapIndexed { index, _ ->
            if (totalWeight != null) {
                pool.toDouble() * weights[index] / totalWeight
            } else {
                pool.toDouble() / active.size
            }
        }

        // 本轮会有目标被「补满」：先给它们补到上限，剩下的钱下轮再分
        val cappedIndices = active.indices.filter { index ->
            raw[index] >= (need[active[index].id] ?: 0L).toDouble()
        }
        if (cappedIndices.isNotEmpty()) {
            cappedIndices.forEach { index ->
                val goal = active[index]
                val amount = need[goal.id] ?: 0L
                result[goal.id] = amount
                pool -= amount
            }
            val remaining = active.filterIndexed { index, _ -> index !in cappedIndices }
            if (remaining.size == active.size) break // 保险：没有进展就退出，避免死循环
            active.clear()
            active.addAll(remaining)
            continue
        }

        // 都没到上限：按比例分完，余数按小数部分从大到小补
        val shares = raw.map { floor(it).toLong() }.toMutableList()
        var remainder = pool - shares.sum()
        if (remainder > 0L) {
            val order = raw.indices.sortedByDescending { raw[it] - floor(raw[it]) }
            var i = 0
            while (remainder > 0L && order.isNotEmpty()) {
                shares[order[i % order.size]] += 1L
                remainder -= 1L
                i++
            }
        }
        active.forEachIndexed { index, goal ->
            result[goal.id] = shares[index]
            pool -= shares[index]
        }
        break
    }
    return result
}

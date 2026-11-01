package com.diandi.bookkeeping.util

import com.diandi.bookkeeping.data.entity.SavingsGoal

/** 按各目标权重分配当前正余额；负余额不参与分配，异常权重按 1 处理。 */
fun allocateBalanceByWeight(balance: Double, goals: List<SavingsGoal>): Map<Long, Double> {
    if (goals.isEmpty()) return emptyMap()
    val distributable = balance.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val weights = goals.map { goal ->
        goal.allocationWeight.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    }
    val totalWeight = weights.sum()
    if (!totalWeight.isFinite() || totalWeight <= 0.0) {
        val equalShare = distributable / goals.size
        return goals.associate { it.id to equalShare }
    }
    return goals.mapIndexed { index, goal ->
        goal.id to (distributable * weights[index] / totalWeight)
    }.toMap()
}

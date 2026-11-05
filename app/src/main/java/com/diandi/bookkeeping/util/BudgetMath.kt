package com.diandi.bookkeeping.util

import com.diandi.bookkeeping.data.entity.Budget

/**
 * 预算汇总（全部为「分」）。
 *
 * 语义：
 * - 固定开销：房租、水电等每月必花的项目；已花金额按分类累加。
 * - 浮动预算：日常浮动开销（可设一个覆盖全部支出的大数）。
 * - **未花预留** = max(0, 预算 − 本周期已花)，因为已花的钱已经从余额里扣过了，
 *   不能再扣第二次。
 * - **可自由分配余额** = 当前余额 − 固定未花预留 − 浮动未花预留。
 */
data class BudgetOverview(
    val balanceCents: Long = 0L,
    val fixedBudgetCents: Long = 0L,
    val fixedSpentCents: Long = 0L,
    val floatingBudgetCents: Long = 0L,
    val floatingSpentCents: Long = 0L
) {
    val fixedReservedCents: Long get() = (fixedBudgetCents - fixedSpentCents).coerceAtLeast(0L)
    val floatingReservedCents: Long get() = (floatingBudgetCents - floatingSpentCents).coerceAtLeast(0L)
    val reservedCents: Long get() = fixedReservedCents + floatingReservedCents

    /** 可自由分配余额：可以为负（说明这个周期已经透支） */
    val freeCents: Long get() = balanceCents - reservedCents

    val hasFixed: Boolean get() = fixedBudgetCents > 0L
    val hasFloating: Boolean get() = floatingBudgetCents > 0L
}

object BudgetMath {

    fun overview(
        balanceCents: Long,
        budgets: List<Budget>,
        expenseByCategory: Map<String, Long>,
        totalExpenseCents: Long
    ): BudgetOverview {
        val enabled = budgets.filter { it.enabled }
        val fixed = enabled.filter { it.kind == Budget.KIND_FIXED }
        val floating = enabled.filter { it.kind == Budget.KIND_FLOATING }

        val fixedCategories = fixed
            .flatMap { TransactionCategories.decode(it.category) }
            .toSet()
        val fixedSpent = fixedCategories.sumOf { expenseByCategory[it] ?: 0L }

        val floatingBudget = floating.sumOf { it.monthlyAmountCents }
        val floatingSpent = floating.sumOf { item ->
            spentForFloating(item, expenseByCategory, totalExpenseCents, fixedSpent)
        }

        return BudgetOverview(
            balanceCents = balanceCents,
            fixedBudgetCents = fixed.sumOf { it.monthlyAmountCents },
            fixedSpentCents = fixedSpent,
            floatingBudgetCents = floatingBudget,
            floatingSpentCents = floatingSpent
        )
    }

    /** 单条预算在本周期内已花金额（分） */
    fun spentForItem(
        budget: Budget,
        expenseByCategory: Map<String, Long>,
        totalExpenseCents: Long,
        fixedCategories: Set<String>
    ): Long = when (budget.kind) {
        Budget.KIND_FIXED -> categoriesSpent(budget, expenseByCategory)
        else -> spentForFloating(budget, expenseByCategory, totalExpenseCents, fixedSpentOf(fixedCategories, expenseByCategory))
    }

    /** 固定开销覆盖的分类集合 */
    fun fixedCategories(budgets: List<Budget>): Set<String> =
        budgets.filter { it.enabled && it.kind == Budget.KIND_FIXED }
            .flatMap { TransactionCategories.decode(it.category) }
            .toSet()

    private fun fixedSpentOf(categories: Set<String>, expenseByCategory: Map<String, Long>): Long =
        categories.sumOf { expenseByCategory[it] ?: 0L }

    private fun categoriesSpent(budget: Budget, expenseByCategory: Map<String, Long>): Long =
        TransactionCategories.decode(budget.category)
            .sumOf { expenseByCategory[it] ?: 0L }

    /**
     * 浮动预算的已花金额：
     * - 指定分类 → 这些分类的支出
     * - 未指定分类（覆盖全部支出）→ 总支出 − 固定开销支出，
     *   避免把房租水电算进日常浮动里。
     */
    private fun spentForFloating(
        budget: Budget,
        expenseByCategory: Map<String, Long>,
        totalExpenseCents: Long,
        fixedSpentCents: Long
    ): Long {
        val categories = TransactionCategories.decode(budget.category)
        return if (categories.isEmpty()) {
            (totalExpenseCents - fixedSpentCents).coerceAtLeast(0L)
        } else {
            categories.sumOf { expenseByCategory[it] ?: 0L }
        }
    }
}

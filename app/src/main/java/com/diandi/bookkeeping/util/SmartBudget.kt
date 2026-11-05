package com.diandi.bookkeeping.util

/**
 * 智能预算：用「上一个资金周期」的实际支出，给还没有预算的大类自动生成预算。
 *
 * 典型场景：房租、水电、话费这类每月金额相对稳定的固定支出，
 * 上个月花了多少，这个月大概率也差不多，直接拿来当预算比手动填更省事。
 */
data class SmartBudgetSuggestion(
    val category: String,
    /** 分 */
    val amountCents: Long
)

object SmartBudget {

    /**
     * @param lastCycleSpend 上个周期各分类的支出（分）
     * @param existingCategories 已经有预算的分类（不会再生成）
     * @return 建议列表，按金额从大到小；没有任何可参考数据时返回空列表
     */
    fun suggest(
        lastCycleSpend: Map<String, Long>,
        existingCategories: Set<String>
    ): List<SmartBudgetSuggestion> =
        lastCycleSpend
            .asSequence()
            .filter { (category, cents) ->
                category.isNotBlank() &&
                    cents > 0L &&
                    category !in existingCategories
            }
            .map { (category, cents) -> SmartBudgetSuggestion(category, cents) }
            .sortedByDescending { it.amountCents }
            .toList()

    /** 选定若干分类时，参考上个周期的实际支出（分），没有数据返回 null */
    fun referenceAmount(lastCycleSpend: Map<String, Long>, categories: Collection<String>): Long? {
        if (categories.isEmpty()) return null
        val total = categories.sumOf { lastCycleSpend[it] ?: 0L }
        return if (total > 0L) total else null
    }
}

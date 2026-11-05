package com.diandi.bookkeeping

import com.diandi.bookkeeping.data.entity.Budget
import com.diandi.bookkeeping.util.BudgetMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新预算模型：可自由分配余额 = 当前余额 − 固定未花预留 − 浮动未花预留。
 * 关键点：已经花掉的钱已经从余额里扣过了，不能再扣第二次。
 */
class BudgetMathTest {

    private fun fixed(category: String, cents: Long, enabled: Boolean = true) = Budget(
        kind = Budget.KIND_FIXED,
        scope = Budget.SCOPE_CATEGORY,
        category = category,
        monthlyAmountCents = cents,
        enabled = enabled
    )

    private fun floating(category: String, cents: Long, enabled: Boolean = true) = Budget(
        kind = Budget.KIND_FLOATING,
        scope = if (category.isBlank()) Budget.SCOPE_TOTAL else Budget.SCOPE_CATEGORY,
        category = category,
        monthlyAmountCents = cents,
        enabled = enabled
    )

    @Test
    fun `没有任何预算时可自由分配等于余额`() {
        val o = BudgetMath.overview(
            balanceCents = 10000,
            budgets = emptyList(),
            expenseByCategory = mapOf("餐饮" to 3000L),
            totalExpenseCents = 3000
        )
        assertEquals(10000L, o.freeCents)
        assertEquals(0L, o.reservedCents)
        assertFalse(o.hasFixed)
        assertFalse(o.hasFloating)
    }

    @Test
    fun `固定与浮动预留都按未花部分计算`() {
        // 余额 10000，固定预算 3000（已花 1200），浮动预算 2000（总支出 1500 中非固定部分 300）
        val o = BudgetMath.overview(
            balanceCents = 10000,
            budgets = listOf(fixed("房租", 3000), floating("", 2000)),
            expenseByCategory = mapOf("房租" to 1200L, "餐饮" to 300L),
            totalExpenseCents = 1500
        )
        assertEquals(1800L, o.fixedReservedCents)
        assertEquals(1700L, o.floatingReservedCents)
        assertEquals(3500L, o.reservedCents)
        assertEquals(6500L, o.freeCents)
    }

    @Test
    fun `已花超过预算时预留为零而不是负数`() {
        val o = BudgetMath.overview(
            balanceCents = 10000,
            budgets = listOf(fixed("房租", 1000)),
            expenseByCategory = mapOf("房租" to 1500L),
            totalExpenseCents = 1500
        )
        assertEquals(0L, o.fixedReservedCents)
        assertEquals(10000L, o.freeCents)
    }

    @Test
    fun `浮动预算指定分类时只算这些分类`() {
        val o = BudgetMath.overview(
            balanceCents = 10000,
            budgets = listOf(floating("餐饮 + 交通", 2000)),
            expenseByCategory = mapOf("餐饮" to 500L, "交通" to 300L, "购物" to 900L),
            totalExpenseCents = 1700
        )
        assertEquals(800L, o.floatingSpentCents)
        assertEquals(1200L, o.floatingReservedCents)
        assertEquals(8800L, o.freeCents)
    }

    @Test
    fun `全部支出的浮动预算会排除固定开销部分`() {
        val o = BudgetMath.overview(
            balanceCents = 10000,
            budgets = listOf(fixed("房租", 3000), floating("", 2000)),
            expenseByCategory = mapOf("房租" to 3000L, "餐饮" to 800L),
            totalExpenseCents = 3800
        )
        // 固定部分 3000 已花完 → 预留 0；浮动已花 = 3800 - 3000 = 800
        assertEquals(0L, o.fixedReservedCents)
        assertEquals(800L, o.floatingSpentCents)
        assertEquals(1200L, o.floatingReservedCents)
        assertEquals(8800L, o.freeCents)
    }

    @Test
    fun `禁用的预算不计入`() {
        val o = BudgetMath.overview(
            balanceCents = 10000,
            budgets = listOf(fixed("房租", 3000, enabled = false), floating("", 2000, enabled = false)),
            expenseByCategory = emptyMap(),
            totalExpenseCents = 0
        )
        assertEquals(0L, o.reservedCents)
        assertEquals(10000L, o.freeCents)
    }

    @Test
    fun `组合类目的固定开销按分类拆分统计`() {
        val o = BudgetMath.overview(
            balanceCents = 5000,
            budgets = listOf(fixed("房租 + 水电", 3000)),
            expenseByCategory = mapOf("房租" to 2500L, "水电" to 300L),
            totalExpenseCents = 2800
        )
        assertEquals(2800L, o.fixedSpentCents)
        assertEquals(200L, o.fixedReservedCents)
        assertEquals(4800L, o.freeCents)
    }

    @Test
    fun `余额不足时可自由分配为负`() {
        val o = BudgetMath.overview(
            balanceCents = 1000,
            budgets = listOf(fixed("房租", 3000)),
            expenseByCategory = emptyMap(),
            totalExpenseCents = 0
        )
        assertEquals(-2000L, o.freeCents)
        assertTrue(o.freeCents < 0)
    }
}

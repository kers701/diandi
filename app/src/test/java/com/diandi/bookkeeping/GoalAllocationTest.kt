package com.diandi.bookkeeping

import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.util.allocateBalanceByWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 余额按权重分配：分的总额不能凭空多出或少掉。 */
class GoalAllocationTest {

    private fun goal(id: Long, weight: Double) = SavingsGoal(
        id = id,
        title = "目标$id",
        targetAmountCents = 100_000L,
        allocationWeight = weight
    )

    @Test
    fun `平均权重按比例分配`() {
        val goals = listOf(goal(1, 1.0), goal(2, 1.0))
        val result = allocateBalanceByWeight(1000L, goals)
        assertEquals(500L, result[1L])
        assertEquals(500L, result[2L])
    }

    @Test
    fun `权重不同按比例分配且总额精确`() {
        val goals = listOf(goal(1, 3.0), goal(2, 1.0))
        val result = allocateBalanceByWeight(1000L, goals)
        assertEquals(750L, result[1L])
        assertEquals(250L, result[2L])
        assertEquals(1000L, result.values.sum())
    }

    @Test
    fun `除不尽时余数补足总额`() {
        val goals = listOf(goal(1, 1.0), goal(2, 1.0), goal(3, 1.0))
        val result = allocateBalanceByWeight(100L, goals)
        assertEquals(100L, result.values.sum())
        assertEquals(3, result.size)
        // 每份 33 或 34 分
        result.values.forEach { assertTrue(it == 33L || it == 34L) }
    }

    @Test
    fun `负余额不参与分配`() {
        val goals = listOf(goal(1, 1.0), goal(2, 1.0))
        val result = allocateBalanceByWeight(-500L, goals)
        assertEquals(0L, result.values.sum())
    }

    @Test
    fun `空目标返回空`() {
        assertTrue(allocateBalanceByWeight(1000L, emptyList()).isEmpty())
    }

    @Test
    fun `异常权重按 1 处理`() {
        val goals = listOf(goal(1, 0.0), goal(2, -1.0), goal(3, Double.NaN))
        val result = allocateBalanceByWeight(300L, goals)
        assertEquals(300L, result.values.sum())
        assertEquals(100L, result[1L])
    }
}

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

    // ========== 进度满了就不再分配（回归：曾出现分够 100% 还继续分导致溢出） ==========

    @Test
    fun `单个目标分够即止不再溢出`() {
        val goals = listOf(
            SavingsGoal(id = 1, title = "相机", targetAmountCents = 100_000L)
        )
        // 余额 5000 元，目标 1000 元 → 只分 1000 元
        val result = allocateBalanceByWeight(500_000L, goals)
        assertEquals(100_000L, result[1L])
    }

    @Test
    fun `溢出部分继续分给没满的目标`() {
        val goals = listOf(
            SavingsGoal(id = 1, title = "小目标", targetAmountCents = 100_000L),
            SavingsGoal(id = 2, title = "大目标", targetAmountCents = 500_000L)
        )
        // 余额 3000 元：小目标只拿 1000 元，剩下 2000 元给大目标
        val result = allocateBalanceByWeight(300_000L, goals)
        assertEquals(100_000L, result[1L])
        assertEquals(200_000L, result[2L])
    }

    @Test
    fun `已有进度的目标只补差额`() {
        val goals = listOf(
            SavingsGoal(
                id = 1,
                title = "相机",
                targetAmountCents = 100_000L,
                currentAmountCents = 80_000L
            )
        )
        // 还差 200 元，余额 500 元 → 只分 200 元
        val result = allocateBalanceByWeight(50_000L, goals)
        assertEquals(20_000L, result[1L])
    }

    @Test
    fun `已达标的目标不参与分配`() {
        val goals = listOf(
            SavingsGoal(
                id = 1,
                title = "已完成",
                targetAmountCents = 100_000L,
                currentAmountCents = 100_000L
            ),
            SavingsGoal(id = 2, title = "进行中", targetAmountCents = 200_000L)
        )
        val result = allocateBalanceByWeight(150_000L, goals)
        assertEquals(0L, result[1L])
        assertEquals(150_000L, result[2L])
    }

    @Test
    fun `全部达标时不再分配`() {
        val goals = listOf(
            SavingsGoal(
                id = 1,
                title = "A",
                targetAmountCents = 10_000L,
                currentAmountCents = 12_000L
            ),
            SavingsGoal(
                id = 2,
                title = "B",
                targetAmountCents = 20_000L,
                currentAmountCents = 20_000L
            )
        )
        val result = allocateBalanceByWeight(1_000_000L, goals)
        assertEquals(0L, result[1L])
        assertEquals(0L, result[2L])
    }

    @Test
    fun `多目标逐个封顶后余款继续分配`() {
        val goals = listOf(
            SavingsGoal(id = 1, title = "A", targetAmountCents = 10_000L),
            SavingsGoal(id = 2, title = "B", targetAmountCents = 20_000L),
            SavingsGoal(id = 3, title = "C", targetAmountCents = 1_000_000L)
        )
        // 余额 1000 元：A 封顶 100 元、B 封顶 200 元，剩下 700 元全给 C
        val result = allocateBalanceByWeight(100_000L, goals)
        assertEquals(10_000L, result[1L])
        assertEquals(20_000L, result[2L])
        assertEquals(70_000L, result[3L])
    }
}

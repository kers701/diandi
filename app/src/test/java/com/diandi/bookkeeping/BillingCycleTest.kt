package com.diandi.bookkeeping

import com.diandi.bookkeeping.util.BillingCycleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 资金月周期：开始日可自定义，且要能正确处理跨月、跨年和月末边界。 */
class BillingCycleTest {

    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `默认1号等于自然月`() {
        val c = BillingCycleCalculator.of(d("2026-11-05"), 1)
        assertEquals("2026-11-01", c.start.toString())
        assertEquals("2026-11-30", c.end.toString())
    }

    @Test
    fun `月中开始日且当天还没到周期`() {
        // 今天是 11-05，周期 15 号开始 → 属于 10-15 到 11-14 这一期
        val c = BillingCycleCalculator.of(d("2026-11-05"), 15)
        assertEquals("2026-10-15", c.start.toString())
        assertEquals("2026-11-14", c.end.toString())
    }

    @Test
    fun `当天正好是开始日`() {
        val c = BillingCycleCalculator.of(d("2026-11-15"), 15)
        assertEquals("2026-11-15", c.start.toString())
        assertEquals("2026-12-14", c.end.toString())
    }

    @Test
    fun `前一天仍属上一周期`() {
        val c = BillingCycleCalculator.of(d("2026-11-14"), 15)
        assertEquals("2026-10-15", c.start.toString())
        assertEquals("2026-11-14", c.end.toString())
    }

    @Test
    fun `二月末尾开始日`() {
        val c = BillingCycleCalculator.of(d("2026-02-28"), 28)
        assertEquals("2026-02-28", c.start.toString())
        assertEquals("2026-03-27", c.end.toString())
    }

    @Test
    fun `上一个周期`() {
        val prev = BillingCycleCalculator.previous(d("2026-11-05"), 15)
        assertEquals("2026-09-15", prev.start.toString())
        assertEquals("2026-10-14", prev.end.toString())
    }

    @Test
    fun `上一个周期跨年`() {
        // 2026-01-05 处于 2025-12-15~2026-01-14 这一期，上一期应是 2025-11-15~2025-12-14
        val current = BillingCycleCalculator.of(d("2026-01-05"), 15)
        assertEquals("2025-12-15", current.start.toString())
        assertEquals("2026-01-14", current.end.toString())

        val prev = BillingCycleCalculator.previous(d("2026-01-05"), 15)
        assertEquals("2025-11-15", prev.start.toString())
        assertEquals("2025-12-14", prev.end.toString())
    }

    @Test
    fun `开始日被限制在1到28`() {
        assertEquals(1, BillingCycleCalculator.clampStartDay(0))
        assertEquals(1, BillingCycleCalculator.clampStartDay(-5))
        assertEquals(28, BillingCycleCalculator.clampStartDay(31))
        assertEquals(15, BillingCycleCalculator.clampStartDay(15))
    }

    @Test
    fun `contains 判断`() {
        val c = BillingCycleCalculator.of(d("2026-11-05"), 15)
        assertTrue(c.contains(d("2026-10-15")))
        assertTrue(c.contains(d("2026-11-14")))
        assertFalse(c.contains(d("2026-10-14")))
        assertFalse(c.contains(d("2026-11-15")))
    }

    @Test
    fun `label 可读`() {
        val c = BillingCycleCalculator.of(d("2026-11-05"), 15)
        assertEquals("10月15日–11月14日", c.label)
    }
}

package com.diandi.bookkeeping

import com.diandi.bookkeeping.util.SmartBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 智能预算：按上个周期的实际支出生成建议，跳过已有预算与零支出。 */
class SmartBudgetTest {

    @Test
    fun `跳过已有预算的分类`() {
        val spend = mapOf("房租" to 300000L, "餐饮" to 120000L)
        val result = SmartBudget.suggest(spend, setOf("房租"))
        assertEquals(1, result.size)
        assertEquals("餐饮", result[0].category)
        assertEquals(120000L, result[0].amountCents)
    }

    @Test
    fun `跳过零支出与空分类`() {
        val spend = mapOf("房租" to 0L, "" to 5000L, "水电" to 8800L)
        val result = SmartBudget.suggest(spend, emptySet())
        assertEquals(1, result.size)
        assertEquals("水电", result[0].category)
    }

    @Test
    fun `按金额从大到小排序`() {
        val spend = mapOf("餐饮" to 120000L, "房租" to 300000L, "水电" to 8800L)
        val result = SmartBudget.suggest(spend, emptySet())
        assertEquals(listOf("房租", "餐饮", "水电"), result.map { it.category })
    }

    @Test
    fun `没有数据返回空`() {
        assertTrue(SmartBudget.suggest(emptyMap(), emptySet()).isEmpty())
    }

    @Test
    fun `参考金额按选中分类求和`() {
        val spend = mapOf("房租" to 300000L, "水电" to 8800L)
        assertEquals(308800L, SmartBudget.referenceAmount(spend, listOf("房租", "水电")))
    }

    @Test
    fun `参考金额无数据返回 null`() {
        assertNull(SmartBudget.referenceAmount(emptyMap(), listOf("房租")))
        assertNull(SmartBudget.referenceAmount(mapOf("餐饮" to 100L), listOf("房租")))
        assertNull(SmartBudget.referenceAmount(mapOf("房租" to 100L), emptyList()))
    }
}

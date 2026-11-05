package com.diandi.bookkeeping

import com.diandi.bookkeeping.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 金额换算与格式化：全部以「分」为准，任何一步都不该出现浮点误差。 */
class MoneyTest {

    @Test
    fun `解析整数元`() {
        assertEquals(1200L, Money.parseToCents("12"))
    }

    @Test
    fun `解析两位小数`() {
        assertEquals(1234L, Money.parseToCents("12.34"))
    }

    @Test
    fun `解析一位小数补齐两位`() {
        assertEquals(1230L, Money.parseToCents("12.3"))
    }

    @Test
    fun `半进位到分`() {
        assertEquals(1L, Money.parseToCents("0.005"))
        assertEquals(2L, Money.parseToCents("0.015"))
    }

    @Test
    fun `忽略货币符号与千分位`() {
        assertEquals(123456L, Money.parseToCents("¥1,234.56"))
        assertEquals(123456L, Money.parseToCents("￥1,234.56"))
        assertEquals(123456L, Money.parseToCents(" 1 234.56 "))
    }

    @Test
    fun `解析负数`() {
        assertEquals(-1234L, Money.parseToCents("-12.34"))
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("-"))
        assertNull(Money.parseToCents("."))
    }

    @Test
    fun `格式化两位小数`() {
        assertEquals("12.34", Money.format(1234))
        assertEquals("0.05", Money.format(5))
        assertEquals("0.00", Money.format(0))
        assertEquals("-12.34", Money.format(-1234))
    }

    @Test
    fun `千分位分组`() {
        assertEquals("1,234,567.89", Money.format(123456789))
        assertEquals("1234567.89", Money.format(123456789, grouping = false))
    }

    @Test
    fun `解析与格式化可逆`() {
        listOf(0L, 1L, 99L, 100L, 1234L, 999999L, 123456789L).forEach { cents ->
            assertEquals(cents, Money.parseToCents(Money.format(cents, grouping = false)))
        }
    }

    @Test
    fun `元与分互转不丢精度`() {
        assertEquals(1999L, Money.fromYuan(19.99))
        assertEquals(0L, Money.fromYuan(Double.NaN))
        assertEquals(19.99, Money.toYuan(1999), 0.0000001)
    }

    @Test
    fun `多次累加不产生浮点误差`() {
        // 0.1 + 0.2 在 Double 下是 0.30000000000000004；用分累加必须精确
        var sum = 0L
        repeat(3) { sum += Money.parseToCents("0.1")!! }
        assertEquals(30L, sum)
        assertEquals("0.30", Money.format(sum))
    }

    @Test
    fun `输入框回显`() {
        assertEquals("12.34", Money.toInputText(1234))
        assertEquals("", Money.toInputText(0))
    }

    @Test
    fun `占比计算`() {
        assertEquals(0.5, Money.ratio(500, 1000), 0.0000001)
        assertEquals(0.0, Money.ratio(100, 0), 0.0000001)
    }
}

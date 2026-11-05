package com.diandi.bookkeeping

import com.diandi.bookkeeping.service.AutoBillParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 通知金额解析：只测纯逻辑（不依赖 Android Context）。
 * 覆盖「余额不能当消费额」「时间片段不能当金额」「千分位」「货币符号」等易错点。
 */
class AutoBillParserTest {

    private val keywords = listOf("消费", "支出", "支付", "付款", "扣款", "收入", "到账", "收款")

    @Test
    fun `银行短信取支出金额而不是余额`() {
        val text = "您尾号1234的储蓄卡消费支出人民币35.50元，余额1000.00元"
        assertEquals(3550L, AutoBillParser.pickAmountCents(text, keywords))
    }

    @Test
    fun `微信支付带货币符号`() {
        assertEquals(1280L, AutoBillParser.pickAmountCents("微信支付：已支付¥12.80", keywords))
    }

    @Test
    fun `千分位金额`() {
        assertEquals(128050L, AutoBillParser.pickAmountCents("消费 1,280.50 元", keywords))
    }

    @Test
    fun `时间片段不当作金额`() {
        // 12:30 里的 12 和 30 都不是金额，只有「30元」是
        assertEquals(3000L, AutoBillParser.pickAmountCents("12:30 消费 30元", keywords))
    }

    @Test
    fun `没有金额返回 null`() {
        assertNull(AutoBillParser.pickAmountCents("您有一条新消息", keywords))
    }

    @Test
    fun `零金额与超大金额被过滤`() {
        assertNull(AutoBillParser.pickAmountCents("消费 0元", keywords))
        assertNull(AutoBillParser.pickAmountCents("消费 999999999元", keywords))
    }

    @Test
    fun `人民币前缀`() {
        assertEquals(8800L, AutoBillParser.pickAmountCents("人民币88.00元消费", keywords))
    }
}

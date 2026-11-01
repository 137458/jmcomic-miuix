package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 我的页签到卡片副标题文案格式化测试。
 * 活动名非空时使用间隔号与后置文案自然分隔，避免与状态文字粘连；活动名为空时仅保留状态文案。
 */
class CheckInSummaryTest {

    @Test
    fun `活动名非空且今日已签到时以间隔号分隔`() {
        assertEquals(
            "11月-兔兔萬歲 · 今天的奖励已入账",
            formatCheckInSummary(eventName = "11月-兔兔萬歲", signedToday = true)
        )
    }

    @Test
    fun `活动名非空且今日未签到时以间隔号分隔`() {
        assertEquals(
            "11月-兔兔萬歲 · 签到可得金币与经验",
            formatCheckInSummary(eventName = "11月-兔兔萬歲", signedToday = false)
        )
    }

    @Test
    fun `活动名首尾空白被清洗且保留中间内容`() {
        assertEquals(
            "[11月] 兔兔萬歲 · 今天的奖励已入账",
            formatCheckInSummary(eventName = "  [11月] 兔兔萬歲  ", signedToday = true)
        )
    }

    @Test
    fun `活动名为空或null时直接显示状态文案`() {
        assertEquals(
            "今天的奖励已入账",
            formatCheckInSummary(eventName = "", signedToday = true)
        )
        assertEquals(
            "今天的奖励已入账",
            formatCheckInSummary(eventName = "   ", signedToday = true)
        )
        assertEquals(
            "签到可得金币与经验",
            formatCheckInSummary(eventName = null, signedToday = false)
        )
    }
}

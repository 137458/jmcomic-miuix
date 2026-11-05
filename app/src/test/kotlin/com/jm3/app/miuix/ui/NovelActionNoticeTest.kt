// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovelActionNoticeTest {

    @Test
    fun 会话失效给去登录引导并用唯一失效文案() {
        val notice = novelActionNotice(SessionOutcome.Expired, failurePrefix = "收藏失败：")

        assertEquals(SessionOutcome.Expired.message, notice?.message)
        assertTrue(notice?.openLogin == true)
    }

    @Test
    fun 普通失败沿用动作文案且不引导登录() {
        val notice = novelActionNotice(SessionOutcome.Failed("网络异常"), failurePrefix = "点赞失败：")

        assertEquals("点赞失败：网络异常", notice?.message)
        assertFalse(notice?.openLogin == true)
    }

    @Test
    fun 普通失败没有原文时用兜底文案() {
        val notice = novelActionNotice(SessionOutcome.Failed(null), failurePrefix = "收藏失败：")

        assertEquals("收藏失败：请稍后重试", notice?.message)
    }

    @Test
    fun 成功结局不产生提示() {
        assertNull(novelActionNotice(SessionOutcome.Ok(Unit), failurePrefix = "点赞失败："))
    }
}

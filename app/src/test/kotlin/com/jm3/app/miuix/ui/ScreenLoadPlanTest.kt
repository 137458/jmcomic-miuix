package com.jm3.app.miuix.ui

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenLoadPlanTest {

    private val ok = SessionOutcome.Ok(Unit)

    @Test
    fun 成功结局只落内容态() {
        val plan = planLoad(ok, hasContent = false, failureMessage = "信箱加载失败")

        assertEquals(ScreenLoadPhase.Content, plan.phase)
        assertNull(plan.message)
        assertFalse(plan.keepContent)
        assertNull(plan.notice)
    }

    @Test
    fun 失效且无内容时整屏失效并给出登录文案() {
        val plan = planLoad(SessionOutcome.Expired, hasContent = false, failureMessage = "信箱加载失败")

        assertEquals(ScreenLoadPhase.Expired, plan.phase)
        assertEquals(LOGIN_EXPIRED_MESSAGE, plan.message)
        assertFalse(plan.keepContent)
    }

    @Test
    fun 失效且已有内容时保留内容只用提示告知() {
        val plan = planLoad(SessionOutcome.Expired, hasContent = true, failureMessage = "信箱加载失败")

        assertEquals(ScreenLoadPhase.Content, plan.phase)
        assertTrue(plan.keepContent)
        assertEquals(LOGIN_EXPIRED_MESSAGE, plan.notice)
        assertNull(plan.message)
    }

    @Test
    fun 失败且无内容时整屏错误带原始消息() {
        val plan = planLoad(
            SessionOutcome.Failed("网络错误"),
            hasContent = false,
            failureMessage = "信箱加载失败",
        )

        assertEquals(ScreenLoadPhase.Failed, plan.phase)
        assertEquals("网络错误", plan.message)
        assertFalse(plan.keepContent)
    }

    @Test
    fun 失败且已有内容时保留内容并拼刷新前缀() {
        val plan = planLoad(
            SessionOutcome.Failed("网络错误"),
            hasContent = true,
            failureMessage = "论坛加载失败",
            stalePrefix = "加载失败：",
        )

        assertEquals(ScreenLoadPhase.Content, plan.phase)
        assertTrue(plan.keepContent)
        assertEquals("加载失败：网络错误", plan.notice)
    }

    @Test
    fun 失败消息为空时回落到各屏默认文案() {
        val plan = planLoad(SessionOutcome.Failed(null), hasContent = false, failureMessage = "信箱加载失败")

        assertEquals(ScreenLoadPhase.Failed, plan.phase)
        assertEquals("信箱加载失败", plan.message)
    }
}

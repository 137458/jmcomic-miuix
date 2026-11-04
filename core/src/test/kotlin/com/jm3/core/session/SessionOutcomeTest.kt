package com.jm3.core.session

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionOutcomeTest {

    @Test
    fun 正常返回收成Ok结果() {
        val outcome = sessionOutcome { "追踪列表" }

        assertTrue(outcome is SessionOutcome.Ok)
        assertEquals("追踪列表", (outcome as SessionOutcome.Ok).value)
    }

    @Test
    fun 服务端返回401时判成会话失效() {
        val outcome = sessionOutcome { throw IllegalStateException("""{"code":401,"msg":"unauthorized"}""") }

        assertTrue(outcome is SessionOutcome.Expired)
    }

    @Test
    fun 繁体登录提示判成会话失效() {
        val outcome = sessionOutcome { throw IllegalStateException("請先登入會員") }

        assertTrue(outcome is SessionOutcome.Expired)
    }

    @Test
    fun 普通失败保留原文() {
        val outcome = sessionOutcome { throw IllegalStateException("HTTP 500") }

        assertTrue(outcome is SessionOutcome.Failed)
        assertEquals("HTTP 500", (outcome as SessionOutcome.Failed).message)
    }

    @Test
    fun 失败没有原文时由调用方给兜底文案() {
        val outcome = sessionOutcome { throw IllegalStateException() }

        assertTrue(outcome is SessionOutcome.Failed)
        assertEquals("列表加载失败", (outcome as SessionOutcome.Failed).messageOr("列表加载失败"))
    }

    @Test
    fun 失败有原文时不用兜底文案() {
        assertTrue(SessionOutcome.Failed("请求超时").messageOr("列表加载失败") == "请求超时")
    }

    @Test
    fun 取消不进失败分支而是原样抛出() {
        assertFailsWith<CancellationException> {
            sessionOutcome<Unit> { throw CancellationException("页面已销毁") }
        }
    }

    @Test
    fun 已有异常可直接分类() {
        assertTrue(classify(IllegalStateException("尚未登入")) is SessionOutcome.Expired)
        assertEquals("HTTP 502", (classify(IllegalStateException("HTTP 502")) as SessionOutcome.Failed).message)
    }

    @Test
    fun classify对取消异常原样抛出() {
        assertFailsWith<CancellationException> {
            classify(CancellationException("流已被取消"))
        }
    }
}

package com.jm3.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** runCatchingSuspending：取消必须原样抛出，否则屏幕销毁后剩下的逻辑还会跑（ENGINEERING.md 三.1）。 */
class CancellationTest {

    @Test
    fun `取消原样抛出而不进 Result`() = runBlocking {
        assertFailsWith<CancellationException> {
            runCatchingSuspending<Unit> { throw CancellationException("页面已关闭") }
        }
    }

    @Test
    fun `普通异常收进 Result`() = runBlocking {
        val result = runCatchingSuspending<Unit> { error("网络不通") }

        assertTrue(result.isFailure)
        assertEquals("网络不通", result.exceptionOrNull()?.message)
    }

    @Test
    fun `成功值原样返回`() = runBlocking {
        assertEquals(7, runCatchingSuspending { 7 }.getOrNull())
    }
}

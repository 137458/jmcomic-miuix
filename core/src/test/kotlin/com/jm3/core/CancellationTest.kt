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
    // 表达式体的返回类型会跟着最后一句走：assertFailsWith 返回被捕获的异常，JUnit 5 只执行返回 void 的用例，
    // 少写这个 <Unit> 时该用例根本不跑，报告里看不到任何失败。
    fun `取消原样抛出而不进 Result`() = runBlocking<Unit> {
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

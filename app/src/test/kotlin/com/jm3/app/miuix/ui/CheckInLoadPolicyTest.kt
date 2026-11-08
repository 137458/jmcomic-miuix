package com.jm3.app.miuix.ui

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.session.SessionOutcome
import io.github.jukomu.jmcomic.api.exception.JmClientInitializationException
import io.github.jukomu.jmcomic.api.exception.NetworkException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CheckInLoadPolicyTest {

    @Test
    fun `首次失败后按 1s 与 3s 退避重试直到成功`() = runBlocking {
        val queue = ArrayDeque<SessionOutcome<String>>(
            listOf(
                SessionOutcome.Failed("network"),
                SessionOutcome.Failed("network"),
                SessionOutcome.Ok("signed"),
            ),
        )
        val waits = mutableListOf<Long>()
        var attempts = 0

        val result = retryCheckInLoad(
            attempt = {
                attempts++
                queue.removeFirst()
            },
            sleep = { waits += it },
        )

        assertEquals(SessionOutcome.Ok("signed"), result)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 3_000L), waits)
    }

    @Test
    fun `三次都失败才返回最后一次失败`() = runBlocking {
        val waits = mutableListOf<Long>()
        var attempts = 0

        val result = retryCheckInLoad(
            attempt = {
                attempts++
                SessionOutcome.Failed("attempt-$attempts")
            },
            sleep = { waits += it },
        )

        assertEquals("attempt-3", (result as SessionOutcome.Failed).message)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 3_000L), waits)
    }

    @Test
    fun `会话失效不重试也不等待`() = runBlocking {
        var attempts = 0

        val result = retryCheckInLoad(
            attempt = {
                attempts++
                SessionOutcome.Expired
            },
            sleep = { error("失效是终态，不应进入退避等待") },
        )

        assertEquals(SessionOutcome.Expired, result)
        assertEquals(1, attempts)
    }

    @Test
    fun `网关已就绪时不再轮询等待`() = runBlocking {
        var checks = 0

        val ready = awaitGatewayReady(
            ready = {
                checks++
                true
            },
            timeoutMs = 1_000L,
            pollMs = 250L,
            sleep = { error("已就绪不应等待") },
        )

        assertTrue(ready)
        assertEquals(1, checks)
    }

    @Test
    fun `网关在等待窗口内就绪后立即放行`() = runBlocking {
        var checks = 0
        val sleeps = mutableListOf<Long>()

        val ready = awaitGatewayReady(
            ready = {
                checks++
                checks >= 3
            },
            timeoutMs = 1_000L,
            pollMs = 250L,
            sleep = { sleeps += it },
        )

        assertTrue(ready)
        assertEquals(3, checks)
        assertEquals(listOf(250L, 250L), sleeps)
    }

    @Test
    fun `网关始终未就绪时按上限返回 false`() = runBlocking {
        var checks = 0
        val sleeps = mutableListOf<Long>()

        val ready = awaitGatewayReady(
            ready = {
                checks++
                false
            },
            timeoutMs = 1_000L,
            pollMs = 250L,
            sleep = { sleeps += it },
        )

        assertFalse(ready)
        assertEquals(4, sleeps.size)
        assertEquals(5, checks)
    }

    @Test
    fun `超时异常归类为网络超时`() {
        val failure = SessionOutcome.Failed("read timed out", SocketTimeoutException("read timed out"))

        assertEquals("网络超时，请稍后重试", checkInFailureCopy(failure, FALLBACK))
    }

    @Test
    fun `网络异常归类为网络连接失败`() {
        assertEquals(
            "网络连接失败，请检查网络后重试",
            checkInFailureCopy(SessionOutcome.Failed("connect failed", NetworkException("connect failed")), FALLBACK),
        )
        assertEquals(
            "网络连接失败，请检查网络后重试",
            checkInFailureCopy(SessionOutcome.Failed("unknown host", UnknownHostException("host")), FALLBACK),
        )
    }

    @Test
    fun `异常链上的网络错误也要归类`() {
        val wrapped = RuntimeException("wrapped", NetworkException("connect failed"))

        assertEquals(
            "网络连接失败，请检查网络后重试",
            checkInFailureCopy(SessionOutcome.Failed("wrapped", wrapped), FALLBACK),
        )
    }

    @Test
    fun `初始化异常优先于其包裹的网络异常`() {
        val init = JmClientInitializationException("all domains dead", SocketTimeoutException("timeout"))

        assertEquals(
            "客户端尚未就绪，请稍后重试",
            checkInFailureCopy(SessionOutcome.Failed("all domains dead", init), FALLBACK),
        )
    }

    @Test
    fun `登录失效文案归类为重新登录`() {
        val failure = SessionOutcome.Failed("""{"code":401,"errorMsg":"請先登入會員"}""")

        assertEquals(LOGIN_EXPIRED_MESSAGE, checkInFailureCopy(failure, FALLBACK))
    }

    @Test
    fun `未知异常回落到入口自己的通用文案`() {
        assertEquals(FALLBACK, checkInFailureCopy(SessionOutcome.Failed("HTTP 502"), FALLBACK))
        assertEquals(FALLBACK, checkInFailureCopy(SessionOutcome.Failed("HTTP 502", IllegalStateException("HTTP 502")), FALLBACK))
        assertEquals(FALLBACK, checkInFailureCopy(SessionOutcome.Failed(null), FALLBACK))
    }

    private companion object {
        const val FALLBACK = "签到状态加载失败，请稍后重试"
    }
}

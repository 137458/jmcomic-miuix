package com.jm3.core

import io.github.jukomu.jmcomic.api.exception.JmClientInitializationException
import io.github.jukomu.jmcomic.api.exception.NetworkException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class FailureKindTest {

    @Test
    fun `登录失效文案优先于异常类型`() {
        val cause = SocketTimeoutException("timeout")

        assertEquals(
            FailureKind.LoginExpired,
            classifyFailure("""{"code":401,"errorMsg":"請先登入會員"}""", cause),
        )
    }

    @Test
    fun `初始化异常优先于其包裹的超时异常`() {
        val cause = JmClientInitializationException("all domains dead", SocketTimeoutException("timeout"))

        assertEquals(FailureKind.ClientNotReady, classifyFailure("all domains dead", cause))
    }

    @Test
    fun `超时异常归类为超时`() {
        assertEquals(FailureKind.Timeout, classifyFailure("read timed out", SocketTimeoutException("read timed out")))
        assertEquals(FailureKind.Timeout, classifyFailure("interrupted", InterruptedIOException("interrupted")))
    }

    @Test
    fun `网络异常归类为网络失败`() {
        assertEquals(FailureKind.Network, classifyFailure("connect failed", NetworkException("connect failed")))
        assertEquals(FailureKind.Network, classifyFailure("unknown host", UnknownHostException("host")))
        assertEquals(FailureKind.Network, classifyFailure("connect refused", ConnectException("connect refused")))
        assertEquals(FailureKind.Network, classifyFailure("no route", NoRouteToHostException("no route")))
    }

    @Test
    fun `异常链上的网络错误也要归类`() {
        val wrapped = RuntimeException("wrapped", NetworkException("connect failed"))

        assertEquals(FailureKind.Network, classifyFailure("wrapped", wrapped))
    }

    @Test
    fun `其他异常落到 Other`() {
        assertEquals(FailureKind.Other, classifyFailure("HTTP 502", IllegalStateException("HTTP 502")))
        assertEquals(FailureKind.Other, classifyFailure(null, null))
    }
}

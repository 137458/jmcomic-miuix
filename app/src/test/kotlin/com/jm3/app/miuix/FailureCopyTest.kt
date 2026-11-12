package com.jm3.app.miuix

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.session.SessionOutcome
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class FailureCopyTest {
    @Test
    fun `业务报文里的登录失效提示优先于异常类型`() {
        val failure = SessionOutcome.Failed("""{"code":401,"errorMsg":"請先登入會員"}""")

        assertEquals(LOGIN_EXPIRED_MESSAGE, failureCopy(failure, "加载失败"))
    }

    @Test
    fun `超时与网络异常归类成固定中文文案`() {
        assertEquals("网络超时，请稍后重试", failureCopy(SocketTimeoutException("read timed out"), "加载失败"))
        assertEquals("网络连接失败，请检查网络后重试", failureCopy(UnknownHostException("host"), "加载失败"))
    }

    @Test
    fun `无法归类的异常回落到调用方兜底文案`() {
        assertEquals("加载失败", failureCopy(RuntimeException("boom"), "加载失败"))
        assertEquals("加载失败", failureCopy(IOException("raw english error"), "加载失败"))
        assertEquals("加载失败", failureCopy(null as Throwable?, "加载失败"))
    }

    @Test
    fun `显式业务IO异常保留可读中文提示`() {
        assertEquals("仅支持 HTTPS 下载地址", failureCopy(UserMessageIOException("仅支持 HTTPS 下载地址"), "下载失败"))
    }
}

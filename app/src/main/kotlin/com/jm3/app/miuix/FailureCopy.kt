package com.jm3.app.miuix

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.isSessionExpired
import com.jm3.core.session.SessionOutcome
import io.github.jukomu.jmcomic.api.exception.JmClientInitializationException
import io.github.jukomu.jmcomic.api.exception.NetworkException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 失败结局的可见文案：按异常类型归类成用户能理解的几档，不把库的英文原文抛上屏。
 *
 * 登录失效优先判文案：服务端把 401 装在业务 JSON 里，没有结构化类型可用，
 * 这条判断与 [com.jm3.core.session.classify] 用的是同一套规则。
 * 未知异常一律回落到各入口自己的通用文案。所有屏幕共用这一份，禁止再各写一套映射。
 */
internal fun failureCopy(failure: SessionOutcome.Failed, fallback: String): String = when {
    isSessionExpired(failure.message) -> LOGIN_EXPIRED_MESSAGE
    failure.cause.hasType(JmClientInitializationException::class.java) -> "客户端尚未就绪，请稍后重试"
    failure.cause.hasType(SocketTimeoutException::class.java) -> "网络超时，请稍后重试"
    failure.cause.hasType(InterruptedIOException::class.java) -> "网络超时，请稍后重试"
    failure.cause.hasNetworkType() -> "网络连接失败，请检查网络后重试"
    else -> fallback
}

/** 只拿到异常本身的调用点（catch 分支）：装成 Failed 走同一套归类，避免第二份映射。 */
internal fun failureCopy(cause: Throwable?, fallback: String): String =
    if (cause == null) fallback else failureCopy(SessionOutcome.Failed(cause.message, cause), fallback)

/** 沿异常链找类型：库常把底层 IOException 包在自己的异常里，只看顶层的类型会漏判。 */
private fun Throwable?.hasType(type: Class<*>): Boolean {
    var current = this
    val visited = HashSet<Throwable>()
    while (current != null && visited.add(current)) {
        if (type.isInstance(current)) return true
        current = current.cause
    }
    return false
}

private fun Throwable?.hasNetworkType(): Boolean =
    hasType(NetworkException::class.java) ||
        hasType(UnknownHostException::class.java) ||
        hasType(ConnectException::class.java) ||
        hasType(NoRouteToHostException::class.java)

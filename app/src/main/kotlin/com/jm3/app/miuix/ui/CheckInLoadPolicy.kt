package com.jm3.app.miuix.ui

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
import kotlinx.coroutines.delay

/**
 * 首屏签到状态加载的退避节奏：立即一次，失败后等 1s、再失败等 3s 各重试一次。
 *
 * 启动早期失败多半是域名池刷新或库初始化尚未收敛，一次失败就落终态会把瞬时故障显示成「签到坏了」。
 */
internal val CHECK_IN_RETRY_DELAYS_MS: List<Long> = listOf(0L, 1_000L, 3_000L)

/** 网关就绪等待上限：库首次初始化（拉线路表 + 全域名探活）约 3.5s，留一点余量。 */
internal const val GATEWAY_READY_TIMEOUT_MS = 4_500L

/** 网关就绪轮询间隔：够短以免拖慢首屏，够长以免空转。 */
internal const val GATEWAY_READY_POLL_MS = 250L

/**
 * 按 [CHECK_IN_RETRY_DELAYS_MS] 重试签到状态加载，只在普通失败时重试。
 *
 * 会话失效是终态：账号层已经在处理补注入或重登，继续重试只会再吃一次 401；
 * 成功更没有重试的理由。因此只有 [SessionOutcome.Failed] 会走下一轮。
 */
internal suspend fun <T> retryCheckInLoad(
    attempt: suspend () -> SessionOutcome<T>,
    delays: List<Long> = CHECK_IN_RETRY_DELAYS_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
): SessionOutcome<T> {
    var last: SessionOutcome<T> = SessionOutcome.Failed(null)
    for ((index, waitMs) in delays.withIndex()) {
        if (index > 0 && waitMs > 0) sleep(waitMs)
        last = attempt()
        if (last !is SessionOutcome.Failed) return last
    }
    return last
}

/**
 * 等网关的域名管理器进入 READY。
 *
 * 未就绪时首个请求会阻塞在库的初始化链上，带上重试会变成「一次阻塞 + 三次超时」；
 * 先等 READY 再发，把等待收敛到一个可预期的上限。超时也返回 false，由调用方照常发请求——
 * 真实错误必须显示出来，不能让页面永远停在加载中。
 */
internal suspend fun awaitGatewayReady(
    ready: () -> Boolean,
    timeoutMs: Long = GATEWAY_READY_TIMEOUT_MS,
    pollMs: Long = GATEWAY_READY_POLL_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    if (ready()) return true
    val polls = (timeoutMs / pollMs).toInt()
    repeat(polls) {
        sleep(pollMs)
        if (ready()) return true
    }
    return false
}

/**
 * 签到失败的可见文案：按异常类型归类成用户能理解的几档，不把库的英文原文抛上屏。
 *
 * 登录失效优先判文案：服务端把 401 装在业务 JSON 里，没有结构化类型可用，
 * 这条判断与 [com.jm3.core.session.classify] 用的是同一套规则。
 * 未知异常一律回落到各入口自己的通用文案。
 */
internal fun checkInFailureCopy(failure: SessionOutcome.Failed, fallback: String): String = when {
    isSessionExpired(failure.message) -> LOGIN_EXPIRED_MESSAGE
    failure.cause.hasType(JmClientInitializationException::class.java) -> "客户端尚未就绪，请稍后重试"
    failure.cause.hasType(SocketTimeoutException::class.java) -> "网络超时，请稍后重试"
    failure.cause.hasType(InterruptedIOException::class.java) -> "网络超时，请稍后重试"
    failure.cause.hasNetworkType() -> "网络连接失败，请检查网络后重试"
    else -> fallback
}

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

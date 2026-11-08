package com.jm3.app.miuix.ui

import com.jm3.app.miuix.failureCopy
import com.jm3.core.session.SessionOutcome
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
 * 签到失败的可见文案：与其它屏共用 [failureCopy] 的归类，避免第二套话术。
 */
internal fun checkInFailureCopy(failure: SessionOutcome.Failed, fallback: String): String =
    failureCopy(failure, fallback)

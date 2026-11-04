package com.jm3.core.session

import com.jm3.core.isSessionExpired
import kotlinx.coroutines.CancellationException

/**
 * 一次网关调用的三种结局：拿到值、会话失效、普通失败。
 *
 * 会话失效的文本判定被关在 [sessionOutcome] 里面，屏幕只匹配分支，不再各自 import
 * [isSessionExpired] 手判字符串（见 ENGINEERING.md 二.1 与架构审查卡 2）。
 */
sealed interface SessionOutcome<out T> {
    data class Ok<T>(val value: T) : SessionOutcome<T>

    data object Expired : SessionOutcome<Nothing>

    data class Failed(val message: String?) : SessionOutcome<Nothing> {
        /** 失败原文可能为空（异常没带 message），这里给调用方一处统一兜底。 */
        fun messageOr(fallback: String): String = message ?: fallback
    }
}

/** 执行一次可能因会话失效而失败的网关调用；取消原样抛出（ENGINEERING.md 三.1）。 */
fun <T> sessionOutcome(block: () -> T): SessionOutcome<T> =
    try {
        SessionOutcome.Ok(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        classify(e)
    }

/** 把已经发生的异常分类；取消原样抛出。 */
fun classify(error: Throwable): SessionOutcome<Nothing> {
    if (error is CancellationException) {
        throw error
    }
    return if (isSessionExpired(error.message)) {
        SessionOutcome.Expired
    } else {
        SessionOutcome.Failed(error.message)
    }
}

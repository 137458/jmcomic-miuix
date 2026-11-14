package com.jm3.app.miuix

import com.jm3.core.FailureKind
import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.classifyFailure
import com.jm3.core.session.SessionOutcome
import java.io.IOException

/** 明确附带用户可读中文提示的业务 IO 异常；仅此类显式异常可透传给 [failureCopy]。 */
internal open class UserMessageIOException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * 失败结局的可见文案：按 [FailureKind] 归类成用户能理解的几档，不把库的英文原文抛上屏。
 *
 * 归类规则（登录失效文案、客户端未就绪、超时、网络失败）在 `:core` 的 [classifyFailure] 里，
 * 协议库的异常类型因此不再出现在 `:app`；这里只负责挑中文文案与兜底。
 * 未知异常一律回落到各入口自己的通用文案。所有屏幕共用这一份，禁止再各写一套映射。
 */
internal fun failureCopy(failure: SessionOutcome.Failed, fallback: String): String =
    when (classifyFailure(failure.message, failure.cause)) {
        FailureKind.LoginExpired -> LOGIN_EXPIRED_MESSAGE
        FailureKind.ClientNotReady -> "客户端尚未就绪，请稍后重试"
        FailureKind.Timeout -> "网络超时，请稍后重试"
        FailureKind.Network -> "网络连接失败，请检查网络后重试"
        FailureKind.Other -> failure.cause.findMessage<UserMessageIOException>() ?: fallback
    }

/** 只拿到异常本身的调用点（catch 分支）：装成 Failed 走同一套归类，避免第二份映射。 */
internal fun failureCopy(cause: Throwable?, fallback: String): String =
    if (cause == null) fallback else failureCopy(SessionOutcome.Failed(cause.message, cause), fallback)

private inline fun <reified T : Throwable> Throwable?.findMessage(): String? {
    var current = this
    val visited = HashSet<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is T) return current.message?.takeIf { it.isNotBlank() }
        current = current.cause
    }
    return null
}

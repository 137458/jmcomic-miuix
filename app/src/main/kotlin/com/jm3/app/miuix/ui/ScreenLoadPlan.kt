package com.jm3.app.miuix.ui

import com.jm3.app.miuix.failureCopy
import com.jm3.core.session.SessionOutcome

/** 一次加载结局在屏级状态上的落点。 */
internal enum class ScreenLoadPhase { Content, Expired, Failed }

/**
 * 加载结局的屏级处置计划：整屏形态（[phase]）与补丁（[keepContent]/[notice]/[message]）分离。
 *
 * [message] 只在整屏形态下给出；[notice] 只在保留内容时给出（交给各屏自己弹 Snackbar），
 * 这样同一次失败不会既换掉整屏又有提示，两个通道永远只有一个生效。
 */
internal data class ScreenLoadPlan(
    val phase: ScreenLoadPhase,
    val message: String? = null,
    val keepContent: Boolean = false,
    val notice: String? = null,
)

/**
 * 结局 → 计划的唯一翻译点：成功、会话失效、失败三档在这里收口。
 *
 * 已有内容时不把整屏顶掉——失效与失败都退化成「保留内容 + 提示」；
 * 没有内容时失效必须与失败分开：失效给登录文案且不给重试（重试必然再吃一次 401）。
 */
internal fun planLoad(
    outcome: SessionOutcome<*>,
    hasContent: Boolean,
    failureMessage: String,
    stalePrefix: String = "刷新失败：",
): ScreenLoadPlan = when (outcome) {
    is SessionOutcome.Ok -> ScreenLoadPlan(ScreenLoadPhase.Content)

    SessionOutcome.Expired -> if (hasContent) {
        ScreenLoadPlan(
            phase = ScreenLoadPhase.Content,
            keepContent = true,
            notice = SessionOutcome.Expired.message,
        )
    } else {
        ScreenLoadPlan(ScreenLoadPhase.Expired, message = SessionOutcome.Expired.message)
    }

    is SessionOutcome.Failed -> {
        // 原文只进日志，屏上一律是可读中文；[failureMessage] 是归类失败时的兜底
        val message = failureCopy(outcome, failureMessage)
        if (hasContent) {
            ScreenLoadPlan(
                phase = ScreenLoadPhase.Content,
                keepContent = true,
                notice = stalePrefix + message,
            )
        } else {
            ScreenLoadPlan(ScreenLoadPhase.Failed, message = message)
        }
    }
}

/**
 * 动作型调用的统一落点：成功不吭声，失效给登录文案并打开登录，普通失败给「动作：原因」。
 *
 * 与 [planLoad] 的分工：那是加载结局，可能保住已有内容；这里是一次性动作，不改屏级形态、
 * 也没有重试按钮，只需要一条提示与（失效时的）登录入口。
 */
internal suspend fun SessionOutcome<*>.notifyAction(
    notify: suspend (String) -> Unit,
    failurePrefix: String,
    fallback: String = "请稍后重试",
    onExpired: () -> Unit = {},
) {
    when (this) {
        is SessionOutcome.Ok -> Unit
        SessionOutcome.Expired -> {
            notify(SessionOutcome.Expired.message)
            onExpired()
        }
        is SessionOutcome.Failed -> notify(failurePrefix + "：" + failureCopy(this, fallback))
    }
}

package com.jm3.app.miuix.ui

import com.jm3.app.miuix.Jm3Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 会话失效的唯一处置入口：把本地会话标成失效。
 *
 * 判不判失效由 :core 的 [com.jm3.core.session.SessionOutcome] 给结论，页面只负责把状态切成
 * 「去登录」（[LoginExpiredScreen]）；清凭据这件事走这里，不再每个屏幕各写一份 launch。
 */
internal fun CoroutineScope.clearExpiredSession() {
    launch { Jm3Graph.account.markSessionExpired() }
}

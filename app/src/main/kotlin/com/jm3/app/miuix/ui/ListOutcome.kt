package com.jm3.app.miuix.ui

import com.jm3.core.session.SessionOutcome

/**
 * 可降级区块（侧栏标签、随便看看、连载更新、分类树）的取值：失败与会话失效一律退化成兜底值。
 *
 * 这些区块只是页面的增强内容，接口不可用时留空即可，绝不能因此顶掉主列表；会话失效的清理
 * 已由 [com.jm3.core.session.AuthCall] 在结局里完成，调用方只需要一个空值。
 *
 * 主列表数据的翻译不走这里：列表有自己的三态（[ListState]）与统一适配（ListStateAdapters.kt）。
 */
internal fun <T> SessionOutcome<T>.orDegrade(fallback: T): T =
    when (this) {
        is SessionOutcome.Ok -> value
        else -> fallback
    }

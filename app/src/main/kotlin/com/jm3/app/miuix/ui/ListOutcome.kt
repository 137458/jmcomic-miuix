package com.jm3.app.miuix.ui

import com.jm3.core.model.SearchPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.toFeed

/**
 * 网关结局 → 屏级列表状态：首页、浏览页、标签结果区共用的一份翻译。
 *
 * 成功落内容；失败与会话失效都落列表内的错误态，文案一律由 [planLoad] 给出 —— 失效时用户
 * 看到的是登录提示，不再取决于原始异常文本里恰好有没有"登录"字样。
 *
 * 这是列表主数据的翻译：失败必须被用户看见（列表内错误态 + 重试）。只想留空、不想顶掉
 * 主列表的增强区块请用 [orDegrade]。
 */
internal fun SessionOutcome<SearchPage>.asListState(failureMessage: String): ListState =
    when (this) {
        is SessionOutcome.Ok -> ListState.Loaded(value.toFeed())
        else -> ListState.Failed(
            message = planLoad(this, hasContent = false, failureMessage = failureMessage).message.orEmpty(),
            sessionExpired = this is SessionOutcome.Expired,
        )
    }

/**
 * 可降级区块（侧栏标签、随便看看、连载更新、分类树）的取值：失败与会话失效一律退化成兜底值。
 *
 * 这些区块只是页面的增强内容，接口不可用时留空即可，绝不能因此顶掉主列表；会话失效的清理
 * 已由 [com.jm3.core.session.AuthCall] 在结局里完成，调用方只需要一个空值。
 */
internal fun <T> SessionOutcome<T>.orDegrade(fallback: T): T =
    when (this) {
        is SessionOutcome.Ok -> value
        else -> fallback
    }

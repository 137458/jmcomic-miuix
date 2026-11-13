package com.jm3.app.miuix.ui.list

import com.jm3.app.miuix.ui.ListState
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.session.SessionOutcome
import com.jm3.core.toSlice

/**
 * 编排层与渲染层之间的唯一一份状态翻译。
 *
 * 编排（[SessionListLoader]）只认自己的 [ListLoadState]，渲染只认 [ListState]；中间这层曾按屏幕抄了
 * 五份 toListState 与三份 toCouiPageSlice，任何一边加态都得改五处。收口到这里后，屏幕只 import 不复制。
 */

/** 编排的屏级三态 → 渲染的 [ListState] 三态。 */
internal fun SessionListSnapshot<SearchResult>.toListState(): ListState = when (val current = state) {
    ListLoadState.Loading -> ListState.Loading
    ListLoadState.Loaded -> ListState.Loaded(feed)
    is ListLoadState.Failed -> ListState.Failed(current.message, current.sessionExpired)
}

/**
 * 网关分页结局 → 共享分页切片：页码必须随载体透传（[SearchPage.toSlice] 负责），否则续页会取错页。
 *
 * 失效与失败两个分支原样透传：失效是 [SessionOutcome.Expired] 单例，重建实例会破坏引用相等判定。
 */
internal fun SessionOutcome<SearchPage>.toCouiPageSlice(): SessionOutcome<PageSlice<SearchResult>> = when (this) {
    is SessionOutcome.Ok -> SessionOutcome.Ok(value.toSlice())
    SessionOutcome.Expired -> SessionOutcome.Expired
    is SessionOutcome.Failed -> this
}

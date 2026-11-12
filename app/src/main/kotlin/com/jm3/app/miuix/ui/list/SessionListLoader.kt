package com.jm3.app.miuix.ui.list

import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.planLoad
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 列表页的屏级加载态：只有三档，与 [com.jm3.app.miuix.ui.ScreenLoadPhase] 一一对应。 */
internal sealed interface ListLoadState {
    data object Loading : ListLoadState

    data object Loaded : ListLoadState

    /** [sessionExpired] 区分「重试可能好」与「会话没了」：后者只给登录入口。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ListLoadState
}

/** 一次列表加载的可观察状态。 */
internal data class SessionListSnapshot<T>(
    val feed: PagedFeed<T> = PagedFeed(),
    val state: ListLoadState = ListLoadState.Loading,
    val refreshing: Boolean = false,
)

/**
 * 分页列表的加载编排：请求代次、在途标记、翻页游标与失败落点都在这里，屏幕只画。
 *
 * 此前每个列表页各自持有一份 requestToken、一份 beginLoadMore/failLoadMore 顺序与一份
 * applyFailure 分派；两套皮肤加起来十几份。这里把它们收成一处，屏幕侧只提供三件事：
 * 取哪一页（[fetchPage]）、条目的 key（[idOf]）、保留内容时怎么提示（[notice]）。
 *
 * 失败处置沿用 [planLoad]：有内容就留内容并提示，没内容才整屏换形态；会话失效与普通失败
 * 的文案、以及「失效不给重试」的规矩都在那一处，这里只负责把计划落到列表状态上。
 */
internal class SessionListLoader<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> Any,
    private val failureMessage: String,
    private val fetchPage: suspend (page: Int) -> SessionOutcome<PageSlice<T>>,
    private val notice: (String) -> Unit,
    private val stalePrefix: String = "加载失败：",
) {
    private val _snapshot = MutableStateFlow(SessionListSnapshot<T>())
    val snapshot: StateFlow<SessionListSnapshot<T>> = _snapshot.asStateFlow()

    /** 请求代次：只增不减，旧请求返回时对不上就直接丢弃，避免旧结果盖掉新列表。 */
    private var requestToken = 0

    val feed: PagedFeed<T> get() = _snapshot.value.feed

    val state: ListLoadState get() = _snapshot.value.state

    val refreshing: Boolean get() = _snapshot.value.refreshing

    /** 首屏与重置加载：清空列表、显示整屏加载态。 */
    fun loadFirstPage() {
        request(page = 1, replace = true, showLoading = true, pulling = false)
    }

    /** 下拉刷新与点击分页的「回到第一页」：旧条目留在屏上，只显示刷新指示。 */
    fun refresh() {
        request(page = 1, replace = true, showLoading = false, pulling = true)
    }

    /** 触底加载下一页；没有下一页或在途时什么都不做。 */
    fun loadMore() {
        val current = _snapshot.value.feed
        if (!current.canLoadMore) return
        request(
            page = current.page + 1,
            replace = false,
            showLoading = false,
            pulling = false,
        )
    }

    /**
     * 点击分页跳到指定页：整页替换，不显示整屏加载态。
     *
     * 跳页失败时保留旧页内容并提示：用户点的是「第 N 页」，不是「重新加载整屏」，
     * 整屏错误会把已经读到的列表顶掉。
     */
    fun goToPage(page: Int) {
        request(page = page, replace = true, showLoading = false, pulling = false, keepOnFailure = true)
    }

    /** 本地抹掉若干条（删除成功后不必回源）。 */
    fun removeWhere(predicate: (T) -> Boolean) {
        _snapshot.update { it.copy(feed = it.feed.copy(items = it.feed.items.filterNot(predicate))) }
    }

    private fun request(
        page: Int,
        replace: Boolean,
        showLoading: Boolean,
        pulling: Boolean,
        keepOnFailure: Boolean = false,
    ) {
        val before = _snapshot.value.feed
        if (!replace && !before.canLoadMore) return
        val token = if (replace) ++requestToken else requestToken
        val pending = if (replace) before else before.beginLoadMore()
        _snapshot.update {
            it.copy(
                feed = pending,
                state = if (showLoading) ListLoadState.Loading else it.state,
                refreshing = if (pulling) true else it.refreshing,
            )
        }
        scope.launch {
            // 启动闸门、IO 切线程与失效通知都在 AuthCall 里，这里只处理三种结局
            val outcome = fetchPage(page)
            if (token != requestToken) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> _snapshot.update {
                    it.copy(
                        feed = if (replace) {
                            PagedFeed<T>().replace(outcome.value, idOf)
                        } else {
                            pending.append(outcome.value, idOf)
                        },
                        state = ListLoadState.Loaded,
                        refreshing = false,
                    )
                }

                SessionOutcome.Expired -> applyFailure(outcome, pending, replace, pulling, keepOnFailure)

                is SessionOutcome.Failed -> applyFailure(outcome, pending, replace, pulling, keepOnFailure)
            }
        }
    }

    /**
     * 失败落点：翻页与刷新时屏上已有内容，一律保留并提示；首屏才允许整屏换形态。
     *
     * 保留内容时顺手把页脚标成可重试（[PagedFeed.failLoadMore]）；整屏失败要撤掉在途标记，
     * 否则页脚会永远转圈。
     */
    private fun applyFailure(
        outcome: SessionOutcome<*>,
        pending: PagedFeed<T>,
        replace: Boolean,
        pulling: Boolean,
        keepOnFailure: Boolean,
    ) {
        val hasContent = pending.items.isNotEmpty() && (!replace || pulling || keepOnFailure)
        val plan = planLoad(outcome, hasContent, failureMessage, stalePrefix)
        if (plan.keepContent) {
            _snapshot.update { it.copy(feed = pending.failLoadMore(), refreshing = false) }
            notice(plan.notice.orEmpty())
        } else {
            _snapshot.update {
                it.copy(
                    feed = pending.copy(loadingMore = false),
                    state = ListLoadState.Failed(
                        message = plan.message.orEmpty(),
                        sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                    ),
                    refreshing = false,
                )
            }
        }
    }
}

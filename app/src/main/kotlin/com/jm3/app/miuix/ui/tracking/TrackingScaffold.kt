package com.jm3.app.miuix.ui.tracking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.core.model.TrackingItem
import com.jm3.core.model.TrackingPage
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.hasMoreByLoaded
import com.jm3.core.paging.loadedThrough
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/*
 * 连载追踪页状态编排骨架（C4 双皮肤去重）。
 *
 * 抽出来的是行为：loader 的构造与首屏加载、每页条数提示的记账、分页条目标页的收敛、
 * 取消追踪的在途守卫与三种结局、成功后的「撤销」二次确认（含撤销本身的三种结局），
 * 以及撤销成功后的回源口径。
 * 皮肤侧只留自己的列表控件、行卡样式、顶栏与空/错/加载态；本文件不引用任何 Miuix/COUI 控件。
 *
 * 两皮肤的真实差异用参数与字段表达，不强行统一：
 * - 每页条数提示的记账口径 [hintGrowsWithLaterPages]：ColorOS 侧让提示跟着后续页变大，HyperOS 侧只用首屏条数；
 * - 撤销成功后的回源动作 [TrackingUndoReload]：HyperOS 整页刷新（回第 1 页并转下拉指示），ColorOS 重拉当前页；
 * - 取消成功后的皮肤钩子 [TrackingScaffoldState] 的 onUnfollowed：只有 ColorOS 侧触发触感；
 * - 已取消条数 [TrackingScaffoldState.removedCount] 只有 ColorOS 侧读（顶栏「共 N 部」要扣掉本地抹除的行）；
 * - 翻页落地后的归顶时机属于滚动容器，留在各自皮肤（HyperOS 等页码对齐，ColorOS 先归顶再请求）。
 */

/** 追踪列表失败的兜底文案，两皮肤共用。 */
internal const val TRACKING_FAILURE_MESSAGE = "追踪列表加载失败"

/** 取消追踪成功后的提示与撤销动作文案。 */
internal const val TRACKING_UNFOLLOWED_MESSAGE = "已取消追踪"
internal const val TRACKING_UNDO_LABEL = "撤销"

/** 动作失败时兜底文案；[com.jm3.app.miuix.ui.coui.theme.CouiStrings.RetryLater] 与本常量同值。 */
internal const val TRACKING_RETRY_LATER = "请稍后重试"

/** 撤销成功后的回源口径：整页刷新回第 1 页，或重拉当前页。 */
internal enum class TrackingUndoReload {
    /** 回源到第 1 页并转下拉刷新指示（HyperOS 原行为）。 */
    FirstPage,

    /** 回源到取消时所在的页码，不转下拉指示（ColorOS 原行为）。 */
    CurrentPage,
}

/** 一次成功拉取的折算结果：落定的每页条数提示 + 这一页的 [PageSlice]。 */
internal data class TrackingPageLoad(
    val pageSizeHint: Int,
    val slice: PageSlice<TrackingItem>,
)

/**
 * 追踪列表一页的折算：接口只给总数、不给总页数，按累计条数对比总数收敛末页。
 *
 * [previousHint] 是上一页落下的每页条数提示；[hintGrowsWithLaterPages] 决定它在后续页上要不要跟着变大。
 * 空页由 [hasMoreByLoaded] 直接判到底，与旧页内实现逐字同义。
 */
internal fun trackingPageLoad(
    page: Int,
    previousHint: Int,
    items: List<TrackingItem>,
    total: Int,
    hintGrowsWithLaterPages: Boolean,
): TrackingPageLoad {
    val hint = when {
        page <= 1 -> items.size
        hintGrowsWithLaterPages -> previousHint.coerceAtLeast(items.size)
        else -> previousHint
    }
    val loaded = loadedThrough(page, hint, items.size)
    return TrackingPageLoad(
        pageSizeHint = hint,
        slice = PageSlice(
            items = items,
            hasMore = hasMoreByLoaded(items.size, loaded, total),
            total = total,
            page = page,
        ),
    )
}

/**
 * 追踪编排要的接口能力：取一页、取消/恢复追踪。
 *
 * 只开这两个方法，是为了让状态机能在纯 kotlin.test 里跑起来（假端口即可），
 * 不必构造真实的 [com.jm3.core.Jm3Gateway] 与 AuthCall。
 */
internal interface TrackingPort {
    suspend fun page(page: Int): SessionOutcome<TrackingPage>

    /** 取消追踪；撤销是同一个动作的第二次调用。 */
    suspend fun setTracking(albumId: String): SessionOutcome<Unit>
}

/** 追踪编排要的提示能力：一句话提示，以及带动作按钮、返回「用户是否点了动作」的提示。 */
internal interface TrackingNotices {
    suspend fun show(message: String)

    suspend fun showWithAction(message: String, actionLabel: String): Boolean
}

/**
 * 追踪页状态：loader 快照、取消追踪的在途标记、已取消条数与每页条数提示。
 *
 * 依赖以窄口注入（scope / [TrackingPort] / [TrackingNotices] / 两个口径参数），
 * 所以状态机能在纯 kotlin.test 里跑起来，不必构造 [ScreenDeps] 与两个皮肤。
 */
@Stable
internal class TrackingScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val tracking: TrackingPort,
    private val notices: TrackingNotices,
    private val hintGrowsWithLaterPages: Boolean,
    private val undoReload: TrackingUndoReload,
    private val onUnfollowed: () -> Unit = {},
) {
    /** 正在取消追踪的条目 id；null 表示没有在途请求。 */
    var removingId: String? by mutableStateOf(null)
        private set

    /** 本地已抹掉的行数：removeWhere 不回写 total，顶栏总数据此扣减。 */
    var removedCount: Int by mutableIntStateOf(0)
        private set

    /** 每页条数提示：接口按页返回且无总页数，翻页时用它估算已加载条数。 */
    var pageSizeHint: Int by mutableIntStateOf(0)
        private set

    private val loader = SessionListLoader(
        scope = scope,
        idOf = { item: TrackingItem -> item.id },
        failureMessage = TRACKING_FAILURE_MESSAGE,
        fetchPage = { page -> fetchPage(page) },
        notice = { message -> scope.launch { notices.show(message) } },
    )

    /** 列表快照；皮肤收集它即参与重组。 */
    val snapshot: StateFlow<SessionListSnapshot<TrackingItem>> = loader.snapshot

    /** 首屏与重试：清空列表并显示整屏加载态。 */
    fun loadFirstPage() {
        loader.loadFirstPage()
    }

    fun loadMore() {
        loader.loadMore()
    }

    fun refresh() {
        loader.refresh()
    }

    /**
     * 分页条上报的目标页收敛成真正要加载的页码；与当前页相同则返回 null（不重发请求）。
     *
     * 皮肤据此决定要不要在请求前归顶，所以收敛与「有没有变化」一起在这里给出。
     */
    fun pageToLoad(target: Int): Int? {
        val feed = loader.feed
        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
        return resolved.takeIf { it != feed.page }
    }

    /** 点击分页：整页替换到收敛后的目标页。 */
    fun goToPage(page: Int) {
        loader.goToPage(page)
    }

    /**
     * 取消追踪：在途时忽略重复点击；成功后本地抹行并给「撤销」，撤销成功后按 [undoReload] 回源。
     *
     * 会话失效只提示一次、不重试同一个请求（重试必然再吃一次 401）。
     */
    fun unfollow(item: TrackingItem) {
        if (removingId != null) return
        removingId = item.id
        // 撤销成功要回的页码取点击时的页码：撤销窗口里用户可能已经翻页
        val page = loader.feed.page
        scope.launch {
            val outcome = tracking.setTracking(item.id)
            removingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    onUnfollowed()
                    loader.removeWhere { it.id == item.id }
                    removedCount += 1
                    if (notices.showWithAction(TRACKING_UNFOLLOWED_MESSAGE, TRACKING_UNDO_LABEL)) {
                        undo(item, page)
                    }
                }

                SessionOutcome.Expired -> notices.show(SessionOutcome.Expired.message)

                is SessionOutcome.Failed -> notices.show(
                    "操作失败：" + failureCopy(outcome, TRACKING_RETRY_LATER),
                )
            }
        }
    }

    /**
     * 撤销：恢复追踪后回源一次。
     *
     * loader 只有整页替换与按条件抹除、没有回插通道，所以条目位置一律交给服务端顺序决定。
     */
    private suspend fun undo(item: TrackingItem, page: Int) {
        when (val undoOutcome = tracking.setTracking(item.id)) {
            is SessionOutcome.Ok -> when (undoReload) {
                TrackingUndoReload.FirstPage -> loader.refresh()
                TrackingUndoReload.CurrentPage -> loader.goToPage(page)
            }

            SessionOutcome.Expired -> notices.show(SessionOutcome.Expired.message)

            is SessionOutcome.Failed -> notices.show(
                "撤销失败：" + failureCopy(undoOutcome, TRACKING_RETRY_LATER),
            )
        }
    }

    /** 一页的三种结局：成功时先记账再折算，失效与失败原样透传给 loader。 */
    private suspend fun fetchPage(page: Int): SessionOutcome<PageSlice<TrackingItem>> =
        when (val outcome = tracking.page(page)) {
            is SessionOutcome.Ok -> {
                val load = trackingPageLoad(
                    page = page,
                    previousHint = pageSizeHint,
                    items = outcome.value.items,
                    total = outcome.value.total,
                    hintGrowsWithLaterPages = hintGrowsWithLaterPages,
                )
                pageSizeHint = load.pageSizeHint
                // 任一成功拉取都让本地抹除计数归零：服务端数据已经是准的
                removedCount = 0
                SessionOutcome.Ok(load.slice)
            }

            SessionOutcome.Expired -> SessionOutcome.Expired

            is SessionOutcome.Failed -> outcome
        }
}

/** 真实网关 + 鉴权闸门 → 追踪编排要的窄端口。 */
private fun ScreenDeps.asTrackingPort(): TrackingPort {
    val deps = this
    return object : TrackingPort {
        override suspend fun page(page: Int): SessionOutcome<TrackingPage> =
            deps.authCall.call { deps.gateway.trackingList(page) }

        override suspend fun setTracking(albumId: String): SessionOutcome<Unit> =
            deps.authCall.call { deps.gateway.setTracking(albumId) }
    }
}

/** 皮肤给的 Snackbar 宿主 → 追踪编排要的窄提示口（状态类本身不认识 Snackbar）。 */
private fun SnackbarHostState.asTrackingNotices(): TrackingNotices {
    val host = this
    return object : TrackingNotices {
        override suspend fun show(message: String) {
            host.showSnackbar(message)
        }

        override suspend fun showWithAction(message: String, actionLabel: String): Boolean =
            host.showSnackbar(message = message, actionLabel = actionLabel) == SnackbarResult.ActionPerformed
    }
}

/**
 * 追踪页唯一入口：接 [ScreenDeps] 与皮肤给的四个差异点，把 loader 与首屏加载挂成效果。
 *
 * @param hintGrowsWithLaterPages 每页条数提示是否跟着后续页变大
 * @param undoReload 撤销成功后的回源口径
 * @param onUnfollowed 取消成功后的皮肤钩子（ColorOS 侧触发触感，HyperOS 侧不传）
 */
@Composable
internal fun rememberTrackingScaffoldState(
    deps: ScreenDeps,
    snackbarHostState: SnackbarHostState,
    hintGrowsWithLaterPages: Boolean,
    undoReload: TrackingUndoReload,
    onUnfollowed: () -> Unit = {},
): TrackingScaffoldState {
    val scope = rememberCoroutineScope()
    // 账号或分页模式一变就换一个 loader：新实例从空列表起，旧账号的条目不会残留在屏上
    val mode = deps.paginationMode
    val user by deps.account.user.collectAsState()
    val followHaptic by rememberUpdatedState(onUnfollowed)
    val state = remember(mode, user?.uid, snackbarHostState, hintGrowsWithLaterPages, undoReload) {
        TrackingScaffoldState(
            scope = scope,
            tracking = deps.asTrackingPort(),
            notices = snackbarHostState.asTrackingNotices(),
            hintGrowsWithLaterPages = hintGrowsWithLaterPages,
            undoReload = undoReload,
            onUnfollowed = { followHaptic() },
        )
    }
    LaunchedEffect(state) { state.loadFirstPage() }
    return state
}

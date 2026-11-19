package com.jm3.app.miuix.ui.watchhistory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.pageLooksFull
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/*
 * 观看历史页状态编排骨架（C4 双皮肤去重）。
 *
 * 抽出来的是行为：loader 的构造与首屏加载、一页返回折算成 PageSlice 的口径、
 * 删除条目的在途守卫与三种结局、删除二次确认的挂起/取消/确认。
 * 皮肤侧只留自己的列表/栅格控件、行卡样式、顶栏、空/错/加载态与弹窗外观；
 * 本文件不引用任何 Miuix/COUI 控件。
 *
 * 两皮肤在这一页没有任何行为差异：文案、事件顺序、失效时的登录入口都逐字相同，
 * 差异全在视觉（HyperOS 列表 + OverlayDialog / ColorOS 栅格 + Dialog）。
 */

/** 服务端观看记录单页条数；接口不返回总数，用返回条数推断是否还有下一页。 */
internal const val WATCH_PAGE_SIZE = 20

/** 观看记录加载失败的兜底文案，两皮肤共用。 */
internal const val WATCH_FAILURE_MESSAGE = "观看记录加载失败"

/** 删除成功提示与删除确认弹窗的文案。 */
internal const val WATCH_DELETED_MESSAGE = "已删除观看记录"
internal const val WATCH_DELETE_TITLE = "删除观看记录"
internal const val WATCH_DELETE_CONFIRM = "删除"
internal const val WATCH_DELETE_DISMISS = "取消"

/** 删除失败提示前缀与兜底文案；[com.jm3.app.miuix.ui.coui.theme.CouiStrings.RetryLater] 与兜底同值。 */
internal const val WATCH_DELETE_FAILURE_PREFIX = "删除失败："
internal const val WATCH_RETRY_LATER = "请稍后重试"

/** 删除确认正文：书名原样嵌入，与服务端一致。 */
internal fun watchDeleteMessage(item: SearchResult): String =
    "确定要删除《" + item.title + "》的观看记录吗？"

/**
 * 观看记录一页折算：接口不返回总数，用本页条数推断是否还有下一页。
 *
 * 会话失效与普通失败原样透传，屏幕据此弹登录入口或重试文案。
 */
internal fun SessionOutcome<List<SearchResult>>.toWatchSlice(
    page: Int,
    pageSize: Int = WATCH_PAGE_SIZE,
): SessionOutcome<PageSlice<SearchResult>> = when (this) {
    is SessionOutcome.Ok -> SessionOutcome.Ok(
        PageSlice(
            items = value,
            hasMore = pageLooksFull(value.size, pageSize),
            page = page,
        ),
    )

    SessionOutcome.Expired -> SessionOutcome.Expired

    is SessionOutcome.Failed -> this
}

/**
 * 观看历史编排要的网关能力：取一页、删除一条。
 *
 * 只开这两个方法，是为了让状态机能在纯 kotlin.test 里跑起来（假端口即可），
 * 不必构造真实的 [com.jm3.core.Jm3Gateway] 与 AuthCall。
 */
internal interface WatchHistoryPort {
    suspend fun page(page: Int): SessionOutcome<List<SearchResult>>

    suspend fun delete(id: String): SessionOutcome<Unit>
}

/** 观看历史编排要的提示能力：一句话提示。 */
internal interface WatchHistoryNotices {
    suspend fun show(message: String)
}

/**
 * 观看历史页状态：loader 快照、删除条目的在途标记与待确认删除项。
 *
 * 依赖以窄口注入（scope / [WatchHistoryPort] / [WatchHistoryNotices] / 失效时的登录入口），
 * 所以状态机能在纯 kotlin.test 里跑起来，不必构造 [ScreenDeps] 与两个皮肤。
 */
@Stable
internal class WatchHistoryScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val history: WatchHistoryPort,
    private val notices: WatchHistoryNotices,
    private val onSessionExpired: () -> Unit,
) {
    /** 正在删除的条目 id；null 表示没有在途请求。 */
    var deletingId: String? by mutableStateOf(null)
        private set

    /** 待确认删除的条目；null 表示没有挂起项。 */
    var pendingDeleteItem: SearchResult? by mutableStateOf(null)
        private set

    private val loader = SessionListLoader(
        scope = scope,
        idOf = { item: SearchResult -> item.id },
        failureMessage = WATCH_FAILURE_MESSAGE,
        fetchPage = { page -> history.page(page).toWatchSlice(page) },
        notice = { message -> scope.launch { notices.show(message) } },
    )

    /** 列表快照；皮肤收集它即参与重组。 */
    val snapshot: StateFlow<SessionListSnapshot<SearchResult>> = loader.snapshot

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

    /** 点击分页：按目标页整页替换。 */
    fun goToPage(page: Int) {
        loader.goToPage(page)
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

    /** 点行尾删除：先挂起，等二次确认。 */
    fun requestDelete(item: SearchResult) {
        pendingDeleteItem = item
    }

    /** 取消删除：只清挂起项，不发请求。 */
    fun dismissDelete() {
        pendingDeleteItem = null
    }

    /** 二次确认：先发删除（在途标记同步落下），再清挂起项，顺序与两皮肤原实现一致。 */
    fun confirmDelete() {
        val target = pendingDeleteItem ?: return
        delete(target)
        pendingDeleteItem = null
    }

    /**
     * 删除一条：在途时忽略重复点击；成功后本地抹行并提示。
     *
     * 删除是账号态动作：会话失效时提示一次并直接交皮肤跳登录，不让用户对着按钮重试。
     */
    private fun delete(item: SearchResult) {
        if (deletingId != null) return
        deletingId = item.id
        scope.launch {
            val outcome = history.delete(item.id)
            deletingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    loader.removeWhere { it.id == item.id }
                    notices.show(WATCH_DELETED_MESSAGE)
                }

                SessionOutcome.Expired -> {
                    notices.show(SessionOutcome.Expired.message)
                    onSessionExpired()
                }

                is SessionOutcome.Failed -> notices.show(
                    WATCH_DELETE_FAILURE_PREFIX + failureCopy(outcome, WATCH_RETRY_LATER),
                )
            }
        }
    }
}

/** 真实网关 + 鉴权闸门 → 观看历史编排要的窄端口。 */
private fun ScreenDeps.asWatchHistoryPort(): WatchHistoryPort {
    val deps = this
    return object : WatchHistoryPort {
        override suspend fun page(page: Int): SessionOutcome<List<SearchResult>> =
            deps.authCall.call { deps.gateway.watchHistory(page) }

        override suspend fun delete(id: String): SessionOutcome<Unit> =
            deps.authCall.call { deps.gateway.deleteWatchHistory(id) }
    }
}

/** 皮肤给的 Snackbar 宿主 → 观看历史编排要的窄提示口（状态类本身不认识 Snackbar）。 */
private fun SnackbarHostState.asWatchHistoryNotices(): WatchHistoryNotices {
    val host = this
    return object : WatchHistoryNotices {
        override suspend fun show(message: String) {
            host.showSnackbar(message)
        }
    }
}

/**
 * 观看历史页唯一入口：接 [ScreenDeps]，把 loader 与首屏加载挂成效果。
 *
 * @param onOpenLogin 会话失效时打开登录屏（两皮肤都指向同一个路由）
 */
@Composable
internal fun rememberWatchHistoryScaffoldState(
    deps: ScreenDeps,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit,
): WatchHistoryScaffoldState {
    val scope = rememberCoroutineScope()
    // 账号或分页模式一变就换一个 loader：新实例从空列表起，旧账号的条目不会残留在屏上
    val mode = deps.paginationMode
    val user by deps.account.user.collectAsState()
    val state = remember(mode, user?.uid, snackbarHostState) {
        WatchHistoryScaffoldState(
            scope = scope,
            history = deps.asWatchHistoryPort(),
            notices = snackbarHostState.asWatchHistoryNotices(),
            onSessionExpired = onOpenLogin,
        )
    }
    LaunchedEffect(state) { state.loadFirstPage() }
    return state
}

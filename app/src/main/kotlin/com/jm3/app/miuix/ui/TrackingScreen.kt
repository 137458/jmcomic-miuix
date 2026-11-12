package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.model.TrackingItem
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import java.time.format.DateTimeFormatter

private sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Loaded : TrackingUiState
    // sessionExpired 区分「重试可能好」和「会话没了」：后者只能去登录，给重试按钮是骗人的
    data class Failed(val message: String, val sessionExpired: Boolean = false) : TrackingUiState
}

/** 连载追踪：已关注作品的更新列表，支持一键取消关注。 */
@Composable
fun TrackingScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    // 在途、失败、是否还有下一页、累计总数都由 PagedFeed 记，页面不再各存一份布尔
    var feed by remember { mutableStateOf(PagedFeed<TrackingItem>()) }
    var state by remember { mutableStateOf<TrackingUiState>(TrackingUiState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var removingId by remember { mutableStateOf<String?>(null) }
    val mode = deps.preferences.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    var pageSizeHint by remember { mutableIntStateOf(0) }
    // 翻页在途守卫：请求期间禁用翻页条，避免连点重复请求
    var pageBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /**
     * 失效与失败共用一个落点：有内容就保留列表只提示，没内容才换整屏。
     *
     * 失效时 AuthCall 已在返回结局前清掉本地会话，整屏只能去登录，重试同一个请求只会再吃一次 401。
     */
    fun applyFailure(outcome: SessionOutcome<*>) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = feed.items.isNotEmpty(),
            failureMessage = "追踪列表加载失败",
        )
        if (plan.keepContent) {
            scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
        } else {
            state = TrackingUiState.Failed(
                message = plan.message.orEmpty(),
                sessionExpired = plan.phase == ScreenLoadPhase.Expired,
            )
        }
    }

    fun loadFirstPage(showLoading: Boolean = true) {
        if (showLoading) state = TrackingUiState.Loading
        scope.launch {
            // 启动闸门、IO 切线程与失效通知都在 AuthCall 里，这里只关心三种结局
            val outcome = deps.authCall.call { deps.gateway.trackingList(1) }
            refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val page = outcome.value
                    pageSizeHint = page.items.size
                    feed = PagedFeed<TrackingItem>().replace(
                        PageSlice(
                            items = page.items,
                            hasMore = page.items.isNotEmpty() && page.items.size < page.total,
                            total = page.total,
                            page = 1,
                        ),
                    ) { it.id }
                    state = TrackingUiState.Loaded
                }

                SessionOutcome.Expired -> applyFailure(outcome)

                is SessionOutcome.Failed -> applyFailure(outcome)
            }
        }
    }

    LaunchedEffect(Unit) { loadFirstPage() }
    PaginationModeReloadEffect(mode) { loadFirstPage() }

    /** 点击分页：按目标页整页替换；接口只给总数，用首页条数估算页码容量。 */
    fun jumpToPage(target: Int) {
        if (pageBusy) return
        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
        if (resolved == feed.page) return
        pageBusy = true
        scope.launch {
            try {
                val outcome = deps.authCall.call { deps.gateway.trackingList(resolved) }
                refreshing = false
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val page = outcome.value
                        val sizeHint = pageSizeHint.coerceAtLeast(page.items.size)
                        val seen = (resolved - 1).coerceAtLeast(0) * sizeHint + page.items.size
                        feed = PagedFeed<TrackingItem>().replace(
                            PageSlice(
                                items = page.items,
                                hasMore = page.items.isNotEmpty() && seen < page.total,
                                total = page.total,
                                page = resolved,
                            ),
                        ) { it.id }
                        state = TrackingUiState.Loaded
                        listState.scrollToItem(0)
                    }

                    SessionOutcome.Expired -> applyFailure(outcome)

                    is SessionOutcome.Failed -> applyFailure(outcome)
                }
            } finally {
                pageBusy = false
            }
        }
    }

    fun loadMore() {
        if (state != TrackingUiState.Loaded) return
        val requested = feed.beginLoadMore()
        if (requested === feed) return
        val page = requested.page + 1
        feed = requested
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.trackingList(page) }
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val next = outcome.value
                    // 接口按页返回且无总页数，按累计条数对比总数收敛；空页直接视为到底
                    val merged = (requested.items + next.items).distinctBy { it.id }
                    feed = requested.append(
                        PageSlice(
                            items = next.items,
                            hasMore = next.items.isNotEmpty() && merged.size < next.total,
                            total = next.total.coerceAtLeast(requested.total ?: 0),
                            page = page,
                        ),
                    ) { it.id }
                }

                SessionOutcome.Expired -> applyFailure(outcome)

                is SessionOutcome.Failed -> feed = requested.failLoadMore()
            }
        }
    }

    fun unfollow(item: TrackingItem) {
        if (removingId != null) return
        removingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.setTracking(item.id) }
            removingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val slice = feed.items.removeMatching { it.id == item.id }
                    feed = feed.copy(
                        items = slice.remaining,
                        total = feed.total?.minus(slice.removed.size)?.coerceAtLeast(slice.remaining.size),
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = "已取消追踪",
                        actionLabel = "撤销",
                    )
                    if (result == SnackbarResult.ActionPerformed && slice.removed.isNotEmpty()) {
                        when (val undoOutcome = deps.authCall.call { deps.gateway.setTracking(item.id) }) {
                            is SessionOutcome.Ok -> {
                                feed = feed.copy(
                                    items = feed.items.restoreAt(slice.removed, slice.insertIndex) { it.id },
                                    total = feed.total?.plus(slice.removed.size),
                                )
                            }
                            SessionOutcome.Expired -> applyFailure(undoOutcome)
                            is SessionOutcome.Failed -> {
                                snackbarHostState.showSnackbar("撤销失败：" + undoOutcome.messageOr("请稍后重试"))
                            }
                        }
                    }
                }

                SessionOutcome.Expired -> applyFailure(outcome)

                is SessionOutcome.Failed -> {
                    snackbarHostState.showSnackbar("操作失败：" + outcome.messageOr("请稍后重试"))
                }
            }
        }
    }

    // 触底加载只在无限滚动模式生效；点击翻页由列表底部翻页条驱动。
    if (!clickPaging) {
        LoadMoreOnScroll(listState = listState, feed = feed, onLoadMore = ::loadMore)
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "连载追踪",
                subtitle = if (feed.items.isEmpty()) "" else "共 ${feed.total ?: feed.items.size} 部",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            TrackingUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is TrackingUiState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = { loadFirstPage() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            TrackingUiState.Loaded -> if (feed.items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无追踪",
                    hint = "在详情页开启追更后会出现在这里",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                GlassPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        loadFirstPage(showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    // 格式化器整页一份：放进行项内会让滚动进出反复构造 DateTimeFormatter
                    val dateFormat = rememberDateFormatter()
                    // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
                    val feedColumns = LocalWideLayout.current.feedColumns
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                    ) {
                        // key 仍是作品 id：与单列时逐字一致，升级后滚动位置不丢
                        feedRowItems(
                            count = feed.items.size,
                            columns = feedColumns,
                            keyPrefix = "",
                            keyOf = { index -> feed.items[index].id },
                        ) { index ->
                            val item = feed.items[index]
                            WorkRowCard(
                                coverUrl = item.coverUrl,
                                title = item.name,
                                onClick = { onOpenAlbum(item.id) },
                                albumId = item.id,
                                subtitle = item.updateAt?.toReadableTime(dateFormat),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                textEndPadding = 4.dp,
                                trailing = {
                                    IconButton(
                                        onClick = { unfollow(item) },
                                        enabled = removingId != item.id,
                                    ) {
                                        Icon(MiuixIcons.Delete, contentDescription = "取消追踪")
                                    }
                                },
                            ) {
                                WorkCardMetaRow(tags = emptyList(), albumCode = item.id)
                            }
                        }
                        if (feed.items.isNotEmpty()) {
                            item(key = "pagination-footer") {
                                if (clickPaging) {
                                    PagerBar(
                                        page = feed.page,
                                        hasMore = feed.hasMore,
                                        enabled = !pageBusy,
                                        onPageChange = ::jumpToPage,
                                    )
                                } else {
                                    LoadMoreFooter(feed = feed, onLoadMore = ::loadMore)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Unix 秒字符串转本地时间；非数字原样返回，避免吞掉服务端文案。 */
private fun String.toReadableTime(dateFormat: DateTimeFormatter): String =
    toLongOrNull()?.let { formatEpochSeconds(it, dateFormat).ifBlank { this } } ?: this

/**
 * 会话失效全屏态：文案位置与排版同 [ErrorScreen]，但动作是「去登录」而不是「重试」——
 * 会话在服务端已经作废，重试同一个请求只会再吃一次 401。
 *
 * 跨屏幕复用（连载追踪、收藏页），所以做成同包的 internal 顶层函数而不是各页私有实现。
 * 暂不并回 Common.kt：[ErrorScreen] 的按钮语义固定为「重试」，没有承载自定义动作的位置；
 * 等 Common.kt 提供带动作的全屏态时，两者应合并。
 */
@Composable
internal fun LoginExpiredScreen(onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // 失效文案只有一份，存在结局类型上：屏幕不再各自 import 文案常量
            text = SessionOutcome.Expired.message,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(text = "去登录", onClick = onOpenLogin)
    }
}

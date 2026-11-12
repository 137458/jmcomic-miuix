// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import top.yukonga.miuix.kmp.basic.SnackbarResult
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.formatEpochSeconds
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberDateFormatter
import com.jm3.app.miuix.ui.rememberSelectionHaptic
import com.jm3.app.miuix.ui.removeMatching
import com.jm3.app.miuix.ui.restoreAt
import com.jm3.core.model.TrackingItem
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 追更页自己的三态：条目不是 [com.jm3.core.model.SearchResult]，用不了首页那份会话缓存状态。 */
private sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Loaded : TrackingUiState
    data class Failed(val message: String, val sessionExpired: Boolean = false) : TrackingUiState
}

/**
 * COUI 连载追踪页：分组行卡 + 分页，签名与原页面一致。
 *
 * 分页两种模式都保留：点击分页给 [CouiPagingGroup]，滚动分页给
 * [CouiFeedPrefetchEffect] 的触底预取，切换模式会按旧页语义回到对应页码。
 */
@Composable
internal fun CouiTrackingScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    val haptic = rememberSelectionHaptic()
    val dateFormat = rememberDateFormatter()
    val mode = deps.preferences.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()

    var feed by remember { mutableStateOf(PagedFeed<TrackingItem>()) }
    var state by remember { mutableStateOf<TrackingUiState>(TrackingUiState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var removingId by remember { mutableStateOf<String?>(null) }
    var pageBusy by remember { mutableStateOf(false) }
    // 首页条数：非末页时用来估算当前已浏览的条数，决定 hasMore
    var pageSizeHint by remember { mutableIntStateOf(0) }

    fun applyFailure(outcome: SessionOutcome<*>) {
        val plan = planLoad(
            outcome,
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
                else -> applyFailure(outcome)
            }
        }
    }

    fun jumpToPage(target: Int) {
        if (pageBusy) return
        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
        if (resolved == feed.page) return
        pageBusy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.trackingList(resolved) }
            pageBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val page = outcome.value
                    pageSizeHint = pageSizeHint.coerceAtLeast(page.items.size)
                    val seen = (resolved - 1).coerceAtLeast(0) * pageSizeHint + page.items.size
                    feed = feed.replace(
                        PageSlice(
                            items = page.items,
                            hasMore = page.items.isNotEmpty() && seen < page.total,
                            total = page.total,
                            page = resolved,
                        ),
                    ) { it.id }
                    listState.scrollToItem(0)
                }
                else -> applyFailure(outcome)
            }
        }
    }

    fun loadMore() {
        if (state != TrackingUiState.Loaded) return
        if (refreshing) return
        val requested = feed.beginLoadMore()
        if (requested === feed) return
        val nextPage = requested.page + 1
        feed = requested
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.trackingList(nextPage) }
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val page = outcome.value
                    val merged = (requested.items + page.items).distinctBy { it.id }
                    feed = requested.append(
                        PageSlice(
                            items = page.items,
                            hasMore = page.items.isNotEmpty() && merged.size < page.total,
                            total = page.total.coerceAtLeast(requested.total ?: 0),
                            page = nextPage,
                        ),
                    ) { it.id }
                }
                else -> feed = requested.failLoadMore()
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
                    haptic()
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
                            is SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                                planLoad(undoOutcome, hasContent = false, failureMessage = "撤销失败").message.orEmpty(),
                            )
                            is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                                "撤销失败：" + failureCopy(undoOutcome.cause, CouiStrings.RetryLater),
                            )
                        }
                    }
                }
                is SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "操作失败").message.orEmpty(),
                )
                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "操作失败：" + failureCopy(outcome.cause, CouiStrings.RetryLater),
                )
            }
        }
    }

    LaunchedEffect(user?.uid) { loadFirstPage() }
    PaginationModeReloadEffect(mode) { loadFirstPage() }
    // 只有滚动分页才预取：点击分页下提前拉下一页会让页码与内容对不上
    CouiFeedPrefetchEffect(
        feed = feed,
        rowStart = 0,
        listState = listState,
        enabled = !clickPaging,
        onPrefetch = { loadMore() },
    )

    CouiTheme {
        val overscroll = rememberCouiOverscrollEffect()
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val items = feed.items

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    LargeTopAppBar(
                        title = { CouiTopBarTitle(text = "连载追踪", textAlign = TextAlign.Start) },
                        navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                        actions = {
                            CouiFeedRefreshAction(
                                refreshing = refreshing,
                                onRefresh = {
                                    if (!refreshing) {
                                        refreshing = true
                                        loadFirstPage(showLoading = false)
                                    }
                                },
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = couiTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
                // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
                val feedColumns = LocalWideLayout.current.feedColumns
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .consumeWindowInsets(innerPadding),
                    state = listState,
                    contentPadding = couiScreenContentPadding(scrollBehavior, innerPadding),
                    overscrollEffect = overscroll,
                ) {
                    if (items.isNotEmpty()) {
                        item(key = "tracking-subtitle") {
                            Footer(text = "共 " + (feed.total ?: items.size) + " 部")
                        }
                    }
                    when (val current = state) {
                        TrackingUiState.Loading -> item(key = "tracking-loading") {
                            CouiLoadingGroup(
                                modifier = Modifier.padding(top = 8.dp),
                                text = "正在加载追踪列表…",
                            )
                        }
                        is TrackingUiState.Failed -> if (current.sessionExpired) {
                            item(key = "tracking-expired") {
                                CouiFeedLoginGroup(message = current.message, onOpenLogin = onOpenLogin)
                            }
                        } else {
                            item(key = "tracking-error") {
                                CouiErrorGroup(
                                    message = current.message,
                                    onRetry = { loadFirstPage() },
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        TrackingUiState.Loaded -> if (items.isEmpty()) {
                            item(key = "tracking-empty") {
                                CouiEmptyGroup(
                                    text = "暂无追踪",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            item(key = "tracking-empty-hint") { Footer(text = "在详情页开启追更后会出现在这里") }
                        } else {
                            feedRowItems(
                                count = items.size,
                                columns = feedColumns,
                                keyPrefix = "tracking-",
                                keyOf = { index -> items[index].id },
                            ) { index ->
                                val item = items[index]
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = CouiPageDefaults.horizontalMargin,
                                            vertical = CouiWorkCardTokens.RowSpacing / 2,
                                        ),
                                ) {
                                    CouiWorkRowCard(
                                        coverUrl = item.coverUrl,
                                        title = item.name,
                                        onClick = { onOpenAlbum(item.id) },
                                        albumId = item.id,
                                        subtitle = item.updateAt.toReadableUpdateTime(dateFormat),
                                        trailing = {
                                            TrackingCancelAction(
                                                busy = removingId == item.id,
                                                onClick = { unfollow(item) },
                                            )
                                        },
                                    )
                                }
                            }
                            item(key = "tracking-footer") {
                                if (clickPaging) {
                                    CouiPagingGroup(
                                        page = feed.page,
                                        pageCount = feed.pageCount,
                                        hasMore = feed.hasMore,
                                        onJumpPage = { jumpToPage(it) },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                } else {
                                    CouiLoadMoreFooter(
                                        feed = feed,
                                        errorMessage = CouiStrings.LoadFailed,
                                        onRetry = { loadMore() },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                            item(key = "tracking-tail") {
                                Spacer(modifier = Modifier.size(CouiPageDefaults.groupSpacing))
                            }
                        }
                    }
                }
            }

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

/** 秒级时间戳转可读时间；解析不出来就原样展示，不吞掉服务端给的值。 */
private fun String?.toReadableUpdateTime(dateFormat: java.time.format.DateTimeFormatter): String? {
    if (this.isNullOrBlank()) return null
    return toLongOrNull()?.let { epoch -> formatEpochSeconds(epoch, dateFormat).ifBlank { this } } ?: this
}

/** 行尾取消入口：图标按钮补到 48dp 触区，滑动删除不做（追踪列表误触代价高）。 */
@Composable
private fun TrackingCancelAction(busy: Boolean, onClick: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(shape = CircleShape, enabled = !busy, minTouchTarget = CouiMinTouchTarget, onClick = onClick)
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CouiIcons.Close,
            contentDescription = "取消追踪",
            modifier = Modifier.size(18.dp),
            tint = scheme.onSurfaceVariant,
        )
    }
}

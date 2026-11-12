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
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
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
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.model.TrackingItem
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.hasMoreByLoaded
import com.jm3.core.paging.loadedThrough
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

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
    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()

    var removingId by remember { mutableStateOf<String?>(null) }
    // 首页条数：非末页时用来估算当前已浏览的条数，决定 hasMore
    var pageSizeHint by remember { mutableIntStateOf(0) }
    // removeWhere 只删条目、不回写 total：取消追踪后本地先扣一个，任一次成功拉取即清零
    var removedCount by remember { mutableIntStateOf(0) }

    val loader = remember(mode, user?.uid, snackbarHostState) {
        SessionListLoader(
            scope = scope,
            idOf = { item: TrackingItem -> item.id },
            failureMessage = "追踪列表加载失败",
            fetchPage = { page ->
                when (val outcome = deps.authCall.call { deps.gateway.trackingList(page) }) {
                    is SessionOutcome.Ok -> {
                        val value = outcome.value
                        pageSizeHint = if (page == 1) {
                            value.items.size
                        } else {
                            pageSizeHint.coerceAtLeast(value.items.size)
                        }
                        removedCount = 0
                        SessionOutcome.Ok(
                            PageSlice(
                                items = value.items,
                                hasMore = hasMoreByLoaded(
                                    value.items.size,
                                    loadedThrough(page, pageSizeHint, value.items.size),
                                    value.total,
                                ),
                                total = value.total,
                                page = page,
                            ),
                        )
                    }

                    SessionOutcome.Expired -> SessionOutcome.Expired
                    is SessionOutcome.Failed -> outcome
                }
            },
            notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    val feed = snapshot.feed

    fun unfollow(item: TrackingItem) {
        if (removingId != null) return
        removingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.setTracking(item.id) }
            removingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    haptic()
                    loader.removeWhere { it.id == item.id }
                    removedCount += 1
                    val result = snackbarHostState.showSnackbar(
                        message = "已取消追踪",
                        actionLabel = "撤销",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        when (val undoOutcome = deps.authCall.call { deps.gateway.setTracking(item.id) }) {
                            // loader 只有 removeWhere、没有插回通道：撤销成功后重拉当前页，条目回到原位
                            is SessionOutcome.Ok -> loader.goToPage(feed.page)
                            is SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                                planLoad(undoOutcome, hasContent = false, failureMessage = "撤销失败").message.orEmpty(),
                            )
                            is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                                "撤销失败：" + failureCopy(undoOutcome, CouiStrings.RetryLater),
                            )
                        }
                    }
                }
                is SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "操作失败").message.orEmpty(),
                )
                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "操作失败：" + failureCopy(outcome, CouiStrings.RetryLater),
                )
            }
        }
    }

    // 账号或翻页模式变化时 remember 会重建 loader，这里只负责首屏加载
    LaunchedEffect(loader) { loader.loadFirstPage() }
    // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
    val feedColumns = LocalWideLayout.current.feedColumns
    // 只有滚动分页才预取：点击分页下提前拉下一页会让页码与内容对不上
    CouiFeedPrefetchEffect(
        feed = feed,
        rowStart = if (feed.items.isNotEmpty()) 1 else 0,
        listState = listState,
        columns = feedColumns,
        enabled = !clickPaging,
        onPrefetch = loader::loadMore,
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
                                refreshing = snapshot.refreshing,
                                onRefresh = loader::refresh,
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = couiTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
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
                            val shownTotal = feed.total?.minus(removedCount)?.coerceAtLeast(items.size)
                                ?: items.size
                            Footer(text = "共 " + shownTotal + " 部")
                        }
                    }
                    when (val current = snapshot.state) {
                        ListLoadState.Loading -> item(key = "tracking-loading") {
                            CouiLoadingGroup(
                                modifier = Modifier.padding(top = 8.dp),
                                text = "正在加载追踪列表…",
                            )
                        }
                        is ListLoadState.Failed -> item(key = "tracking-error") {
                            CouiScreenFailureGroup(
                                message = current.message,
                                expired = current.sessionExpired,
                                onRetry = loader::loadFirstPage,
                                onOpenLogin = onOpenLogin,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        ListLoadState.Loaded -> if (items.isEmpty()) {
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
                                        onJumpPage = { target ->
                                            val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
                                            if (resolved != feed.page) {
                                                scope.launch {
                                                    listState.scrollToItem(0)
                                                    loader.goToPage(resolved)
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                } else {
                                    CouiLoadMoreFooter(
                                        feed = feed,
                                        errorMessage = CouiStrings.LoadFailed,
                                        onRetry = loader::loadMore,
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

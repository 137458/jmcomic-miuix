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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.formatEpochSeconds
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberDateFormatter
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.tracking.TrackingUndoReload
import com.jm3.app.miuix.ui.tracking.rememberTrackingScaffoldState
import com.jm3.core.settings.PaginationMode
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * COUI 连载追踪页：分组行卡 + 分页，签名与原页面一致。
 *
 * 加载编排、取消追踪与撤销都在 TrackingScaffoldState；本屏只留 ColorOS 的视觉与两处皮肤差异：
 * 每页条数提示随后续页变大（非末页估算已浏览条数），取消成功触发一次触感。
 * 分页两种模式都保留：点击分页给 [CouiPagingGroup]，滚动分页给
 * [CouiFeedPrefetchEffect] 的触底预取，切换模式会按旧页语义回到对应页码。
 */
@Composable
internal fun CouiTrackingScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val haptic = rememberSelectionHaptic()
    val dateFormat = rememberDateFormatter()
    val mode = deps.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()

    val state = rememberTrackingScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        hintGrowsWithLaterPages = true,
        undoReload = TrackingUndoReload.CurrentPage,
        onUnfollowed = haptic,
    )
    val snapshot by state.snapshot.collectAsState()
    val feed = snapshot.feed
    val feedColumns = LocalWideLayout.current.feedColumns
    CouiFeedPrefetchEffect(
        feed = feed,
        rowStart = if (feed.items.isNotEmpty()) 1 else 0,
        listState = listState,
        columns = feedColumns,
        enabled = !clickPaging,
        onPrefetch = state::loadMore,
    )

    CouiTheme {
        val scrollBehavior = rememberCouiPageScrollBehavior()
        val items = feed.items

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = "连载追踪",
                onBack = onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
                actions = {
                    CouiFeedRefreshAction(
                        refreshing = snapshot.refreshing,
                        onRefresh = state::refresh,
                    )
                },
            ) { contentPadding ->
                CouiPullToRefresh(
                    isRefreshing = snapshot.refreshing,
                    onRefresh = state::refresh,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .couiPageScroll()
                            .consumeWindowInsets(contentPadding),
                        state = listState,
                        contentPadding = contentPadding,
                        flingBehavior = rememberCOUIFlingBehavior(),
                    ) {
                        if (items.isNotEmpty()) {
                            item(key = "tracking-subtitle") {
                                val shownTotal = feed.total?.minus(state.removedCount)?.coerceAtLeast(items.size)
                                    ?: items.size
                                PagerFooterPreference(
                                    summary = "共 " + shownTotal + " 部",
                                    withExtraMarginBottom = false,
                                )
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
                                    onRetry = state::loadFirstPage,
                                    onOpenLogin = nav::login,
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
                                item(key = "tracking-empty-hint") {
                                    PagerFooterPreference(
                                        summary = "在详情页开启追更后会出现在这里",
                                        withExtraMarginBottom = false,
                                    )
                                }
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
                                                horizontal = CouiLayoutTokens.HorizontalMargin,
                                                vertical = CouiWorkCardTokens.RowSpacing / 2,
                                            ),
                                    ) {
                                        CouiWorkRowCard(
                                            coverUrl = item.coverUrl,
                                            title = item.name,
                                            onClick = { nav.album(item.id) },
                                            albumId = item.id,
                                            subtitle = item.updateAt.toReadableUpdateTime(dateFormat),
                                            trailing = {
                                                TrackingCancelAction(
                                                    busy = state.removingId == item.id,
                                                    onClick = { state.unfollow(item) },
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
                                                val resolved = state.pageToLoad(target)
                                                if (resolved != null) {
                                                    scope.launch {
                                                        listState.scrollToItem(0)
                                                        state.goToPage(resolved)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    } else {
                                        CouiLoadMoreFooter(
                                            feed = feed,
                                            errorMessage = CouiStrings.LoadFailed,
                                            onRetry = state::loadMore,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                }
                                item(key = "tracking-tail") {
                                    Spacer(modifier = Modifier.size(CouiLayoutTokens.GroupSpacing))
                                }
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
    val scheme = COUITheme.colorScheme
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
            tint = scheme.labelSecondary,
        )
    }
}

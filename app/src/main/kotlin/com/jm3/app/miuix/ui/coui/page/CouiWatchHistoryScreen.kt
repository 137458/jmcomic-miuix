// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.authorNames
import com.jm3.app.miuix.ui.coui.component.CouiAutoLoadMoreEffect
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 观看记录每页条数：与旧页保持一致，hasMore 用它反推。 */
private const val WATCH_PAGE_SIZE = 20

private sealed interface CouiWatchUiState {
    data object Loading : CouiWatchUiState

    data object Loaded : CouiWatchUiState

    data class Failed(
        val message: String,
        val sessionExpired: Boolean = false,
    ) : CouiWatchUiState
}

/**
 * 观看记录（COUI 栅格版）：参数与旧 Miuix 页一比一，数据、分页与删除逻辑沿用旧页口径，
 * 只把行卡换成栅格卡。
 *
 * 栅格触底不用预取组件（COUI 预取只支持 LazyListState），改为页脚项进入组合即取下一页；
 * beginLoadMore 自带防抖，重复触发不会重发请求。
 */
@Composable
fun CouiWatchHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    CouiTheme {
        val scope = rememberCoroutineScope()
        val mode = deps.preferences.paginationMode
        val clickPaging = mode == PaginationMode.CLICK
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns

        var feed by remember { mutableStateOf(PagedFeed<SearchResult>()) }
        var uiState by remember { mutableStateOf<CouiWatchUiState>(CouiWatchUiState.Loading) }
        var deletingId by remember { mutableStateOf<String?>(null) }
        var pendingDeleteItem by remember { mutableStateOf<SearchResult?>(null) }

        fun applyFailure(outcome: SessionOutcome<*>, inFlight: PagedFeed<SearchResult>, replace: Boolean) {
            val plan = planLoad(
                outcome = outcome,
                hasContent = !replace && inFlight.items.isNotEmpty(),
                failureMessage = "观看记录加载失败",
                stalePrefix = "加载失败：",
            )
            feed = if (plan.keepContent) inFlight.failLoadMore() else inFlight.copy(loadingMore = false)
            if (plan.keepContent) {
                scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
            } else {
                uiState = CouiWatchUiState.Failed(
                    message = plan.message.orEmpty(),
                    sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                )
            }
        }

        fun fetch(targetPage: Int, replace: Boolean, showLoading: Boolean) {
            if (!replace && !feed.canLoadMore) return
            val requested = if (replace) feed else feed.beginLoadMore()
            // beginLoadMore 被拦下时返回同一实例：已经在拉或已到底，不再重复触发
            if (!replace && requested === feed) return
            if (!replace) {
                feed = requested
            } else if (showLoading) {
                uiState = CouiWatchUiState.Loading
            }
            scope.launch {
                val outcome = deps.authCall.call { deps.gateway.watchHistory(targetPage) }
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val items = outcome.value
                        val slice = PageSlice(
                            items = items,
                            hasMore = items.size >= WATCH_PAGE_SIZE,
                            page = targetPage,
                        )
                        feed = if (replace) {
                            PagedFeed<SearchResult>().replace(slice) { it.id }
                        } else {
                            requested.append(slice) { it.id }
                        }
                        uiState = CouiWatchUiState.Loaded
                    }

                    else -> applyFailure(outcome, if (replace) feed else requested, replace)
                }
            }
        }

        fun loadMore() {
            if (!feed.canLoadMore) return
            fetch(feed.page + 1, replace = false, showLoading = false)
        }

        fun delete(item: SearchResult) {
            if (deletingId != null) return
            deletingId = item.id
            scope.launch {
                val outcome = deps.authCall.call { deps.gateway.deleteWatchHistory(item.id) }
                deletingId = null
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        feed = feed.copy(items = feed.items.filterNot { it.id == item.id })
                        snackbarHostState.showSnackbar("已删除观看记录")
                    }

                    SessionOutcome.Expired -> {
                        snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                        onOpenLogin()
                    }

                    is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                        "删除失败：" + outcome.messageOr(CouiStrings.RetryLater),
                    )
                }
            }
        }

        LaunchedEffect(mode) { fetch(1, replace = true, showLoading = true) }
        // 翻页模式切换后当前页语义变了，回到第一页重新取，避免页脚停在旧页码
        PaginationModeReloadEffect(mode) { fetch(1, replace = true, showLoading = false) }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "观看记录",
                onBack = onBack,
            ) { innerPadding ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .consumeWindowInsets(innerPadding),
                    contentPadding = innerPadding,
                    horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                    verticalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                    overscrollEffect = rememberCouiOverscrollEffect(),
                ) {
                    when (val state = uiState) {
                        CouiWatchUiState.Loading -> item(
                            key = "coui-watch-loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                        }

                        is CouiWatchUiState.Failed -> item(
                            key = "coui-watch-failed",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiScreenFailureGroup(
                                message = state.message,
                                expired = state.sessionExpired,
                                onRetry = { fetch(1, replace = true, showLoading = true) },
                                onOpenLogin = onOpenLogin,
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                        }

                        CouiWatchUiState.Loaded -> {
                            if (feed.items.isEmpty()) {
                                item(key = "coui-watch-empty", span = { GridItemSpan(maxLineSpan) }) {
                                    CouiEmptyGroup(
                                        text = "暂无观看记录",
                                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    )
                                }
                            } else {
                                items(items = feed.items, key = { "coui-watch-" + it.id }) { item ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        CouiWorkGridCard(
                                            title = item.title,
                                            coverUrl = item.coverUrl,
                                            onClick = { onOpenAlbum(item.id) },
                                            albumId = item.id,
                                            coverContentDescription = item.title,
                                            subtitle = item.authors.authorNames(),
                                            category = item.category,
                                            badge = {
                                                CouiGridDeleteBadge(
                                                    contentDescription = "删除该观看记录",
                                                    onClick = { pendingDeleteItem = item },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(CouiWorkCardTokens.BadgeInset),
                                                )
                                            },
                                        )
                                    }
                                }
                                item(key = "coui-watch-paging", span = { GridItemSpan(maxLineSpan) }) {
                                    if (clickPaging) {
                                        CouiPagingGroup(
                                            page = feed.page,
                                            pageCount = feed.pageCount,
                                            hasMore = feed.hasMore,
                                            onJumpPage = { target ->
                                                val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
                                                if (resolved != feed.page) {
                                                    scope.launch {
                                                        gridState.scrollToItem(0)
                                                        fetch(resolved, replace = true, showLoading = true)
                                                    }
                                                }
                                            },
                                        )
                                    } else {
                                        CouiAutoLoadMoreEffect(feed = feed, onLoadMore = { loadMore() })
                                        CouiLoadMoreFooter(
                                            feed = feed,
                                            onRetry = { loadMore() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            val toDelete = pendingDeleteItem
            if (toDelete != null) {
                Dialog(
                    onDismissRequest = { pendingDeleteItem = null },
                    visible = true,
                    title = "删除观看记录",
                    message = "确定要删除《" + toDelete.title + "》的观看记录吗？",
                    placement = CouiDialogPlacement.Center,
                    confirmAction = couiDestructiveConfirmAction(
                        text = "删除",
                        onClick = {
                            delete(toDelete)
                            pendingDeleteItem = null
                        },
                    ),
                    dismissAction = CouiDialogAction(
                        text = "取消",
                        onClick = { pendingDeleteItem = null },
                    ),
                )
            }
        }
    }
}

/**
 * 栅格格卡右上角的删除角标：落在封面点击区里，自己消费点击，不触发整卡跳转。
 */
@Composable
private fun CouiGridDeleteBadge(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(28.dp)
            .clip(CircleShape)
            .background(scheme.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CouiIcons.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = scheme.onSurfaceVariant,
        )
    }
}

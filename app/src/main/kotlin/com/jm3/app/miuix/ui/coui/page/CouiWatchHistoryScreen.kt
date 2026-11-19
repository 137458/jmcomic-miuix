// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.watchhistory.WATCH_DELETE_CONFIRM
import com.jm3.app.miuix.ui.watchhistory.WATCH_DELETE_DISMISS
import com.jm3.app.miuix.ui.watchhistory.WATCH_DELETE_TITLE
import com.jm3.app.miuix.ui.watchhistory.watchDeleteMessage
import com.jm3.app.miuix.ui.watchhistory.rememberWatchHistoryScaffoldState
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 观看记录（COUI 栅格版）：形参与其他 COUI 页一致收成 [AppRoutes]，数据与分页语义由
 * WatchHistoryScaffoldState 统一，本页只负责栅格渲染与删除确认。
 *
 * 栅格触底不用预取组件（COUI 预取只支持 LazyListState），改为页脚项进入组合即取下一页；
 * 骨架的 loadMore 自带防抖（canLoadMore 里的 loadingMore 守卫），重复触发不会重发请求。
 */
@Composable
internal fun CouiWatchHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        val scope = rememberCoroutineScope()
        val mode = deps.paginationMode
        val clickPaging = mode == PaginationMode.CLICK
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val state = rememberWatchHistoryScaffoldState(
            deps = deps,
            snackbarHostState = snackbarHostState,
            onOpenLogin = nav::login,
        )
        val snapshot by state.snapshot.collectAsState()
        val feed = snapshot.feed

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
                    when (val load = snapshot.state) {
                        ListLoadState.Loading -> item(
                            key = "coui-watch-loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                        }

                        is ListLoadState.Failed -> item(
                            key = "coui-watch-failed",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiScreenFailureGroup(
                                message = load.message,
                                expired = load.sessionExpired,
                                onRetry = state::loadFirstPage,
                                onOpenLogin = nav::login,
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                        }

                        ListLoadState.Loaded -> {
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
                                            onClick = { nav.album(item.id) },
                                            albumId = item.id,
                                            coverContentDescription = item.title,
                                            subtitle = item.authors.authorNames(),
                                            category = item.category,
                                            badge = {
                                                CouiGridDeleteBadge(
                                                    contentDescription = "删除该观看记录",
                                                    onClick = { state.requestDelete(item) },
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
                                                val resolved = state.pageToLoad(target)
                                                if (resolved != null) {
                                                    scope.launch {
                                                        gridState.scrollToItem(0)
                                                        state.goToPage(resolved)
                                                    }
                                                }
                                            },
                                        )
                                    } else {
                                        CouiAutoLoadMoreEffect(feed = feed, onLoadMore = state::loadMore)
                                        CouiLoadMoreFooter(
                                            feed = feed,
                                            onRetry = state::loadMore,
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

            val toDelete = rememberRetainedTarget(state.pendingDeleteItem)
            if (toDelete != null) {
                Dialog(
                    onDismissRequest = state::dismissDelete,
                    visible = state.pendingDeleteItem != null,
                    material = couiDialogMaterial(),
                    title = WATCH_DELETE_TITLE,
                    message = watchDeleteMessage(toDelete),
                    placement = CouiDialogPlacement.Center,
                    confirmAction = couiDestructiveConfirmAction(
                        text = WATCH_DELETE_CONFIRM,
                        onClick = state::confirmDelete,
                    ),
                    dismissAction = CouiDialogAction(
                        text = WATCH_DELETE_DISMISS,
                        onClick = state::dismissDelete,
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
                backgroundColor = scheme.container,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(28.dp),
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

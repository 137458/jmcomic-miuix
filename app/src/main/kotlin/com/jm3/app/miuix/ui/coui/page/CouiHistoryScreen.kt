// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.coui.component.CouiAutoLoadMoreEffect
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.formatEpochMillis
import com.jm3.app.miuix.ui.history.HISTORY_DELETED_MESSAGE
import com.jm3.app.miuix.ui.history.HISTORY_UNDO_ACTION
import com.jm3.app.miuix.ui.history.HistoryAlbum
import com.jm3.app.miuix.ui.history.HistoryListState
import com.jm3.app.miuix.ui.history.HistoryResponseHandling
import com.jm3.app.miuix.ui.history.groupByAlbum
import com.jm3.app.miuix.ui.history.rememberHistoryScaffoldState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberDateFormatter
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.settings.PaginationMode
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/**
 * 阅读历史（COUI 栅格版）：参数与旧 Miuix 页一比一，聚合口径、分页与删除逻辑都与它共用
 * [rememberHistoryScaffoldState]，数据来自本机 HistoryRepository，不经过网关。
 *
 * 栅格触底不用预取组件（COUI 预取只支持 LazyListState），改为页脚项进入组合即取下一页。
 * 本皮肤没有请求令牌：骨架按 [HistoryResponseHandling.ApplyAsRequested] 保留它原有的迟到响应落点。
 */
@Composable
internal fun CouiHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    CouiTheme {
        val mode = deps.paginationMode
        val clickPaging = mode == PaginationMode.CLICK
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val snackbarHostState = remember { SnackbarHostState() }
        val history = rememberHistoryScaffoldState(
            repository = deps.history,
            responseHandling = HistoryResponseHandling.ApplyAsRequested,
            showMessage = { message -> snackbarHostState.showSnackbar(message) },
            showUndoPrompt = {
                snackbarHostState.showSnackbar(
                    message = HISTORY_DELETED_MESSAGE,
                    actionLabel = HISTORY_UNDO_ACTION,
                ) == SnackbarResult.ActionPerformed
            },
            scrollToTop = { gridState.scrollToItem(0) },
        )

        val albums = remember(history.feed.items) { history.feed.items.groupByAlbum() }
        val dateFormat = rememberDateFormatter()

        PaginationModeReloadEffect(mode) { history.loadFirstPage() }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "阅读历史",
                onBack = onBack,
                actions = {
                    if (albums.isNotEmpty()) {
                        CouiTopBarAction(
                            icon = CouiIcons.Close,
                            contentDescription = "清空阅读历史",
                            onClick = { history.requestClearConfirm() },
                        )
                    }
                },
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
                    when (val current = history.state) {
                        HistoryListState.Loading -> item(
                            key = "coui-history-loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                        }

                        is HistoryListState.Failed -> item(
                            key = "coui-history-failed",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiErrorGroup(
                                message = current.message,
                                onRetry = { history.loadFirstPage() },
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                        }

                        HistoryListState.Loaded -> {
                            if (albums.isEmpty()) {
                                item(key = "coui-history-empty", span = { GridItemSpan(maxLineSpan) }) {
                                    CouiEmptyGroup(
                                        text = "暂无阅读历史",
                                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    )
                                }
                            } else {
                                items(items = albums, key = { "coui-history-" + it.albumId }) { album ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        CouiHistoryCard(
                                            album = album,
                                            subtitle = couiHistorySubtitle(album, dateFormat),
                                            onResume = { nav.reader(album.latest.photoId) },
                                            onOpenDetail = { nav.album(album.albumId) },
                                            onDelete = { history.removeAlbum(album.albumId) },
                                        )
                                    }
                                }
                                item(key = "coui-history-paging", span = { GridItemSpan(maxLineSpan) }) {
                                    if (clickPaging) {
                                        CouiPagingGroup(
                                            page = history.feed.page,
                                            pageCount = history.feed.pageCount,
                                            hasMore = history.feed.hasMore,
                                            onJumpPage = { target -> history.jumpToPage(target) },
                                        )
                                    } else {
                                        CouiAutoLoadMoreEffect(feed = history.feed, onLoadMore = { history.loadMore() })
                                        CouiLoadMoreFooter(
                                            feed = history.feed,
                                            onRetry = { history.loadMore() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Dialog(
                onDismissRequest = { if (!history.clearing) history.dismissClearConfirm() },
                visible = history.showClearConfirm,
                material = couiDialogMaterial(),
                title = "清空阅读历史",
                message = "将删除本机全部阅读记录，已下载的章节不受影响。",
                placement = CouiDialogPlacement.Center,
                confirmAction = couiDestructiveConfirmAction(
                    text = if (history.clearing) "清空中…" else "清空",
                    enabled = !history.clearing,
                    onClick = { history.clearAll() },
                ),
                dismissAction = CouiDialogAction(
                    text = "取消",
                    style = CouiDialogActionStyle.Secondary,
                    onClick = { if (!history.clearing) history.dismissClearConfirm() },
                ),
            )

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 聚合卡：封面 + 最近章节 + 已读话数；整卡点击续读，卡内角标删除、footer 进详情。 */
@Composable
private fun CouiHistoryCard(
    album: HistoryAlbum,
    subtitle: String,
    onResume: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    CouiWorkGridCard(
        title = album.latest.title,
        coverUrl = album.latest.coverUrl,
        onClick = onResume,
        albumId = album.albumId,
        coverContentDescription = album.latest.title,
        subtitle = subtitle,
        badge = {
            CouiHistoryDeleteBadge(
                contentDescription = "删除该作品记录",
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(CouiWorkCardTokens.BadgeInset),
            )
        },
        footer = {
            CouiHistoryDetailLink(
                accent = scheme.accent,
                onClick = onOpenDetail,
            )
        },
    )
}

/** footer 里的详情入口：卡宽只有 112dp，用一行小字承接详情跳转。 */
@Composable
private fun ColumnScope.CouiHistoryDetailLink(
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val linkShape = CouiShape(radius = 8.dp, cornerWeight = 2f)
    Text(
        text = "详情",
        kind = CouiTextKind.GroupFooter,
        color = accent,
        maxLines = 1,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(linkShape)
            .couiPressable(shape = linkShape, minTouchTarget = CouiMinTouchTarget, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** 栅格格卡右上角的删除角标：落在封面点击区里，自己消费点击，不触发整卡续读。 */
@Composable
private fun CouiHistoryDeleteBadge(
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

/** 聚合行副标题：共读话数 + 最近阅读时间，与旧页文案一致。 */
private fun couiHistorySubtitle(
    album: HistoryAlbum,
    dateFormat: DateTimeFormatter,
): String = "共读 " + album.chapters + " 话 · " + formatEpochMillis(album.latest.updatedAt, dateFormat)

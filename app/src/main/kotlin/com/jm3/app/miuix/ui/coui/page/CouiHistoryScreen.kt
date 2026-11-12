// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.removeMatching
import com.jm3.app.miuix.ui.restoreAt
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
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberDateFormatter
import com.jm3.core.history.HistoryEntry
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pageLooksFull
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.runCatchingSuspending
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/** 阅读历史每页条数：与旧页保持一致，满页即代表还有下一页。 */
private const val HISTORY_PAGE_SIZE = 20

private sealed interface CouiHistoryListState {
    data object Loading : CouiHistoryListState

    data object Loaded : CouiHistoryListState

    data class Failed(val message: String) : CouiHistoryListState
}

/** 按作品聚合的阅读历史：最近一章用于一键续读，chapters 为该作品已读章节数。 */
internal data class CouiAlbumHistory(
    val albumId: String,
    val latest: HistoryEntry,
    val chapters: Int,
)

/** 保持最近阅读时间倒序，把同一作品的记录折叠成一条。 */
internal fun List<HistoryEntry>.couiGroupByAlbum(): List<CouiAlbumHistory> {
    val order = mutableListOf<String>()
    val latest = mutableMapOf<String, HistoryEntry>()
    val counts = mutableMapOf<String, Int>()
    for (entry in this) {
        if (entry.albumId !in latest) {
            latest[entry.albumId] = entry
            order += entry.albumId
        }
        counts[entry.albumId] = (counts[entry.albumId] ?: 0) + 1
    }
    return order.map { CouiAlbumHistory(it, latest.getValue(it), counts.getValue(it)) }
}

/**
 * 阅读历史（COUI 栅格版）：参数与旧 Miuix 页一比一，聚合口径、分页与删除逻辑沿用旧页，
 * 数据来自本机 HistoryRepository，不经过网关。
 *
 * 栅格触底不用预取组件（COUI 预取只支持 LazyListState），改为页脚项进入组合即取下一页。
 */
@Composable
fun CouiHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenReader: (String) -> Unit,
) {
    CouiTheme {
        val repository = deps.history
        val mode = deps.preferences.paginationMode
        val clickPaging = mode == PaginationMode.CLICK
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var feed by remember { mutableStateOf(PagedFeed<HistoryEntry>()) }
        var state by remember { mutableStateOf<CouiHistoryListState>(CouiHistoryListState.Loading) }
        var showClearConfirm by rememberSaveable { mutableStateOf(false) }
        var clearing by remember { mutableStateOf(false) }
        // 翻页在途守卫：仓库读取期间忽略重复翻页，避免连点重发请求
        var pageBusy by remember { mutableStateOf(false) }

        fun loadFirstPage() {
            state = CouiHistoryListState.Loading
            scope.launch {
                runCatchingSuspending { repository.page(1, HISTORY_PAGE_SIZE) }
                    .onSuccess { entries ->
                        feed = PagedFeed<HistoryEntry>().replace(
                            PageSlice(
                                items = entries,
                                hasMore = pageLooksFull(entries.size, HISTORY_PAGE_SIZE),
                                page = 1,
                            ),
                        ) { entry -> entry.photoId }
                        state = CouiHistoryListState.Loaded
                    }
                    .onFailure { state = CouiHistoryListState.Failed(failureCopy(it, "历史加载失败")) }
            }
        }

        fun jumpToPage(target: Int) {
            if (pageBusy) return
            val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
            if (resolved == feed.page) return
            pageBusy = true
            scope.launch {
                try {
                    runCatchingSuspending { repository.page(resolved, HISTORY_PAGE_SIZE) }
                        .onSuccess { entries ->
                            feed = PagedFeed<HistoryEntry>().replace(
                                PageSlice(
                                    items = entries,
                                    hasMore = pageLooksFull(entries.size, HISTORY_PAGE_SIZE),
                                    page = resolved,
                                ),
                            ) { entry -> entry.photoId }
                            state = CouiHistoryListState.Loaded
                            gridState.scrollToItem(0)
                        }
                        .onFailure { state = CouiHistoryListState.Failed(failureCopy(it, "历史加载失败")) }
                } finally {
                    pageBusy = false
                }
            }
        }

        fun loadMore() {
            if (state != CouiHistoryListState.Loaded) return
            val requested = feed.beginLoadMore()
            if (requested === feed) return
            val nextPage = requested.page + 1
            feed = requested
            scope.launch {
                runCatchingSuspending { repository.page(nextPage, HISTORY_PAGE_SIZE) }
                    .onSuccess { entries ->
                        feed = requested.append(
                            PageSlice(
                                items = entries,
                                hasMore = pageLooksFull(entries.size, HISTORY_PAGE_SIZE),
                                page = nextPage,
                            ),
                        ) { entry -> entry.photoId }
                    }
                    .onFailure { feed = requested.failLoadMore() }
            }
        }

        /** 删除整部作品的阅读记录：聚合行删除后按 albumId 从当前列表移除，并提供撤销入口。 */
        fun removeAlbum(albumId: String) {
            val slice = feed.items.removeMatching { it.albumId == albumId }
            if (slice.removed.isEmpty()) return
            scope.launch {
                val deleteOutcome = runCatchingSuspending { repository.deleteAlbum(albumId) }
                if (deleteOutcome.isFailure) {
                    snackbarHostState.showSnackbar("删除失败")
                    return@launch
                }
                feed = feed.copy(items = slice.remaining)
                val result = snackbarHostState.showSnackbar(
                    message = "已删除阅读记录",
                    actionLabel = "撤销",
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val restoreOutcome = runCatchingSuspending {
                        slice.removed.forEach { repository.upsert(it) }
                    }
                    if (restoreOutcome.isSuccess) {
                        feed = feed.copy(
                            items = feed.items.restoreAt(slice.removed, slice.insertIndex) { it.photoId },
                        )
                    } else {
                        snackbarHostState.showSnackbar("撤销失败")
                    }
                }
            }
        }

        fun clearAll() {
            if (clearing) return
            clearing = true
            scope.launch {
                runCatchingSuspending { repository.clear() }
                clearing = false
                showClearConfirm = false
                feed = PagedFeed<HistoryEntry>()
                state = CouiHistoryListState.Loaded
            }
        }

        val albums = remember(feed.items) { feed.items.couiGroupByAlbum() }
        val dateFormat = rememberDateFormatter()

        LaunchedEffect(Unit) { loadFirstPage() }
        PaginationModeReloadEffect(mode) { loadFirstPage() }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "阅读历史",
                onBack = onBack,
                actions = {
                    if (albums.isNotEmpty()) {
                        CouiTopBarAction(
                            icon = CouiIcons.Close,
                            contentDescription = "清空阅读历史",
                            onClick = { showClearConfirm = true },
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
                    when (val current = state) {
                        CouiHistoryListState.Loading -> item(
                            key = "coui-history-loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                        }

                        is CouiHistoryListState.Failed -> item(
                            key = "coui-history-failed",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            CouiErrorGroup(
                                message = current.message,
                                onRetry = { loadFirstPage() },
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                        }

                        CouiHistoryListState.Loaded -> {
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
                                            onResume = { onOpenReader(album.latest.photoId) },
                                            onOpenDetail = { onOpenAlbum(album.albumId) },
                                            onDelete = { removeAlbum(album.albumId) },
                                        )
                                    }
                                }
                                item(key = "coui-history-paging", span = { GridItemSpan(maxLineSpan) }) {
                                    if (clickPaging) {
                                        CouiPagingGroup(
                                            page = feed.page,
                                            pageCount = feed.pageCount,
                                            hasMore = feed.hasMore,
                                            onJumpPage = { target -> jumpToPage(target) },
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

            Dialog(
                onDismissRequest = { if (!clearing) showClearConfirm = false },
                visible = showClearConfirm,
                material = couiDialogMaterial(),
                title = "清空阅读历史",
                message = "将删除本机全部阅读记录，已下载的章节不受影响。",
                placement = CouiDialogPlacement.Center,
                confirmAction = couiDestructiveConfirmAction(
                    text = if (clearing) "清空中…" else "清空",
                    enabled = !clearing,
                    onClick = { clearAll() },
                ),
                dismissAction = CouiDialogAction(
                    text = "取消",
                    style = CouiDialogActionStyle.Secondary,
                    onClick = { if (!clearing) showClearConfirm = false },
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
    album: CouiAlbumHistory,
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
    album: CouiAlbumHistory,
    dateFormat: java.time.format.DateTimeFormatter,
): String = "共读 " + album.chapters + " 话 · " + formatEpochMillis(album.latest.updatedAt, dateFormat)

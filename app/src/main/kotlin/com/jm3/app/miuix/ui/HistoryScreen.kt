package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.CenteredEmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadMoreFooter
import com.jm3.app.miuix.ui.components.LoadMoreOnScroll
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.MinTouchTarget
import com.jm3.app.miuix.ui.components.PagerBar
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.history.HISTORY_DELETED_MESSAGE
import com.jm3.app.miuix.ui.history.HISTORY_UNDO_ACTION
import com.jm3.app.miuix.ui.history.HistoryAlbum
import com.jm3.app.miuix.ui.history.HistoryListState
import com.jm3.app.miuix.ui.history.HistoryResponseHandling
import com.jm3.app.miuix.ui.history.groupByAlbum
import com.jm3.app.miuix.ui.history.rememberHistoryScaffoldState
import com.jm3.core.settings.PaginationMode
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    val mode = deps.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // 状态编排（装载、续页、跳页、删除撤销、清空）在共享骨架里，这里只接线与铺视觉
    val history = rememberHistoryScaffoldState(
        repository = deps.history,
        responseHandling = HistoryResponseHandling.DropLateResponses,
        showMessage = { message -> snackbarHostState.showSnackbar(message) },
        showUndoPrompt = {
            snackbarHostState.showSnackbar(
                message = HISTORY_DELETED_MESSAGE,
                actionLabel = HISTORY_UNDO_ACTION,
            ) == SnackbarResult.ActionPerformed
        },
        scrollToTop = { listState.scrollToItem(0) },
    )

    PaginationModeReloadEffect(mode) { history.loadFirstPage() }

    val albums = remember(history.feed.items) { history.feed.items.groupByAlbum() }

    // 触底加载只在无限滚动模式生效；点击翻页由列表底部翻页条驱动。
    if (!clickPaging) {
        LoadMoreOnScroll(listState = listState, feed = history.feed, onLoadMore = history::loadMore)
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "阅读历史",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    if (albums.isNotEmpty()) {
                        MinTouchTarget(onClick = { history.requestClearConfirm() }) {
                            TextButton(
                                text = "清空",
                                onClick = { history.requestClearConfirm() },
                                minWidth = 0.dp,
                                minHeight = 36.dp,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val current = history.state) {
            HistoryListState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is HistoryListState.Failed -> ErrorScreen(
                message = current.message,
                onRetry = history::loadFirstPage,
                modifier = Modifier.padding(innerPadding),
            )
            HistoryListState.Loaded -> {
                if (albums.isEmpty()) {
                    CenteredEmptyState(
                        title = "暂无阅读历史",
                        hint = "读过的章节会出现在这里",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    // 格式化器整页一份：放进行项内会让滚动进出反复构造 DateTimeFormatter
                    val dateFormat = rememberDateFormatter()
                    TrackedLazyColumn(
                        state = listState,
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        items(albums, key = { it.albumId }) { album ->
                            AlbumHistoryCard(
                                album = album,
                                dateFormat = dateFormat,
                                onResume = { nav.reader(album.latest.photoId) },
                                onOpenDetail = { nav.album(album.albumId) },
                                onDelete = { history.removeAlbum(album.albumId) },
                            )
                        }
                        if (albums.isNotEmpty()) {
                            item(key = "pagination-footer") {
                                if (clickPaging) {
                                    PagerBar(
                                        page = history.feed.page,
                                        hasMore = history.feed.hasMore,
                                        enabled = !history.pageBusy,
                                        onPageChange = history::jumpToPage,
                                    )
                                } else {
                                    LoadMoreFooter(feed = history.feed, onLoadMore = history::loadMore)
                                }
                            }
                        }
                    }
                }
            }
        }

        OverlayDialog(
            show = history.showClearConfirm,
            maxWidth = overlayDialogMaxWidth(),
            title = "清空阅读历史",
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = { history.dismissClearConfirm() },
        ) {
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "将删除本机全部阅读记录，已下载的章节不受影响。",
                    style = MiuixTheme.textStyles.body2,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // HyperOS 弹窗惯例：按钮纵向堆叠、各占满宽，避免横排等分与系统弹窗观感不一致
                TextButton(
                    text = "取消",
                    onClick = { history.dismissClearConfirm() },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = if (history.clearing) "清空中…" else "清空",
                    onClick = history::clearAll,
                    enabled = !history.clearing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 聚合行：封面 + 最近章节 + 已读话数，整行点击续读；尾部删除整部记录。 */
@Composable
private fun AlbumHistoryCard(
    album: HistoryAlbum,
    dateFormat: DateTimeFormatter,
    onResume: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    val heroHost = LocalAlbumHeroHost.current
    val cardCorner = LocalSkin.current.shapes.cardCorner
    WorkRowCard(
        coverUrl = album.latest.coverUrl,
        title = album.latest.title,
        onClick = onResume,
        albumId = album.albumId,
        subtitle = "共读 ${album.chapters} 话 · ${formatEpochMillis(album.latest.updatedAt, dateFormat)}",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        textEndPadding = 4.dp,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除该作品记录")
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MinTouchTarget(onClick = onResume) {
                TextButton(
                    text = "继续阅读",
                    onClick = onResume,
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                )
            }
            MinTouchTarget(
                onClick = {
                    heroHost?.startForward(album.albumId, album.latest.coverUrl, cardCorner.value)
                    onOpenDetail()
                },
            ) {
                TextButton(
                    text = "详情",
                    onClick = {
                        heroHost?.startForward(album.albumId, album.latest.coverUrl, cardCorner.value)
                        onOpenDetail()
                    },
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                )
            }
        }
    }
}

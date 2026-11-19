package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.EmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadMoreFooter
import com.jm3.app.miuix.ui.components.LoadMoreOnScroll
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.PagerBar
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.ui.watchhistory.WATCH_DELETE_CONFIRM
import com.jm3.app.miuix.ui.watchhistory.WATCH_DELETE_DISMISS
import com.jm3.app.miuix.ui.watchhistory.WATCH_DELETE_TITLE
import com.jm3.app.miuix.ui.watchhistory.watchDeleteMessage
import com.jm3.app.miuix.ui.watchhistory.rememberWatchHistoryScaffoldState
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.settings.PaginationMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 服务端观看记录：与本地历史不同，这里记录的是账号云端足迹，支持逐条删除。
 *
 * 加载编排、删除与二次确认都在 WatchHistoryScaffoldState；本屏只留 HyperOS 的视觉（玻璃脚手架、
 * 行卡、Overlay 确认弹窗），文案常量与确认正文也来自骨架，两皮肤不会各写一份。
 */
@Composable
internal fun WatchHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val mode = deps.paginationMode
    val skin = LocalSkin.current
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val listState = rememberLazyListState()
    val state = rememberWatchHistoryScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        onOpenLogin = nav::login,
    )
    val snapshot by state.snapshot.collectAsState()
    val feed = snapshot.feed

    if (mode != PaginationMode.CLICK && snapshot.state == ListLoadState.Loaded) {
        LoadMoreOnScroll(
            listState = listState,
            feed = feed,
            onLoadMore = state::loadMore,
        )
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "云端观看记录",
                subtitle = feed.items.takeIf { it.isNotEmpty() }?.let { "本页 ${it.size} 部" }.orEmpty(),
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = snapshot.state) {
            ListLoadState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ListLoadState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = nav::login,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = state::loadFirstPage,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            ListLoadState.Loaded -> GlassPullToRefresh(
                isRefreshing = snapshot.refreshing,
                onRefresh = state::refresh,
                pullToRefreshState = refreshState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
                val feedColumns = LocalWideLayout.current.feedColumns
                TrackedLazyColumn(
                    state = listState,
                    // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize().readableWidth(),
                ) {
                    if (feed.items.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                title = "暂无观看记录",
                                hint = "阅读作品后会同步到账号云端",
                            )
                        }
                    } else {
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
                                title = item.title,
                                onClick = { nav.album(item.id) },
                                albumId = item.id,
                                subtitle = item.authors.authorNames(),
                                category = item.category,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                textEndPadding = 4.dp,
                                trailing = {
                                    IconButton(
                                        onClick = { state.requestDelete(item) },
                                        enabled = state.deletingId != item.id,
                                    ) {
                                        Icon(MiuixIcons.Delete, contentDescription = "删除记录")
                                    }
                                },
                            ) {
                                WorkCardMetaRow(tags = item.tags, albumCode = item.id)
                            }
                        }
                        item(key = "footer") {
                            if (mode == PaginationMode.CLICK) {
                                // 接口不返回总数，交给通用翻页条按 feed.hasMore 判下一页
                                PagerBar(
                                    page = feed.page,
                                    hasMore = feed.hasMore,
                                    onPageChange = { target ->
                                        state.goToPage(pagerTargetPage(feed.page, target, feed.pageCount))
                                    },
                                )
                            } else {
                                // 三态页脚与其他列表保持一致：加载中 / 失败重试 / 没有更多
                                LoadMoreFooter(
                                    feed = feed,
                                    onLoadMore = state::loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }

        val toDelete = state.pendingDeleteItem
        if (toDelete != null) {
            OverlayDialog(
                show = true,
                maxWidth = overlayDialogMaxWidth(),
                title = WATCH_DELETE_TITLE,
                cornerRadius = skin.shapes.dialogCorner,
                onDismissRequest = state::dismissDelete,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = watchDeleteMessage(toDelete),
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        text = WATCH_DELETE_DISMISS,
                        onClick = state::dismissDelete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = WATCH_DELETE_CONFIRM,
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                        onClick = state::confirmDelete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

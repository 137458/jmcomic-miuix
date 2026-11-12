package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.pageLooksFull
import com.jm3.core.paging.pagerNextPage
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
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

/** 服务端观看记录单页条数；接口不返回总数，用返回条数推断是否还有下一页。 */
private const val WATCH_PAGE_SIZE = 20

/** 服务端观看记录：与本地历史不同，这里记录的是账号云端足迹，支持逐条删除。 */
@Composable
fun WatchHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val mode = deps.paging.paginationMode
    val user by deps.account.user.collectAsState()
    val skin = LocalSkin.current
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var deletingId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<SearchResult?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 代次守卫、在途标记、翻页游标与失败落点都在 loader 里；这里只给「取哪一页」与「怎么提示」
    val loader = remember(mode, user?.uid, snackbarHostState) {
        SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = "观看记录加载失败",
            fetchPage = { page ->
                deps.authCall.call { deps.gateway.watchHistory(page) }.toWatchSlice(page)
            },
            notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    val feed = snapshot.feed

    fun delete(item: SearchResult) {
        if (deletingId != null) return
        deletingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.deleteWatchHistory(item.id) }
            deletingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    loader.removeWhere { it.id == item.id }
                    snackbarHostState.showSnackbar("已删除观看记录")
                }

                // 删除是账号态操作：失效时提示一次并直接给登录入口，不让用户对着按钮重试
                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> {
                    snackbarHostState.showSnackbar("删除失败：" + failureCopy(outcome, "请稍后重试"))
                }
            }
        }
    }

    // 分页模式或登录账号切换后重新加载第一页，避免残留旧账号或旧分页语义的列表
    LaunchedEffect(loader) {
        loader.loadFirstPage()
    }

    if (mode != PaginationMode.CLICK && snapshot.state == ListLoadState.Loaded) {
        LoadMoreOnScroll(
            listState = listState,
            feed = feed,
            onLoadMore = loader::loadMore,
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
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = loader::loadFirstPage,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            ListLoadState.Loaded -> GlassPullToRefresh(
                isRefreshing = snapshot.refreshing,
                onRefresh = loader::refresh,
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
                                onClick = { onOpenAlbum(item.id) },
                                albumId = item.id,
                                subtitle = item.authors.authorNames(),
                                category = item.category,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                textEndPadding = 4.dp,
                                trailing = {
                                    IconButton(
                                        onClick = { pendingDeleteItem = item },
                                        enabled = deletingId != item.id,
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
                                        loader.goToPage(pagerTargetPage(feed.page, target, feed.pageCount))
                                    },
                                )
                            } else {
                                // 三态页脚与其他列表保持一致：加载中 / 失败重试 / 没有更多
                                LoadMoreFooter(
                                    feed = feed,
                                    onLoadMore = loader::loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }

        val toDelete = pendingDeleteItem
        if (toDelete != null) {
            OverlayDialog(
                show = true,
                maxWidth = overlayDialogMaxWidth(),
                title = "删除观看记录",
                cornerRadius = skin.shapes.dialogCorner,
                onDismissRequest = { pendingDeleteItem = null },
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "确定要删除《" + toDelete.title + "》的观看记录吗？",
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        text = "取消",
                        onClick = { pendingDeleteItem = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "删除",
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                        onClick = {
                            delete(toDelete)
                            pendingDeleteItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 接口不返回总数，用本页条数推断是否还有下一页。 */
private fun SessionOutcome<List<SearchResult>>.toWatchSlice(page: Int): SessionOutcome<PageSlice<SearchResult>> =
    when (this) {
        is SessionOutcome.Ok -> SessionOutcome.Ok(
            PageSlice(
                items = value,
                hasMore = pageLooksFull(value.size, WATCH_PAGE_SIZE),
                page = page,
            ),
        )

        SessionOutcome.Expired -> SessionOutcome.Expired

        is SessionOutcome.Failed -> this
    }

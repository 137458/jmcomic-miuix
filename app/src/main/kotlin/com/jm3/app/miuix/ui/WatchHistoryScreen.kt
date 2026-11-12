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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.paging.pagerNextPage
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

private sealed interface WatchUiState {
    data object Loading : WatchUiState
    data object Loaded : WatchUiState
    // sessionExpired 区分「重试可能好」与「会话没了」：后者只能去登录，给重试按钮是骗人的
    data class Failed(val message: String, val sessionExpired: Boolean = false) : WatchUiState
}

/** 服务端观看记录：与本地历史不同，这里记录的是账号云端足迹，支持逐条删除。 */
@Composable
fun WatchHistoryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val mode = deps.preferences.paginationMode
    val skin = LocalSkin.current
    // 当前页码、是否还有下一页、追加在途与追加失败都由 PagedFeed 记；
    // 追加失败时 hasMore 保持原值，所以页脚一定留着重试入口，不会被误画成「已经到底」
    var feed by remember { mutableStateOf(PagedFeed<SearchResult>()) }
    var state by remember { mutableStateOf<WatchUiState>(WatchUiState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var deletingId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<SearchResult?>(null) }
    val listState = rememberLazyListState()
    var requestToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    /** 失效与失败共用一个落点：有内容就保留列表只提示，没内容才换整屏（失效换成去登录）。 */
    fun applyFailure(outcome: SessionOutcome<*>, inFlight: PagedFeed<SearchResult>, replace: Boolean) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = !replace && inFlight.items.isNotEmpty(),
            failureMessage = "观看记录加载失败",
            stalePrefix = "加载失败：",
        )
        // 保留内容时把页脚标成可重试；整屏失败要撤掉在途标记，否则页脚永远转圈
        feed = if (plan.keepContent) inFlight.failLoadMore() else inFlight.copy(loadingMore = false)
        if (plan.keepContent) {
            scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
        } else {
            state = WatchUiState.Failed(
                message = plan.message.orEmpty(),
                sessionExpired = plan.phase == ScreenLoadPhase.Expired,
            )
        }
    }

    fun fetch(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        // 翻页请求在途时不再发起新的翻页，避免连点并发打同一页导致列表重复
        if (!replace && !feed.canLoadMore) return
        if (showLoading) state = WatchUiState.Loading
        val requested = if (replace) feed else feed.beginLoadMore()
        if (!replace) feed = requested
        val token = if (replace) ++requestToken else requestToken
        scope.launch {
            // 启动闸门、IO 切线程与失效通知都在 AuthCall 里，这里只关心三种结局
            val outcome = deps.authCall.call { deps.gateway.watchHistory(targetPage) }
            if (token != requestToken) return@launch
            refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val slice = PageSlice(
                        items = outcome.value,
                        // 接口不返回总数，用本页条数推断还有没有下一页
                        hasMore = outcome.value.size >= WATCH_PAGE_SIZE,
                        page = targetPage,
                    )
                    feed = if (replace) {
                        PagedFeed<SearchResult>().replace(slice) { it.id }
                    } else {
                        requested.append(slice) { it.id }
                    }
                    state = WatchUiState.Loaded
                }

                // 失效不是「加载失败」：没有列表可留时换成「去登录」，别让用户反复点重试
                SessionOutcome.Expired -> applyFailure(outcome, requested, replace)

                is SessionOutcome.Failed -> applyFailure(outcome, requested, replace)
            }
        }
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

                // 删除是账号态操作：失效时提示一次并直接给登录入口，不让用户对着按钮重试
                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> {
                    snackbarHostState.showSnackbar("删除失败：" + outcome.messageOr("请稍后重试"))
                }
            }
        }
    }

    // 分页模式切换后重新按新形态加载第一页，避免同一份状态混用两种翻页语义
    LaunchedEffect(mode) { fetch(targetPage = 1, replace = true, showLoading = true) }

    if (mode != PaginationMode.CLICK && state == WatchUiState.Loaded) {
        LoadMoreOnScroll(
            listState = listState,
            feed = feed,
            onLoadMore = {
                fetch(
                    pagerNextPage(feed.page, feed.pageCount ?: Int.MAX_VALUE),
                    replace = false,
                    showLoading = false,
                )
            },
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
        when (val s = state) {
            WatchUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is WatchUiState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = { fetch(targetPage = 1, replace = true, showLoading = true) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            WatchUiState.Loaded -> if (feed.items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无观看记录",
                    hint = "阅读作品后会同步到账号云端",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                GlassPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        fetch(targetPage = 1, replace = true, showLoading = false)
                    },
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
                                        fetch(
                                            pagerTargetPage(feed.page, target, feed.pageCount),
                                            replace = true,
                                            showLoading = false,
                                        )
                                    },
                                )
                            } else {
                                // 三态页脚与其他列表保持一致：加载中 / 失败重试 / 没有更多
                                LoadMoreFooter(
                                    feed = feed,
                                    onLoadMore = { fetch(pagerNextPage(feed.page, feed.pageCount ?: Int.MAX_VALUE), replace = false, showLoading = false) },
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

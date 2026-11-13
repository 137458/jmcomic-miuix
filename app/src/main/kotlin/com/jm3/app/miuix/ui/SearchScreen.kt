package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toSlice
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

internal sealed interface SearchUiState {
    data object Loading : SearchUiState
    data class Loaded(val feed: PagedFeed<SearchResult>) : SearchUiState
    data class Failed(
        val message: String,
        val sessionExpired: Boolean = false,
    ) : SearchUiState
}

/** 空列表起手：第一页由 [replacedBy] 落进来。 */
private fun emptyFeed() = PagedFeed<SearchResult>()

/** 结局 → 页面状态：成功整页替换，失败与会话失效都落错误文案（文案统一由 [planLoad] 给出）。 */
internal fun searchStateOf(outcome: SessionOutcome<SearchPage>, failureMessage: String): SearchUiState =
    when (outcome) {
        is SessionOutcome.Ok -> SearchUiState.Loaded(emptyFeed().replacedBy(outcome.value))
        else -> SearchUiState.Failed(
            message = planLoad(outcome, hasContent = false, failureMessage = failureMessage).message.orEmpty(),
            sessionExpired = outcome is SessionOutcome.Expired,
        )
    }

/** loader 快照 → 本屏渲染状态：三档一一对应，失败文案与失效标记原样搬过来。 */
private fun SessionListSnapshot<SearchResult>.toUiState(): SearchUiState = when (val s = state) {
    ListLoadState.Loading -> SearchUiState.Loading
    ListLoadState.Loaded -> SearchUiState.Loaded(feed)
    is ListLoadState.Failed -> SearchUiState.Failed(message = s.message, sessionExpired = s.sessionExpired)
}

/** 独立搜索结果页：标签/作者/作品/人物点击与外部入口共用，支持排序与分页。 */
@Composable
internal fun SearchScreen(
    deps: ScreenDeps,
    query: String,
    scope: SearchScope,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var timeValue by rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val selectionHaptic = rememberSelectionHaptic()
    val snackbarHostState = remember { SnackbarHostState() }

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL
    // 分页模式是全局可观察状态，设置页切换后本页立即变更控件形态。
    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK

    // 关键词/范围/排序/时间任一变化就是另一份结果：loader 跟着重建，在途请求与页游标从零起算。
    val loader = remember(query, scope, order, time) {
        SessionListLoader<SearchResult>(
            scope = coroutineScope,
            idOf = { it.id },
            failureMessage = "搜索失败",
            fetchPage = { page ->
                val outcome = deps.authCall.call { deps.gateway.search(query, page, order, scope, time) }
                when (outcome) {
                    is SessionOutcome.Ok -> SessionOutcome.Ok(outcome.value.toSlice())
                    SessionOutcome.Expired -> SessionOutcome.Expired
                    is SessionOutcome.Failed -> outcome
                }
            },
            notice = { message -> coroutineScope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    val state = snapshot.toUiState()
    // 下拉指示器状态由 loader 给：刷新只保留内容，不再由本屏另置一份
    val refreshing = snapshot.refreshing
    // 跳页成功后回顶：loader 只认页号，回顶时点由本屏登记
    var pendingScrollPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(loader) { loader.loadFirstPage() }
    LaunchedEffect(snapshot.feed.page, snapshot.state) {
        if (pendingScrollPage != 0 && snapshot.state is ListLoadState.Loaded && pendingScrollPage == snapshot.feed.page) {
            pendingScrollPage = 0
            listState.scrollToItem(0)
        }
    }

    PaginationModeReloadEffect(mode) { loader.loadFirstPage() }

    fun loadMore() {
        loader.loadMore()
    }

    /** 点击分页：按目标页整页替换并回到列表顶部。 */
    fun jumpToPage(target: Int) {
        val current = snapshot.feed
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        if (current.pageCount == null && resolved > current.page && !current.hasMore) return
        pendingScrollPage = resolved
        loader.goToPage(resolved)
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = if (scope == SearchScope.SITE) "搜索：$query" else "${scope.label}：$query",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 排序/时间筛选固定于列表之外：与顶栏同处玻璃层
                GlassChipRow(
                    tabs = BrowseOrder.entries.map { it.label },
                    selectedTabIndex = BrowseOrder.entries.indexOf(order),
                    onTabSelected = {
                        BrowseOrder.entries.getOrNull(it)?.let { selected ->
                            selectionHaptic()
                            orderValue = selected.apiValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                // 时间范围由服务端过滤（原版年/月筛选的等价能力，库只提供 今日/本周/本月/全部）
                GlassChipRow(
                    tabs = BrowseTime.entries.map { it.label },
                    selectedTabIndex = BrowseTime.entries.indexOf(time),
                    onTabSelected = {
                        BrowseTime.entries.getOrNull(it)?.let { selected ->
                            selectionHaptic()
                            timeValue = selected.apiValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        // 列表内容穿到玻璃顶栏（顶栏 + 筛选条）下方，顶栏才能采样到真实背景
        GlassPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { loader.refresh() },
            pullToRefreshState = refreshState,
            modifier = Modifier.fillMaxSize(),
            // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
            contentPadding = innerPadding,
        ) {
            // 触底判定全在 PagedFeed.shouldLoadMore 里；点击翻页模式不订阅滚动
            if (!clickPaging) {
                val paging = state as? SearchUiState.Loaded
                if (paging != null) {
                    LoadMoreOnScroll(
                        listState = listState,
                        feed = paging.feed,
                        onLoadMore = ::loadMore,
                    )
                }
            }
            // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
            val feedColumns = LocalWideLayout.current.feedColumns
            TrackedLazyColumn(
                state = listState,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize().readableWidth(),
            ) {
                when (val s = state) {
                    is SearchUiState.Loading -> if (!refreshing) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                InfiniteProgressIndicator()
                            }
                        }
                    }
                    is SearchUiState.Failed -> item(key = "error") {
                        ScreenFailureOrLogin(
                            message = s.message,
                            expired = s.sessionExpired,
                            onRetry = { loader.loadFirstPage() },
                            onOpenLogin = nav::login,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    is SearchUiState.Loaded -> if (clickPaging) {
                        pagedSearchResultItems(
                            feed = s.feed,
                            onPageChange = ::jumpToPage,
                            onOpenAlbum = nav::album,
                            columns = feedColumns,
                        )
                    } else {
                        searchResultItems(
                            feed = s.feed,
                            onLoadMore = ::loadMore,
                            onOpenAlbum = nav::album,
                            columns = feedColumns,
                        )
                    }
                }
            }
        }
    }
}

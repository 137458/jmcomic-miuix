package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.GlassTabRow
import com.jm3.core.mergedWith
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchScope
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

private sealed interface SearchUiState {
    data object Loading : SearchUiState
    data class Loaded(val page: SearchPage) : SearchUiState
    data class Failed(val message: String) : SearchUiState
}

/** 独立搜索结果页：标签/作者/作品/人物点击与外部入口共用，支持排序与分页。 */
@Composable
fun SearchScreen(
    query: String,
    scope: SearchScope,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    var state by remember(query, scope) { mutableStateOf<SearchUiState>(SearchUiState.Loading) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var timeValue by rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }
    var loadingMore by remember { mutableStateOf(false) }
    // 加载更多失败单独置位，供列表底部给出重试入口
    var loadMoreFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 排序/下拉刷新会替换第一页，旧的分页响应只在代次未变时落地
    var requestToken by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val selectionHaptic = rememberSelectionHaptic()

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL
    // 分页模式是全局可观察状态，设置页切换后本页立即变更控件形态。
    val clickPaging = Jm3Graph.paginationMode == PaginationMode.CLICK

    fun fetch(page: Int, sort: BrowseOrder): SearchPage =
        Jm3Graph.gateway.search(query, page, sort, scope, time)

    fun loadFirstPage(byPull: Boolean = false) {
        if (byPull) refreshing = true
        requestToken++
        val token = requestToken
        // 下拉刷新时保留当前结果，避免列表在刷新指示器下方整屏消失
        if (!byPull) state = SearchUiState.Loading
        coroutineScope.launch {
            val next = withContext(Dispatchers.IO) { runCatching { fetch(1, order) } }
            if (token != requestToken) return@launch
            state = next.fold(
                onSuccess = { SearchUiState.Loaded(it) },
                onFailure = { SearchUiState.Failed(it.message ?: "搜索失败") },
            )
            refreshing = false
        }
    }

    fun loadMore() {
        val loaded = state as? SearchUiState.Loaded ?: return
        if (loadingMore || loaded.page.currentPage >= loaded.page.totalPages) return
        val token = requestToken
        loadingMore = true
        coroutineScope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { fetch(loaded.page.currentPage + 1, order) }.getOrNull()
            }
            loadingMore = false
            if (next != null && token == requestToken) {
                loadMoreFailed = false
                state = SearchUiState.Loaded(loaded.page.mergedWith(next))
            } else if (token == requestToken) {
                loadMoreFailed = true
            }
        }
    }

    /** 点击分页：整页替换并回到列表顶部。 */
    fun jumpToPage(target: Int) {
        val loaded = state as? SearchUiState.Loaded ?: return
        if (target < 1 || target > loaded.page.totalPages) return
        requestToken++
        val token = requestToken
        state = SearchUiState.Loading
        coroutineScope.launch {
            val next = withContext(Dispatchers.IO) { runCatching { fetch(target, order) } }
            if (token != requestToken) return@launch
            next.fold(
                onSuccess = {
                    state = SearchUiState.Loaded(it)
                    listState.scrollToItem(0)
                },
                onFailure = { state = SearchUiState.Failed(it.message ?: "搜索失败") },
            )
        }
    }

    LaunchedEffect(query, scope, orderValue, timeValue) {
        loadFirstPage()
    }

    LaunchedEffect((state as? SearchUiState.Loaded)?.page, clickPaging) {
        if (clickPaging || state !is SearchUiState.Loaded) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (last >= 0 && total > 0 && last >= total - 3) loadMore()
        }
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
                GlassTabRow(
                    tabs = BrowseOrder.entries.map { it.label },
                    selectedTabIndex = BrowseOrder.entries.indexOf(order),
                    onTabSelected = {
                        selectionHaptic()
                        orderValue = BrowseOrder.entries[it].apiValue
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // 时间范围由服务端过滤（原版年/月筛选的等价能力，库只提供 今日/本周/本月/全部）
                GlassTabRow(
                    tabs = BrowseTime.entries.map { it.label },
                    selectedTabIndex = BrowseTime.entries.indexOf(time),
                    onTabSelected = {
                        selectionHaptic()
                        timeValue = BrowseTime.entries[it].apiValue
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        },
    ) { innerPadding ->
        // 列表内容穿到玻璃顶栏（顶栏 + 筛选条）下方，顶栏才能采样到真实背景
        GlassPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { loadFirstPage(byPull = true) },
            pullToRefreshState = refreshState,
            modifier = Modifier.fillMaxSize(),
            // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
            contentPadding = innerPadding,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
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
                        ErrorScreen(
                            message = "搜索失败：${s.message}",
                            onRetry = { loadFirstPage() },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    is SearchUiState.Loaded -> if (clickPaging) {
                        pagedSearchResultItems(
                            page = s.page,
                            onPageChange = ::jumpToPage,
                            onOpenAlbum = onOpenAlbum,
                        )
                    } else {
                        searchResultItems(
                            page = s.page,
                            loadingMore = loadingMore,
                            loadMoreFailed = loadMoreFailed,
                            onLoadMore = ::loadMore,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }
        }
    }
}

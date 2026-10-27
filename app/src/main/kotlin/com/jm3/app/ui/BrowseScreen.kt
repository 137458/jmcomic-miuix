package com.jm3.app.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jm3.core.Jm3Gateway
import com.jm3.core.mergedWith
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.LabeledOption
import com.jm3.core.model.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar

/** 浏览模式：分类走 categories/filter，榜单为排序+时间参数近似（同一接口）。 */
enum class BrowseMode(val title: String) {
    CATEGORY("分类"),
    RANK("榜单"),
}

private sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Loaded(val page: SearchPage) : BrowseUiState
    data class Failed(val message: String) : BrowseUiState
}

@Composable
fun BrowseScreen(mode: BrowseMode, gateway: Jm3Gateway, onOpenAlbum: (String) -> Unit) {
    var categoryValue by rememberSaveable { mutableStateOf(BrowseCategory.ALL.apiValue) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var timeValue by rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }
    var state by remember { mutableStateOf<BrowseUiState>(BrowseUiState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    // 仅下拉手势触发的刷新使用顶部指示器；切换筛选等程序化加载走列表内进度条。
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val category = BrowseCategory.entries.firstOrNull { it.apiValue == categoryValue } ?: BrowseCategory.ALL
    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL

    fun filter(): BrowseFilter = when (mode) {
        BrowseMode.CATEGORY -> BrowseFilter(category = category, order = order, time = BrowseTime.ALL)
        BrowseMode.RANK -> BrowseFilter(category = BrowseCategory.ALL, order = order, time = time)
    }

    fun refresh(byPull: Boolean = false) {
        if (byPull) refreshing = true
        scope.launch {
            state = BrowseUiState.Loading
            state = withContext(Dispatchers.IO) {
                runCatching { BrowseUiState.Loaded(gateway.browse(filter(), 1)) }
                    .getOrElse { BrowseUiState.Failed(it.message ?: "加载失败") }
            }
            refreshing = false
        }
    }

    fun loadMore() {
        val loaded = state as? BrowseUiState.Loaded ?: return
        if (loadingMore || loaded.page.currentPage >= loaded.page.totalPages) return
        loadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { gateway.browse(filter(), loaded.page.currentPage + 1) }.getOrNull()
            }
            loadingMore = false
            if (next != null) {
                state = loaded.copy(page = loaded.page.mergedWith(next))
            }
        }
    }

    LaunchedEffect(mode, categoryValue, orderValue, timeValue) {
        refresh()
    }

    val loadedNow = state as? BrowseUiState.Loaded
    LaunchedEffect(loadedNow?.page) {
        if (loadedNow == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val page = (state as? BrowseUiState.Loaded)?.page ?: return@collect
                if (last != null && last >= page.items.size - 2) loadMore()
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = mode.title) },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { refresh(byPull = true) },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = innerPadding,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "filters") {
                    // 分组筛选统一用 Miuix TabRow（分段控件语义），替代手写 Chip 行。
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        if (mode == BrowseMode.CATEGORY) {
                            TabRow(
                                tabs = BrowseCategory.entries.map { it.label },
                                selectedTabIndex = BrowseCategory.entries.indexOf(category),
                                onTabSelected = { categoryValue = BrowseCategory.entries[it].apiValue },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        TabRow(
                            tabs = BrowseOrder.entries.map { it.label },
                            selectedTabIndex = BrowseOrder.entries.indexOf(order),
                            onTabSelected = { orderValue = BrowseOrder.entries[it].apiValue },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                        if (mode == BrowseMode.RANK) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TabRow(
                                tabs = BrowseTime.entries.map { it.label },
                                selectedTabIndex = BrowseTime.entries.indexOf(time),
                                onTabSelected = { timeValue = BrowseTime.entries[it].apiValue },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                when (val s = state) {
                    is BrowseUiState.Loading -> if (!refreshing) {
                        // 下拉刷新时顶部指示器已在转圈，列表内不再重复显示。
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is BrowseUiState.Failed -> item(key = "error") {
                        ErrorScreen(
                            message = "加载失败：${s.message}",
                            onRetry = ::refresh,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    is BrowseUiState.Loaded -> searchResultItems(
                        page = s.page,
                        loadingMore = loadingMore,
                        onLoadMore = ::loadMore,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
                item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

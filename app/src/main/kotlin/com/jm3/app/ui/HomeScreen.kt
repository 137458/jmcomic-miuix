package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
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
import com.jm3.core.model.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Loaded(val query: String, val page: SearchPage) : HomeUiState
    data class Failed(val message: String) : HomeUiState
}

@Composable
fun HomeScreen(gateway: Jm3Gateway, onOpenAlbum: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var lastQuery by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Idle) }
    var loadingMore by remember { mutableStateOf(false) }
    // 仅下拉手势触发的刷新使用顶部指示器；普通搜索由列表内进度条表达，避免误显刷新态。
    var refreshing by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun runSearch(text: String, byPull: Boolean = false) {
        lastQuery = text
        if (byPull) refreshing = true
        scope.launch {
            state = HomeUiState.Loading
            state = withContext(Dispatchers.IO) {
                runCatching { HomeUiState.Loaded(text, gateway.search(text, 1)) }
                    .getOrElse { HomeUiState.Failed(it.message ?: "网络错误") }
            }
            refreshing = false
        }
    }

    // 加载更多失败时保留已有结果
    fun loadMore() {
        val loaded = state as? HomeUiState.Loaded ?: return
        if (loadingMore || loaded.page.currentPage >= loaded.page.totalPages) return
        loadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { gateway.search(loaded.query, loaded.page.currentPage + 1) }.getOrNull()
            }
            loadingMore = false
            if (next != null) {
                state = loaded.copy(page = loaded.page.mergedWith(next))
            }
        }
    }

    // 接近列表尾部时自动加载下一页
    val loadedNow = state as? HomeUiState.Loaded
    LaunchedEffect(loadedNow?.page) {
        if (loadedNow == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val page = (state as? HomeUiState.Loaded)?.page ?: return@collect
                if (last != null && last >= page.items.size - 2) loadMore()
            }
    }

    fun submitSearch() {
        val q = query.trim()
        when {
            q.isEmpty() -> Unit
            q.all { it.isDigit() } -> onOpenAlbum(q) // 车号直达
            else -> runSearch(q)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "JM3") },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { if (lastQuery.isNotEmpty()) runSearch(lastQuery, byPull = true) },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = innerPadding,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "search") {
                    // SearchBar + InputField：胶囊输入框、聚焦展开搜索动作、返回键收起、内置清除键。
                    SearchBar(
                        inputField = {
                            InputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = { submitSearch() },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索 / 车号",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onExpandedChange = { searchExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                        expanded = searchExpanded,
                        outsideEndAction = {
                            IconButton(onClick = ::submitSearch) {
                                Icon(
                                    imageVector = MiuixIcons.Search,
                                    contentDescription = "搜索",
                                )
                            }
                        },
                    ) {}
                }
                when (val s = state) {
                    is HomeUiState.Idle -> item(key = "hint") {
                        EmptyState(
                            title = "输入关键词搜索，或输入车号直达详情",
                            hint = "例如作品名、作者名或纯数字车号",
                        )
                    }
                    is HomeUiState.Loading -> if (!refreshing) {
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
                    is HomeUiState.Failed -> item(key = "error") {
                        ErrorScreen(
                            message = "加载失败：${s.message}",
                            onRetry = { runSearch(lastQuery) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    is HomeUiState.Loaded -> searchResultItems(
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

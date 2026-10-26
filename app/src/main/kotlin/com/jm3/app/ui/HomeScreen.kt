package com.jm3.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Loaded(val query: String, val page: SearchPage) : HomeUiState
    data class Failed(val message: String) : HomeUiState
}

@Composable
fun HomeScreen(gateway: Jm3Gateway, onOpenAlbum: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Idle) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun runSearch(text: String) {
        scope.launch {
            state = HomeUiState.Loading
            state = withContext(Dispatchers.IO) {
                runCatching { HomeUiState.Loaded(text, gateway.search(text, 1)) }
                    .getOrElse { HomeUiState.Failed(it.message ?: "网络错误") }
            }
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

    Scaffold(
        topBar = { TopAppBar(title = "JM3") },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item(key = "search") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "搜索 / 车号",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        text = "搜索",
                        onClick = {
                            val q = query.trim()
                            when {
                                q.isEmpty() -> Unit
                                q.all { it.isDigit() } -> onOpenAlbum(q) // 车号直达
                                else -> runSearch(q)
                            }
                        },
                    )
                }
            }
            when (val s = state) {
                is HomeUiState.Idle -> item(key = "hint") {
                    Text(
                        text = "输入关键词搜索，或输入车号直达详情",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                is HomeUiState.Loading -> item(key = "loading") {
                    Text(text = "加载中…", modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                is HomeUiState.Failed -> item(key = "error") {
                    Text(text = "加载失败：${s.message}", modifier = Modifier.fillMaxWidth().padding(16.dp))
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

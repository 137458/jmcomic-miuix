package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.squircle.squircleClip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.model.TrackingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val trackingDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private sealed interface TrackingUiState {
    data object Loading : TrackingUiState
    data object Loaded : TrackingUiState
    data class Failed(val message: String) : TrackingUiState
}

/** 连载追踪：已关注作品的更新列表，支持一键取消关注。 */
@Composable
fun TrackingScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var items by remember { mutableStateOf<List<TrackingItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<TrackingUiState>(TrackingUiState.Loading) }
    var nextPage by remember { mutableIntStateOf(2) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // 加载更多失败单独置位，供列表底部给出重试入口
    var loadMoreFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var removingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadFirstPage(showLoading: Boolean = true) {
        if (showLoading) state = TrackingUiState.Loading
        nextPage = 2
        endReached = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.trackingList(1) }
            }
            refreshing = false
            result
                .onSuccess {
                    items = it.items
                    total = it.total
                    endReached = it.items.isEmpty() || it.items.size >= it.total
                    state = TrackingUiState.Loaded
                }
                .onFailure { state = TrackingUiState.Failed(it.message ?: "追踪列表加载失败") }
        }
    }

    LaunchedEffect(Unit) { loadFirstPage() }

    fun loadMore() {
        if (loadingMore || endReached || state != TrackingUiState.Loaded) return
        loadingMore = true
        val page = nextPage
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.trackingList(page) }
            }
            loadingMore = false
            result
                .onSuccess { next ->
                    loadMoreFailed = false
                    // 接口按页返回且无总页数，按累计条数对比总数收敛；空页直接视为到底
                    items = (items + next.items).distinctBy { it.id }
                    total = next.total.coerceAtLeast(total)
                    nextPage = page + 1
                    if (next.items.isEmpty() || items.size >= total) endReached = true
                }
                .onFailure { loadMoreFailed = true }
        }
    }

    fun unfollow(item: TrackingItem) {
        if (removingId != null) return
        removingId = item.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.setTracking(item.id) }
            }
            removingId = null
            result
                .onSuccess {
                    items = items.filterNot { it.id == item.id }
                    total = (total - 1).coerceAtLeast(items.size)
                    snackbarHostState.showSnackbar("已取消追踪")
                }
                .onFailure { snackbarHostState.showSnackbar("操作失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "连载追踪",
                subtitle = if (items.isEmpty()) "" else "共 $total 部",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            TrackingUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is TrackingUiState.Failed -> ErrorScreen(
                message = s.message,
                onRetry = { loadFirstPage() },
                modifier = Modifier.padding(innerPadding),
            )
            TrackingUiState.Loaded -> if (items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无追踪",
                    hint = "在详情页开启追更后会出现在这里",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        loadFirstPage(showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding(),
                        ),
                    ) {
                        items(items, key = { it.id }) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                BasicComponent(
                                    title = item.name,
                                    summary = item.updateAt?.toReadableTime(),
                                    startAction = { TrackingCover(item.coverUrl) },
                                    onClick = { onOpenAlbum(item.id) },
                                    endActions = {
                                        IconButton(
                                            onClick = { unfollow(item) },
                                            enabled = removingId != item.id,
                                        ) {
                                            Icon(MiuixIcons.Delete, contentDescription = "取消追踪")
                                        }
                                    },
                                )
                            }
                        }
                        if (items.isNotEmpty()) {
                            item(key = "load-more") {
                                LoadMoreFooter(
                                    loadingMore = loadingMore,
                                    hasMore = !endReached,
                                    loadMoreFailed = loadMoreFailed,
                                    onLoadMore = ::loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 追踪条目封面：与搜索结果行同尺寸，缺失时留白保持行高。 */
@Composable
private fun TrackingCover(url: String?) {
    if (url == null) {
        Spacer(modifier = Modifier.size(width = 48.dp, height = 64.dp))
        return
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .squircleClip(6.dp),
    )
}

/** Unix 秒字符串转本地时间；非数字原样返回，避免吞掉服务端文案。 */
private fun String.toReadableTime(): String =
    toLongOrNull()?.let { trackingDateFormat.format(Date(it * 1000)) } ?: this

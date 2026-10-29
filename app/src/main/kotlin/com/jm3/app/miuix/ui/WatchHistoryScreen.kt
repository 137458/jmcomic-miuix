package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.jm3.core.model.SearchResult
import com.jm3.core.settings.PaginationMode
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 服务端观看记录单页条数；接口不返回总数，用返回条数推断是否还有下一页。 */
private const val WATCH_PAGE_SIZE = 20

private sealed interface WatchUiState {
    data object Loading : WatchUiState
    data object Loaded : WatchUiState
    data class Failed(val message: String) : WatchUiState
}

/** 服务端观看记录：与本地历史不同，这里记录的是账号云端足迹，支持逐条删除。 */
@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val mode = Jm3Graph.paginationMode
    var items by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<WatchUiState>(WatchUiState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var deletingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetch(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        if (showLoading) state = WatchUiState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.watchHistory(targetPage) }
            }
            refreshing = false
            loadingMore = false
            result
                .onSuccess { list ->
                    items = if (replace) list else (items + list).distinctBy { it.id }
                    page = targetPage
                    hasNext = list.size >= WATCH_PAGE_SIZE
                    state = WatchUiState.Loaded
                }
                .onFailure {
                    if (replace) {
                        state = WatchUiState.Failed(it.message ?: "观看记录加载失败")
                    } else {
                        // 追加失败不影响已有列表，仅提示并停止继续翻页
                        hasNext = false
                        scope.launch { snackbarHostState.showSnackbar("加载失败：" + (it.message ?: "请稍后重试")) }
                    }
                }
        }
    }

    fun delete(item: SearchResult) {
        if (deletingId != null) return
        deletingId = item.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.deleteWatchHistory(item.id) }
            }
            deletingId = null
            result
                .onSuccess {
                    items = items.filterNot { it.id == item.id }
                    snackbarHostState.showSnackbar("已删除观看记录")
                }
                .onFailure { snackbarHostState.showSnackbar("删除失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    // 分页模式切换后重新按新形态加载第一页，避免同一份状态混用两种翻页语义
    LaunchedEffect(mode) { fetch(targetPage = 1, replace = true, showLoading = true) }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "云端观看记录",
                subtitle = items.takeIf { it.isNotEmpty() }?.let { "本页 ${it.size} 部" }.orEmpty(),
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            WatchUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is WatchUiState.Failed -> ErrorScreen(
                message = s.message,
                onRetry = { fetch(targetPage = 1, replace = true, showLoading = true) },
                modifier = Modifier.padding(innerPadding),
            )
            WatchUiState.Loaded -> if (items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无观看记录",
                    hint = "阅读作品后会同步到账号云端",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        fetch(targetPage = 1, replace = true, showLoading = false)
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
                                    title = item.title,
                                    summary = listOf(
                                        item.authors.joinToString(" "),
                                        item.tags.joinToString(" "),
                                    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                                    startAction = { WatchCover(item.coverUrl) },
                                    onClick = { onOpenAlbum(item.id) },
                                    endActions = {
                                        IconButton(
                                            onClick = { delete(item) },
                                            enabled = deletingId != item.id,
                                        ) {
                                            Icon(MiuixIcons.Delete, contentDescription = "删除记录")
                                        }
                                    },
                                )
                            }
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    mode == PaginationMode.CLICK -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            text = "上一页",
                                            onClick = { fetch(page - 1, replace = true, showLoading = false) },
                                            enabled = page > 1,
                                            minWidth = 0.dp,
                                            minHeight = 36.dp,
                                        )
                                        Text(
                                            text = "第 $page 页",
                                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        TextButton(
                                            text = "下一页",
                                            onClick = { fetch(page + 1, replace = true, showLoading = false) },
                                            enabled = hasNext,
                                            minWidth = 0.dp,
                                            minHeight = 36.dp,
                                        )
                                    }
                                    loadingMore -> InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                                    hasNext -> TextButton(
                                        text = "加载更多",
                                        onClick = {
                                            loadingMore = true
                                            fetch(page + 1, replace = false, showLoading = false)
                                        },
                                    )
                                    else -> Text(
                                        text = "已经到底了",
                                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 观看记录封面：与搜索结果行同尺寸，缺失时留白保持行高。 */
@Composable
private fun WatchCover(url: String?) {
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

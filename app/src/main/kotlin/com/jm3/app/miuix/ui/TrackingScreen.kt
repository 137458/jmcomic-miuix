package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.isSessionExpired
import com.jm3.core.model.TrackingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
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
    // sessionExpired 区分「重试可能好」和「会话没了」：后者只能去登录，给重试按钮是骗人的
    data class Failed(val message: String, val sessionExpired: Boolean = false) : TrackingUiState
}

/** 连载追踪：已关注作品的更新列表，支持一键取消关注。 */
@Composable
fun TrackingScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
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

    /**
     * 会话失效：清掉本地会话并把整屏切成「去登录」。
     *
     * 重试同一个请求只会再吃一次 401，所以这里不给重试，只留登录入口。
     */
    fun expireSession() {
        state = TrackingUiState.Failed(LOGIN_EXPIRED_MESSAGE, sessionExpired = true)
        scope.launch { Jm3Graph.account.markSessionExpired() }
    }

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
                .onFailure {
                    if (isSessionExpired(it.message)) expireSession() else {
                        state = TrackingUiState.Failed(it.message ?: "追踪列表加载失败")
                    }
                }
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
                .onFailure {
                    if (isSessionExpired(it.message)) expireSession() else loadMoreFailed = true
                }
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
                .onFailure {
                    if (isSessionExpired(it.message)) expireSession() else {
                        snackbarHostState.showSnackbar("操作失败：" + (it.message ?: "请稍后重试"))
                    }
                }
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
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            TrackingUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is TrackingUiState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = { loadFirstPage() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            TrackingUiState.Loaded -> if (items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无追踪",
                    hint = "在详情页开启追更后会出现在这里",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                GlassPullToRefresh(
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
                        contentPadding = innerPadding,
                    ) {
                        items(items, key = { it.id }) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                BasicComponent(
                                    title = item.name,
                                    summary = item.updateAt?.toReadableTime(),
                                    startAction = { TrackingCover(item.id, item.coverUrl) },
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

/** 追踪条目封面：与其他作品行统一缩略图尺寸与圆角，缺失时留白保持行高。 */
@Composable
private fun TrackingCover(albumId: String, url: String?) {
    WorkThumbnail(url = url, albumId = albumId)
}

/** Unix 秒字符串转本地时间；非数字原样返回，避免吞掉服务端文案。 */
private fun String.toReadableTime(): String =
    toLongOrNull()?.let { trackingDateFormat.format(Date(it * 1000)) } ?: this

/**
 * 会话失效全屏态：文案位置与排版同 [ErrorScreen]，但动作是「去登录」而不是「重试」——
 * 会话在服务端已经作废，重试同一个请求只会再吃一次 401。
 *
 * 跨屏幕复用（连载追踪、收藏页），所以做成同包的 internal 顶层函数而不是各页私有实现。
 * 暂不并回 Common.kt：[ErrorScreen] 的按钮语义固定为「重试」，没有承载自定义动作的位置；
 * 等 Common.kt 提供带动作的全屏态时，两者应合并。
 */
@Composable
internal fun LoginExpiredScreen(onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = LOGIN_EXPIRED_MESSAGE,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(text = "去登录", onClick = onOpenLogin)
    }
}

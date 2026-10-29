package com.jm3.app.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.history.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val historyDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private sealed interface HistoryListState {
    data object Loading : HistoryListState
    data object Loaded : HistoryListState
    data class Failed(val message: String) : HistoryListState
}

/** 按作品聚合的阅读历史：最近一章用于一键续读，count 为该作品已读章节数。 */
private data class AlbumHistory(
    val albumId: String,
    val latest: HistoryEntry,
    val chapters: Int,
)

/** 保持最近阅读时间倒序，把同一作品的记录折叠成一条。 */
private fun List<HistoryEntry>.groupByAlbum(): List<AlbumHistory> {
    val order = mutableListOf<String>()
    val latest = mutableMapOf<String, HistoryEntry>()
    val counts = mutableMapOf<String, Int>()
    for (entry in this) {
        if (entry.albumId !in latest) {
            latest[entry.albumId] = entry
            order += entry.albumId
        }
        counts[entry.albumId] = (counts[entry.albumId] ?: 0) + 1
    }
    return order.map { AlbumHistory(it, latest.getValue(it), counts.getValue(it)) }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenReader: (String) -> Unit,
) {
    val repository = Jm3Graph.history
    val pageSize = 20
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var state by remember { mutableStateOf<HistoryListState>(HistoryListState.Loading) }
    var nextPage by remember { mutableStateOf(2) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // 加载更多失败单独置位，供列表底部给出重试入口
    var loadMoreFailed by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadFirstPage() {
        scope.launch {
            state = HistoryListState.Loading
            nextPage = 2
            endReached = false
            runCatching { repository.page(1, pageSize) }
                .onSuccess {
                    entries = it
                    state = HistoryListState.Loaded
                    if (it.size < pageSize) endReached = true
                }
                .onFailure { state = HistoryListState.Failed(it.message ?: "历史加载失败") }
        }
    }

    LaunchedEffect(Unit) {
        loadFirstPage()
    }

    fun loadMore() {
        if (loadingMore || endReached || state != HistoryListState.Loaded) return
        loadingMore = true
        scope.launch {
            val result = runCatching { repository.page(nextPage, pageSize) }
            loadingMore = false
            result.onSuccess { next ->
                loadMoreFailed = false
                if (next.isEmpty()) {
                    endReached = true
                } else {
                    entries = entries + next
                    nextPage += 1
                    if (next.size < pageSize) endReached = true
                }
            }.onFailure { loadMoreFailed = true }
        }
    }

    /** 删除整部作品的阅读记录：聚合行删除后按 albumId 从当前列表移除。 */
    fun removeAlbum(albumId: String) {
        scope.launch {
            runCatching { repository.deleteAlbum(albumId) }
            entries = entries.filterNot { it.albumId == albumId }
        }
    }

    fun clearAll() {
        if (clearing) return
        clearing = true
        scope.launch {
            runCatching { repository.clear() }
            clearing = false
            showClearConfirm = false
            entries = emptyList()
            state = HistoryListState.Loaded
        }
    }

    val albums = remember(entries) { entries.groupByAlbum() }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "阅读历史",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    if (albums.isNotEmpty()) {
                        TextButton(
                            text = "清空",
                            onClick = { showClearConfirm = true },
                            minWidth = 0.dp,
                            minHeight = 36.dp,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            HistoryListState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is HistoryListState.Failed -> ErrorScreen(
                message = s.message,
                onRetry = ::loadFirstPage,
                modifier = Modifier.padding(innerPadding),
            )
            HistoryListState.Loaded -> {
                if (albums.isEmpty()) {
                    CenteredEmptyState(
                        title = "暂无阅读历史",
                        hint = "读过的章节会出现在这里",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                    ) {
                        items(albums, key = { it.albumId }) { album ->
                            AlbumHistoryCard(
                                album = album,
                                onResume = { onOpenReader(album.latest.photoId) },
                                onOpenDetail = { onOpenAlbum(album.albumId) },
                                onDelete = { removeAlbum(album.albumId) },
                            )
                        }
                        if (albums.isNotEmpty()) {
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

        OverlayDialog(
            show = showClearConfirm,
            title = "清空阅读历史",
            onDismissRequest = { showClearConfirm = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = "将删除本机全部阅读记录，已下载的章节不受影响。",
                    style = MiuixTheme.textStyles.body2,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // HyperOS 弹窗惯例：按钮纵向堆叠、各占满宽，避免横排等分与系统弹窗观感不一致
                TextButton(
                    text = "取消",
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = if (clearing) "清空中…" else "清空",
                    onClick = ::clearAll,
                    enabled = !clearing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 聚合行：封面 + 最近章节 + 已读话数，整行点击续读；尾部删除整部记录。 */
@Composable
private fun AlbumHistoryCard(
    album: AlbumHistory,
    onResume: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onResume)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = album.latest.coverUrl
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 58.dp, height = 78.dp).squircleClip(8.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 58.dp, height = 78.dp).squircleClip(8.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.latest.title,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共读 ${album.chapters} 话 · ${historyDateFormat.format(Date(album.latest.updatedAt))}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        text = "继续阅读",
                        onClick = onResume,
                        minWidth = 0.dp,
                        minHeight = 32.dp,
                    )
                    TextButton(
                        text = "详情",
                        onClick = onOpenDetail,
                        minWidth = 0.dp,
                        minHeight = 32.dp,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除该作品记录")
            }
        }
    }
}
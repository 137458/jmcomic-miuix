package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.history.HistoryEntry
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.settings.PaginationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
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
    val mode = Jm3Graph.preferences.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    // 在途、失败、是否还有下一页都由 PagedFeed 记；仓库按 pageSize 分页，满页即代表还有下一页
    var feed by remember { mutableStateOf(PagedFeed<HistoryEntry>()) }
    var state by remember { mutableStateOf<HistoryListState>(HistoryListState.Loading) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadFirstPage() {
        scope.launch {
            state = HistoryListState.Loading
            runCatching { repository.page(1, pageSize) }
                .onSuccess {
                    feed = PagedFeed<HistoryEntry>().replace(
                        PageSlice(items = it, hasMore = it.size >= pageSize, page = 1),
                    ) { entry -> entry.photoId }
                    state = HistoryListState.Loaded
                }
                .onFailure { state = HistoryListState.Failed(it.message ?: "历史加载失败") }
        }
    }

    LaunchedEffect(Unit) {
        loadFirstPage()
    }
    PaginationModeReloadEffect(mode) { loadFirstPage() }

    /** 点击分页：仓库按固定页大小切片，整页替换后回到列表顶部。 */
    fun jumpToPage(target: Int) {
        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
        if (resolved == feed.page) return
        scope.launch {
            runCatching { repository.page(resolved, pageSize) }
                .onSuccess {
                    feed = PagedFeed<HistoryEntry>().replace(
                        PageSlice(items = it, hasMore = it.size >= pageSize, page = resolved),
                    ) { entry -> entry.photoId }
                    state = HistoryListState.Loaded
                    listState.scrollToItem(0)
                }
                .onFailure { state = HistoryListState.Failed(it.message ?: "历史加载失败") }
        }
    }

    fun loadMore() {
        if (state != HistoryListState.Loaded) return
        val requested = feed.beginLoadMore()
        if (requested === feed) return
        val page = requested.page + 1
        feed = requested
        scope.launch {
            runCatching { repository.page(page, pageSize) }
                .onSuccess { next ->
                    feed = requested.append(
                        PageSlice(items = next, hasMore = next.size >= pageSize, page = page),
                    ) { entry -> entry.photoId }
                }
                .onFailure { feed = requested.failLoadMore() }
        }
    }

    /** 删除整部作品的阅读记录：聚合行删除后按 albumId 从当前列表移除。 */
    fun removeAlbum(albumId: String) {
        scope.launch {
            runCatching { repository.deleteAlbum(albumId) }
            feed = feed.copy(items = feed.items.filterNot { it.albumId == albumId })
        }
    }

    fun clearAll() {
        if (clearing) return
        clearing = true
        scope.launch {
            runCatching { repository.clear() }
            clearing = false
            showClearConfirm = false
            feed = PagedFeed()
            state = HistoryListState.Loaded
        }
    }

    val albums = remember(feed.items) { feed.items.groupByAlbum() }

    // 触底加载只在无限滚动模式生效；点击翻页由列表底部翻页条驱动。
    if (!clickPaging) {
        LoadMoreOnScroll(listState = listState, feed = feed, onLoadMore = ::loadMore)
    }

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
                    TrackedLazyColumn(
                        state = listState,
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize(),
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
                            item(key = "pagination-footer") {
                                if (clickPaging) {
                                    PagerBar(
                                        page = feed.page,
                                        hasMore = feed.hasMore,
                                        onPageChange = ::jumpToPage,
                                    )
                                } else {
                                    LoadMoreFooter(feed = feed, onLoadMore = ::loadMore)
                                }
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
    val heroHost = LocalAlbumHeroHost.current
    WorkRowCard(
        coverUrl = album.latest.coverUrl,
        title = album.latest.title,
        onClick = onResume,
        albumId = album.albumId,
        subtitle = "共读 ${album.chapters} 话 · ${historyDateFormat.format(Date(album.latest.updatedAt))}",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        textEndPadding = 4.dp,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除该作品记录")
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                text = "继续阅读",
                onClick = onResume,
                minWidth = 0.dp,
                minHeight = 32.dp,
            )
            TextButton(
                text = "详情",
                onClick = {
                    heroHost?.startForward(album.albumId, album.latest.coverUrl, HERO_CARD_CORNER_DP)
                    onOpenDetail()
                },
                minWidth = 0.dp,
                minHeight = 32.dp,
            )
        }
    }
}
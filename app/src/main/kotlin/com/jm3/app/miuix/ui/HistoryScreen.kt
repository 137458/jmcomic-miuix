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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.skin.LocalSkin
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.history.HistoryEntry
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.runCatchingSuspending
import com.jm3.core.settings.PaginationMode
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal sealed interface HistoryListState {
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
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenReader: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val repository = deps.history
    val pageSize = 20
    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    // 翻页在途守卫：仓库读取期间禁用翻页条，避免连点重复请求
    var pageBusy by remember { mutableStateOf(false) }
    var requestToken by remember { mutableIntStateOf(0) }
    // 在途、失败、是否还有下一页都由 PagedFeed 记；仓库按 pageSize 分页，满页即代表还有下一页
    var feed by remember { mutableStateOf(PagedFeed<HistoryEntry>()) }
    var state by remember { mutableStateOf<HistoryListState>(HistoryListState.Loading) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun loadFirstPage() {
        val token = ++requestToken
        pageBusy = false
        scope.launch {
            state = HistoryListState.Loading
            val outcome = runCatchingSuspending { repository.page(1, pageSize) }
            if (token != requestToken) return@launch
            outcome
                .onSuccess {
                    feed = PagedFeed<HistoryEntry>().replace(
                        PageSlice(items = it, hasMore = it.size >= pageSize, page = 1),
                    ) { entry -> entry.photoId }
                    state = HistoryListState.Loaded
                }
                .onFailure { state = HistoryListState.Failed(failureCopy(it, "历史加载失败")) }
        }
    }

    LaunchedEffect(Unit) {
        loadFirstPage()
    }
    PaginationModeReloadEffect(mode) { loadFirstPage() }

    /** 点击分页：仓库按固定页大小切片，整页替换后回到列表顶部。 */
    fun jumpToPage(target: Int) {
        if (pageBusy) return
        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
        if (resolved == feed.page) return
        pageBusy = true
        val token = ++requestToken
        scope.launch {
            try {
                val outcome = runCatchingSuspending { repository.page(resolved, pageSize) }
                if (token != requestToken) return@launch
                outcome
                    .onSuccess {
                        feed = PagedFeed<HistoryEntry>().replace(
                            PageSlice(items = it, hasMore = it.size >= pageSize, page = resolved),
                        ) { entry -> entry.photoId }
                        state = HistoryListState.Loaded
                        listState.scrollToItem(0)
                    }
                    .onFailure { state = HistoryListState.Failed(failureCopy(it, "历史加载失败")) }
            } finally {
                if (token == requestToken) pageBusy = false
            }
        }
    }

    fun loadMore() {
        if (state != HistoryListState.Loaded) return
        val requested = feed.beginLoadMore()
        if (requested === feed) return
        val page = requested.page + 1
        val token = requestToken
        feed = requested
        scope.launch {
            val outcome = runCatchingSuspending { repository.page(page, pageSize) }
            if (token != requestToken) return@launch
            outcome
                .onSuccess { next ->
                    feed = feed.append(
                        PageSlice(items = next, hasMore = next.size >= pageSize, page = page),
                    ) { entry -> entry.photoId }
                }
                .onFailure { feed = feed.failLoadMore() }
        }
    }

    /** 删除整部作品的阅读记录：聚合行删除后按 albumId 从最新列表移除，并提供撤销入口。 */
    fun removeAlbum(albumId: String) {
        val initialSlice = feed.items.removeMatching { it.albumId == albumId }
        if (initialSlice.removed.isEmpty()) return
        val token = requestToken
        scope.launch {
            val deleteOutcome = runCatchingSuspending { repository.deleteAlbum(albumId) }
            if (deleteOutcome.isFailure) {
                snackbarHostState.showSnackbar("删除失败")
                return@launch
            }
            if (token != requestToken) return@launch
            val applied = removeAlbumFromFeed(feed, albumId)
            val removed = applied.slice.removed.ifEmpty { initialSlice.removed }
            val insertIndex = if (applied.slice.removed.isNotEmpty()) applied.slice.insertIndex else initialSlice.insertIndex
            feed = applied.feed
            val result = snackbarHostState.showSnackbar(
                message = "已删除阅读记录",
                actionLabel = "撤销",
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (token != requestToken) return@launch
                val restoreOutcome = runCatchingSuspending {
                    removed.forEach { repository.upsert(it) }
                }
                if (restoreOutcome.isSuccess) {
                    val restored = restoreAlbumToFeed(
                        latestFeed = feed,
                        removed = removed,
                        insertIndex = insertIndex,
                        requestTokenAtDelete = token,
                        currentRequestToken = requestToken,
                    )
                    if (restored != null) {
                        feed = restored
                    }
                } else {
                    snackbarHostState.showSnackbar("撤销失败")
                }
            }
        }
    }

    fun clearAll() {
        if (clearing) return
        clearing = true
        val token = ++requestToken
        pageBusy = false
        scope.launch {
            val outcome = try {
                runCatchingSuspending { repository.clear() }
            } finally {
                clearing = false
            }
            showClearConfirm = false
            val resolution = resolveClearAllOutcome(
                currentFeed = feed,
                currentState = state,
                outcome = outcome,
                requestTokenAtClear = token,
                currentRequestToken = requestToken,
            )
            feed = resolution.feed
            state = resolution.state
            if (resolution.showFailureSnackbar) {
                snackbarHostState.showSnackbar("清除失败")
            }
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
                        MinTouchTarget(onClick = { showClearConfirm = true }) {
                            TextButton(
                                text = "清空",
                                onClick = { showClearConfirm = true },
                                minWidth = 0.dp,
                                minHeight = 36.dp,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
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
                    // 格式化器整页一份：放进行项内会让滚动进出反复构造 DateTimeFormatter
                    val dateFormat = rememberDateFormatter()
                    TrackedLazyColumn(
                        state = listState,
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        items(albums, key = { it.albumId }) { album ->
                            AlbumHistoryCard(
                                album = album,
                                dateFormat = dateFormat,
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
                                        enabled = !pageBusy,
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
            maxWidth = overlayDialogMaxWidth(),
            title = "清空阅读历史",
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = { showClearConfirm = false },
        ) {
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
    dateFormat: DateTimeFormatter,
    onResume: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    val heroHost = LocalAlbumHeroHost.current
    val cardCorner = LocalSkin.current.shapes.cardCorner
    WorkRowCard(
        coverUrl = album.latest.coverUrl,
        title = album.latest.title,
        onClick = onResume,
        albumId = album.albumId,
        subtitle = "共读 ${album.chapters} 话 · ${formatEpochMillis(album.latest.updatedAt, dateFormat)}",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        textEndPadding = 4.dp,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除该作品记录")
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MinTouchTarget(onClick = onResume) {
                TextButton(
                    text = "继续阅读",
                    onClick = onResume,
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                )
            }
            MinTouchTarget(
                onClick = {
                    heroHost?.startForward(album.albumId, album.latest.coverUrl, cardCorner.value)
                    onOpenDetail()
                },
            ) {
                TextButton(
                    text = "详情",
                    onClick = {
                        heroHost?.startForward(album.albumId, album.latest.coverUrl, cardCorner.value)
                        onOpenDetail()
                    },
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                )
            }
        }
    }
}

internal data class HistoryRemoveResult(
    val feed: PagedFeed<HistoryEntry>,
    val slice: RemovedSlice<HistoryEntry>,
)

internal fun removeAlbumFromFeed(
    latestFeed: PagedFeed<HistoryEntry>,
    albumId: String,
): HistoryRemoveResult {
    val slice = latestFeed.items.removeMatching { it.albumId == albumId }
    return HistoryRemoveResult(
        feed = latestFeed.copy(items = slice.remaining),
        slice = slice,
    )
}

internal fun restoreAlbumToFeed(
    latestFeed: PagedFeed<HistoryEntry>,
    removed: List<HistoryEntry>,
    insertIndex: Int,
    requestTokenAtDelete: Int,
    currentRequestToken: Int,
): PagedFeed<HistoryEntry>? {
    if (requestTokenAtDelete != currentRequestToken) return null
    return latestFeed.copy(
        items = latestFeed.items.restoreAt(removed, insertIndex) { it.photoId },
    )
}

internal data class ClearAllOutcome(
    val feed: PagedFeed<HistoryEntry>,
    val state: HistoryListState,
    val showFailureSnackbar: Boolean,
)

internal fun resolveClearAllOutcome(
    currentFeed: PagedFeed<HistoryEntry>,
    currentState: HistoryListState,
    outcome: Result<Unit>,
    requestTokenAtClear: Int,
    currentRequestToken: Int,
): ClearAllOutcome = if (outcome.isSuccess) {
    if (requestTokenAtClear == currentRequestToken) {
        ClearAllOutcome(
            feed = PagedFeed(),
            state = HistoryListState.Loaded,
            showFailureSnackbar = false,
        )
    } else {
        ClearAllOutcome(
            feed = currentFeed,
            state = currentState,
            showFailureSnackbar = false,
        )
    }
} else {
    ClearAllOutcome(
        feed = currentFeed,
        state = currentState,
        showFailureSnackbar = true,
    )
}

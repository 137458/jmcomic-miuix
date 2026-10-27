package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.jm3.app.Jm3Graph
import com.jm3.app.data.download.decodePhotoPages
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val photo: PhotoPages,
        val chapters: List<Chapter>,
        val chapterIndex: Int,
    ) : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

@Composable
fun ReaderScreen(
    photoId: String,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }
    val scope = rememberCoroutineScope()

    // 优先读已下载落盘数据，缺失时回退网关；供首载与失败重试复用
    fun load() {
        scope.launch {
            state = withContext(Dispatchers.IO) {
                val saved = runCatching { Jm3Graph.downloadManager.find(photoId) }.getOrNull()
                val local = saved
                    ?.takeIf { it.record.status == DownloadStatus.DONE }
                    ?.pagesJson
                    ?.let { decodePhotoPages(it) }
                    ?.takeIf { it.images.isNotEmpty() }
                if (local != null) {
                    // 离线页的 albumId 未落盘，用下载记录的 albumId 补章节列表
                    val chapters = runCatching { Jm3Graph.gateway.album(saved.record.albumId).chapters }
                        .getOrDefault(emptyList())
                    ReaderUiState.Loaded(
                        photo = local,
                        chapters = chapters,
                        chapterIndex = chapters.indexOfFirst { it.id == photoId },
                    )
                } else {
                    runCatching {
                        val photo = Jm3Graph.gateway.photo(photoId)
                        // 章节列表用于上下话与目录抽屉；失败不阻塞阅读
                        val chapters = runCatching { Jm3Graph.gateway.album(photo.albumId).chapters }
                            .getOrDefault(emptyList())
                        ReaderUiState.Loaded(
                            photo = photo,
                            chapters = chapters,
                            chapterIndex = chapters.indexOfFirst { it.id == photo.photoId },
                        )
                    }.getOrElse { ReaderUiState.Failed(it.message ?: "章节加载失败") }
                }
            }
        }
    }

    LaunchedEffect(photoId) {
        load()
    }

    when (val s = state) {
        is ReaderUiState.Loading -> Scaffold(
            topBar = { TopAppBar(title = "阅读", navigationIcon = { BackButton(onBack = onBack) }) },
        ) { innerPadding ->
            LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        is ReaderUiState.Failed -> Scaffold(
            topBar = { TopAppBar(title = "阅读", navigationIcon = { BackButton(onBack = onBack) }) },
        ) { innerPadding ->
            ErrorScreen(
                message = "章节加载失败：${s.message}",
                onRetry = ::load,
                modifier = Modifier.padding(innerPadding),
            )
        }
        is ReaderUiState.Loaded -> {
            LaunchedEffect(s.photo.photoId) {
                val coverUrl = withContext(Dispatchers.IO) {
                    runCatching { Jm3Graph.gateway.coverUrl(s.photo.albumId) }.getOrNull()
                }
                runCatching {
                    Jm3Graph.history.upsert(
                        HistoryEntry(
                            photoId = s.photo.photoId,
                            albumId = s.photo.albumId,
                            title = s.photo.title,
                            coverUrl = coverUrl,
                            updatedAt = 0L,
                        ),
                    )
                }
            }
            ReaderPages(
                photo = s.photo,
                chapters = s.chapters,
                chapterIndex = s.chapterIndex,
                onBack = onBack,
                onOpenChapter = onOpenChapter,
            )
        }
    }
}

// ScrollBar 目前在 Miuix 标记为实验 API（warning 级别），仅此处显式表态使用
@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun ReaderPages(
    photo: PhotoPages,
    chapters: List<Chapter>,
    chapterIndex: Int,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    val imageLoader = rememberReaderImageLoader(Jm3Graph.gateway, Jm3Graph.downloadFiles)
    val listState = rememberLazyListState()
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    // 页面占位为 fillMaxWidth + 3:4，解码尺寸按同比例推算；预取与展示共用该尺寸，
    // 否则两次解码尺寸不同会让内存缓存键不一致，预取实际命中不到。
    val pageWidthPx = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    val pageSize = remember(pageWidthPx) { Size(pageWidthPx, pageWidthPx * 4 / 3) }
    // 已发起预加载的页号；翻页向前向后都只发一次
    val prefetched = remember { mutableSetOf<Int>() }
    var currentPage by remember(photo) { mutableStateOf(if (photo.images.isEmpty()) 0 else 1) }
    var showPageSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var pickerValue by remember { mutableIntStateOf(1) }

    val prevChapter = chapters.getOrNull(chapterIndex - 1)?.takeIf { chapterIndex > 0 }
    val nextChapter = chapters.getOrNull(chapterIndex + 1)?.takeIf { chapterIndex in 0 until chapters.lastIndex }
    val totalPages = photo.images.size.coerceAtLeast(1)

    fun jumpToPage(page: Int) {
        val target = page.coerceIn(1, totalPages)
        currentPage = target
        // 跳页期间阻止“可见页→页码”回写覆盖目标页：滚动落定后再恢复由可见页驱动。
        scope.launch { listState.scrollToItem(target - 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = photo.title,
                subtitle = "第 $currentPage / ${photo.images.size} 页",
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        floatingToolbar = {
            ReaderToolbar(
                currentPage = if (totalPages == 0) 0 else currentPage,
                totalPages = photo.images.size,
                prevChapter = prevChapter,
                nextChapter = nextChapter,
                onPrevChapter = { prevChapter?.let { onOpenChapter(it.id) } },
                onNextChapter = { nextChapter?.let { onOpenChapter(it.id) } },
                onJumpPage = {
                    pickerValue = currentPage
                    showPageSheet = true
                },
                onChapters = { showChapterSheet = true },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                items(photo.images, key = { it.index }) { image ->
                    ReaderPage(image = image, imageLoader = imageLoader, pageSize = pageSize)
                }
                // 底部悬浮工具栏会遮住最后一页末尾，留出可滚动空间
                item(key = "toolbar-space") { Spacer(modifier = Modifier.height(96.dp)) }
            }
            // 长图阅读时长列表无位置反馈，补 Miuix 滚动条（拖动可直接跳页）
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerPadding,
            )
        }

        OverlayBottomSheet(
            show = showPageSheet,
            title = "跳页",
            onDismissRequest = { showPageSheet = false },
        ) {
            NumberPicker(
                value = pickerValue,
                onValueChange = { pickerValue = it },
                range = 1..totalPages,
                label = { it.toString() },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    showPageSheet = false
                    jumpToPage(pickerValue)
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(text = "跳转到第 $pickerValue 页")
            }
        }

        OverlayBottomSheet(
            show = showChapterSheet,
            title = "章节",
            onDismissRequest = { showChapterSheet = false },
        ) {
            if (chapters.isEmpty()) {
                Text(
                    text = "暂无章节信息",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(chapters, key = { it.id }) { chapter ->
                        BasicComponent(
                            title = chapter.title,
                            summary = if (chapter.id == photo.photoId) "当前章节" else null,
                            onClick = {
                                showChapterSheet = false
                                if (chapter.id != photo.photoId) onOpenChapter(chapter.id)
                            },
                        )
                    }
                    item(key = "chapter-gap") { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }

    // 顶栏页码与预取共用一次订阅：同一次 collect 内完成，collectLatest 让快速滚动只处理最新页
    LaunchedEffect(listState, photo.images, imageLoader, pageSize) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { first ->
                // 末尾补了工具栏留白项，页码需按图片数收敛，避免显示 N+1/N
                val total = photo.images.size
                currentPage = if (total == 0) 0 else (first + 1).coerceAtMost(total)
                // 预加载可见页之后的 1..3 页
                for (offset in 1..3) {
                    val index = first + offset
                    if (index < photo.images.size && prefetched.add(index)) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(photo.images[index])
                                .size(pageSize)
                                .build(),
                        )
                    }
                }
            }
    }
}

/** 阅读器底部悬浮工具栏：上一话/跳页/下一话/目录。 */
@Composable
private fun ReaderToolbar(
    currentPage: Int,
    totalPages: Int,
    prevChapter: Chapter?,
    nextChapter: Chapter?,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onJumpPage: () -> Unit,
    onChapters: () -> Unit,
) {
    FloatingToolbar {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevChapter, enabled = prevChapter != null) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = "上一话",
                )
            }
            TextButton(
                text = "第 $currentPage / $totalPages 页",
                onClick = onJumpPage,
                minWidth = 0.dp,
            )
            IconButton(onClick = onNextChapter, enabled = nextChapter != null) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = "下一话",
                )
            }
            IconButton(onClick = onChapters) {
                Icon(imageVector = MiuixIcons.ListView, contentDescription = "章节")
            }
        }
    }
}

@Composable
private fun ReaderPage(image: PageImage, imageLoader: ImageLoader, pageSize: Size) {
    val context = LocalPlatformContext.current
    var retryKey by remember(image) { mutableIntStateOf(0) }
    // 与预取请求同一尺寸，保证内存缓存可复用；重试时重建请求绕过失败缓存
    val request = remember(image, pageSize, context, retryKey) {
        ImageRequest.Builder(context).data(image).size(pageSize).build()
    }
    var failed by remember(image, retryKey) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "图片加载失败")
                TextButton(
                    text = "重试",
                    onClick = { failed = false; retryKey++ },
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                )
            }
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            onSuccess = { failed = false },
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

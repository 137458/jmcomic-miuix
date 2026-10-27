package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.jm3.app.Jm3Graph
import com.jm3.app.data.download.decodePhotoPages
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(val photo: PhotoPages) : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

@Composable
fun ReaderScreen(photoId: String, onBack: () -> Unit) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }
    val scope = rememberCoroutineScope()

    // 优先读已下载落盘数据，缺失时回退网关；供首载与失败重试复用
    fun load() {
        scope.launch {
            state = withContext(Dispatchers.IO) {
                val local = runCatching { Jm3Graph.downloadManager.find(photoId) }.getOrNull()
                    ?.takeIf { it.record.status == DownloadStatus.DONE }
                    ?.pagesJson
                    ?.let { decodePhotoPages(it) }
                    ?.takeIf { it.images.isNotEmpty() }
                if (local != null) {
                    ReaderUiState.Loaded(local)
                } else {
                    runCatching { ReaderUiState.Loaded(Jm3Graph.gateway.photo(photoId)) }
                        .getOrElse { ReaderUiState.Failed(it.message ?: "章节加载失败") }
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
            ReaderPages(photo = s.photo, onBack = onBack)
        }
    }
}

// ScrollBar 目前在 Miuix 标记为实验 API（warning 级别），仅此处显式表态使用
@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun ReaderPages(photo: PhotoPages, onBack: () -> Unit) {
    val imageLoader = rememberReaderImageLoader(Jm3Graph.gateway, Jm3Graph.downloadFiles)
    val listState = rememberLazyListState()
    val context = LocalPlatformContext.current
    // 页面占位为 fillMaxWidth + 3:4，解码尺寸按同比例推算；预取与展示共用该尺寸，
    // 否则两次解码尺寸不同会让内存缓存键不一致，预取实际命中不到。
    val pageWidthPx = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
    val pageSize = remember(pageWidthPx) { Size(pageWidthPx, pageWidthPx * 4 / 3) }
    // 已发起预加载的页号；翻页向前向后都只发一次
    val prefetched = remember { mutableSetOf<Int>() }
    var currentPage by remember(photo) { mutableStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = photo.title,
                subtitle = "第 $currentPage / ${photo.images.size} 页",
                navigationIcon = { BackButton(onBack = onBack) },
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
            }
            // 长图阅读时长列表无位置反馈，补 Miuix 滚动条（拖动可直接跳页）
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerPadding,
            )
        }
    }

    // 顶栏页码与预取共用一次订阅：同一次 collect 内完成，collectLatest 让快速滚动只处理最新页
    LaunchedEffect(listState, photo.images, imageLoader, pageSize) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { first ->
                currentPage = first + 1
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

@Composable
private fun ReaderPage(image: PageImage, imageLoader: ImageLoader, pageSize: Size) {
    val context = LocalPlatformContext.current
    // 与预取请求同一尺寸，保证内存缓存可复用
    val request = remember(image, pageSize, context) {
        ImageRequest.Builder(context).data(image).size(pageSize).build()
    }
    var failed by remember(image) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
        if (failed) {
            Text(text = "图片加载失败", modifier = Modifier.align(Alignment.Center))
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

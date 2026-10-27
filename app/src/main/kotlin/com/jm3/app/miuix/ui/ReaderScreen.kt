package com.jm3.app.miuix.ui

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private const val COMMENT_MAX_LENGTH = 200
private const val VOLUME_THROTTLE_MS = 150L
private const val TRACK_MUTE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

/** 音量键翻页分发：Activity 收到按键后转交挂载中的阅读器，未挂载时返回 false 走系统音量。 */
object ReaderVolumeKeys {
    @Volatile
    var handler: ((Int) -> Boolean)? = null

    fun dispatch(keyCode: Int): Boolean = handler?.invoke(keyCode) ?: false
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val photo: PhotoPages,
        val chapters: List<Chapter>,
        val chapterIndex: Int,
        val favorited: Boolean,
        val liked: Boolean,
    ) : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

@Composable
fun ReaderScreen(
    photoId: String,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
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
                    // 离线页的 albumId 未落盘，用下载记录的 albumId 补章节列表与收藏/点赞状态
                    val detail = runCatching { Jm3Graph.gateway.album(saved.record.albumId) }.getOrNull()
                    ReaderUiState.Loaded(
                        photo = local,
                        chapters = detail?.chapters.orEmpty(),
                        chapterIndex = detail?.chapters?.indexOfFirst { it.id == photoId } ?: -1,
                        favorited = detail?.favorited == true,
                        liked = detail?.liked == true,
                    )
                } else {
                    runCatching {
                        val photo = Jm3Graph.gateway.photo(photoId)
                        // 详情仅用于章节列表与收藏/点赞状态；失败不阻塞阅读
                        val detail = runCatching { Jm3Graph.gateway.album(photo.albumId) }.getOrNull()
                        ReaderUiState.Loaded(
                            photo = photo,
                            chapters = detail?.chapters.orEmpty(),
                            chapterIndex = detail?.chapters?.indexOfFirst { it.id == photo.photoId } ?: -1,
                            favorited = detail?.favorited == true,
                            liked = detail?.liked == true,
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
                favoritedInit = s.favorited,
                likedInit = s.liked,
                onBack = onBack,
                onOpenChapter = onOpenChapter,
                onOpenLogin = onOpenLogin,
                snackbarHostState = snackbarHostState,
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
    favoritedInit: Boolean,
    likedInit: Boolean,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val imageLoader = rememberReaderImageLoader(Jm3Graph.gateway, Jm3Graph.downloadFiles)
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState { photo.images.size }
    val context = LocalPlatformContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val scope = rememberCoroutineScope()
    val user by Jm3Graph.account.user.collectAsState()
    // 页面占位为 fillMaxWidth + 3:4，解码尺寸按同比例推算；预取与展示共用该尺寸，
    // 否则两次解码尺寸不同会让内存缓存键不一致，预取实际命中不到。
    val pageWidthPx = windowSize.width.coerceAtLeast(1)
    val pageSize = remember(pageWidthPx) { Size(pageWidthPx, pageWidthPx * 4 / 3) }
    // 左右模式整屏展示，解码尺寸按屏幕容器
    val screenSize = remember(windowSize.width, windowSize.height) {
        Size(windowSize.width.coerceAtLeast(1), windowSize.height.coerceAtLeast(1))
    }
    // 已发起预加载的页号；翻页向前向后都只发一次
    val prefetched = remember { mutableSetOf<Int>() }
    var currentPage by remember(photo) { mutableStateOf(if (photo.images.isEmpty()) 0 else 1) }
    var showPageSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var pickerValue by remember { mutableIntStateOf(1) }
    // 原版点击正文切换上下栏显隐
    var chromeVisible by remember { mutableStateOf(true) }
    var horizontal by remember { mutableStateOf(false) }
    var lastVolumeAt by remember { mutableLongStateOf(0L) }
    var favorited by remember(photo.albumId) { mutableStateOf(favoritedInit) }
    var liked by remember(photo.albumId) { mutableStateOf(likedInit) }
    var engagementBusy by remember { mutableStateOf(false) }
    var comments by remember(photo.albumId) { mutableStateOf<CommentPage?>(null) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsFailed by remember { mutableStateOf(false) }
    var commentText by remember(photo.albumId) { mutableStateOf("") }
    var commentBusy by remember { mutableStateOf(false) }
    // 评论抽屉内的回复：目标评论与发送态
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyBusy by remember { mutableStateOf(false) }
    var showTrackPrompt by remember { mutableStateOf(false) }
    var trackPromptShown by remember { mutableStateOf(false) }
    var trackMuteChecked by remember { mutableStateOf(false) }
    var trackBusy by remember { mutableStateOf(false) }

    // 悬浮工具栏毛玻璃：内容层录屏作为 backdrop，工具栏按胶囊形状采样并模糊
    val toolbarBackdrop = rememberLayerBackdrop()
    val toolbarBlurRadius = with(LocalDensity.current) { 22.dp.toPx() }

    val prevChapter = chapters.getOrNull(chapterIndex - 1)?.takeIf { chapterIndex > 0 }
    val nextChapter = chapters.getOrNull(chapterIndex + 1)
        ?.takeIf { chapterIndex in 0 until chapters.lastIndex }
    val totalPages = photo.images.size.coerceAtLeast(1)
    val lastIndex = (photo.images.size - 1).coerceAtLeast(0)

    LaunchedEffect(Unit) {
        horizontal = Jm3Graph.settings.readerHorizontal()
        if (!Jm3Graph.settings.volumeKeyHintShown()) {
            delay(1000)
            snackbarHostState.showSnackbar("可使用音量键＋翻页")
            Jm3Graph.settings.markVolumeKeyHintShown()
        }
    }

    /** 跳页：左右模式走弹簧动画，直向模式滚动列表并冻结页码回写。 */
    fun goToPage(page: Int) {
        val target = page.coerceIn(1, totalPages)
        if (horizontal) {
            currentPage = target
            scope.launch { pagerState.springAnimateToPage(target - 1) }
        } else {
            currentPage = target
            // 跳页期间阻止“可见页→页码”回写覆盖目标页：滚动落定后再恢复由可见页驱动。
            scope.launch { listState.scrollToItem(target - 1) }
        }
    }

    /** 切换阅读方向：同一章节内保持当前页，不重新请求图片。 */
    fun switchMode(nextHorizontal: Boolean) {
        if (nextHorizontal == horizontal) return
        val page = currentPage.coerceIn(1, totalPages)
        horizontal = nextHorizontal
        scope.launch { Jm3Graph.settings.setReaderHorizontal(nextHorizontal) }
        if (nextHorizontal) {
            scope.launch { pagerState.scrollToPage((page - 1).coerceIn(0, lastIndex)) }
        } else {
            scope.launch { listState.scrollToItem((page - 1).coerceIn(0, lastIndex)) }
        }
    }

    fun requireLogin(): Boolean {
        if (user != null) return true
        onOpenLogin()
        return false
    }

    fun toggleFavorite() {
        if (!requireLogin() || engagementBusy) return
        engagementBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleFavorite(photo.albumId) }
            }
            engagementBusy = false
            result
                .onSuccess {
                    favorited = !favorited
                    snackbarHostState.showSnackbar(if (favorited) "已收藏" else "已取消收藏")
                }
                .onFailure { snackbarHostState.showSnackbar("收藏失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun toggleLike() {
        if (!requireLogin() || engagementBusy) return
        if (liked) {
            scope.launch { snackbarHostState.showSnackbar("已经喜欢过了") }
            return
        }
        engagementBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleLike(photo.albumId) }
            }
            engagementBusy = false
            result
                .onSuccess {
                    liked = true
                    snackbarHostState.showSnackbar("已喜欢")
                }
                .onFailure { snackbarHostState.showSnackbar("操作失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun loadComments() {
        commentsLoading = true
        commentsFailed = false
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.comments(photo.albumId) }.getOrNull()
            }
            commentsLoading = false
            if (page == null) commentsFailed = true else comments = page
        }
    }

    fun openComments() {
        showCommentSheet = true
        if (comments == null) loadComments()
    }

    fun sendComment() {
        if (!requireLogin() || commentBusy || commentText.isBlank()) return
        commentBusy = true
        val text = commentText.trim()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.postComment(photo.albumId, text) }
            }
            commentBusy = false
            result
                .onSuccess {
                    commentText = ""
                    snackbarHostState.showSnackbar("评论已发表")
                    loadComments()
                }
                .onFailure { snackbarHostState.showSnackbar("发表失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.replyComment(photo.albumId, text, target.id) }
            }
            replyBusy = false
            result
                .onSuccess {
                    replyTarget = null
                    snackbarHostState.showSnackbar("回复已发送")
                    loadComments()
                }
                .onFailure { snackbarHostState.showSnackbar("回复失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    /** 关闭末页追更引导：勾选「3天不要显示」时写入静默截止时间。 */
    fun closeTrackPrompt() {
        showTrackPrompt = false
        if (trackMuteChecked) {
            scope.launch {
                Jm3Graph.settings.setTrackPromptMuteUntil(System.currentTimeMillis() + TRACK_MUTE_DAYS_MS)
            }
        }
    }

    fun followSeries() {
        if (!requireLogin() || trackBusy) return
        trackBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.setTracking(photo.albumId) }
            }
            trackBusy = false
            result
                .onSuccess { snackbarHostState.showSnackbar("已追踪连载") }
                .onFailure { snackbarHostState.showSnackbar("追踪失败：" + (it.message ?: "请稍后重试")) }
            closeTrackPrompt()
        }
    }

    // 音量键翻页：150ms 节流，与原生桥一致
    DisposableEffect(Unit) {
        ReaderVolumeKeys.handler = { keyCode ->
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                else -> 0
            }
            if (delta == 0) {
                false
            } else {
                val now = System.currentTimeMillis()
                if (now - lastVolumeAt >= VOLUME_THROTTLE_MS) {
                    lastVolumeAt = now
                    goToPage(currentPage + delta)
                }
                true
            }
        }
        onDispose { ReaderVolumeKeys.handler = null }
    }

    // 最后一话最后一页弹追更引导；勾选静默后三天内不再出现
    LaunchedEffect(currentPage, photo.images.size) {
        if (trackPromptShown || photo.images.isEmpty()) return@LaunchedEffect
        if (chapters.isEmpty() || chapterIndex != chapters.lastIndex) return@LaunchedEffect
        if (currentPage != photo.images.size) return@LaunchedEffect
        if (System.currentTimeMillis() < Jm3Graph.settings.trackPromptMuteUntil()) return@LaunchedEffect
        trackPromptShown = true
        showTrackPrompt = true
    }

    Scaffold(
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    title = photo.title,
                    subtitle = "第 $currentPage / ${photo.images.size} 页",
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
                        IconButton(onClick = { prevChapter?.let { onOpenChapter(it.id) } }, enabled = prevChapter != null) {
                            Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一话")
                        }
                        TextButton(
                            text = "$currentPage/${photo.images.size}",
                            onClick = {
                                pickerValue = currentPage
                                showPageSheet = true
                            },
                            minWidth = 0.dp,
                            minHeight = 36.dp,
                        )
                        IconButton(onClick = { nextChapter?.let { onOpenChapter(it.id) } }, enabled = nextChapter != null) {
                            Icon(imageVector = MiuixIcons.ChevronForward, contentDescription = "下一话")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        floatingToolbar = {
            if (chromeVisible) {
                ReaderToolbar(
                    horizontal = horizontal,
                    favorited = favorited,
                    liked = liked,
                    backdrop = toolbarBackdrop,
                    blurRadius = toolbarBlurRadius,
                    onToggleMode = { switchMode(!horizontal) },
                    onChapters = { showChapterSheet = true },
                    onComments = ::openComments,
                    onFavorite = ::toggleFavorite,
                    onLike = ::toggleLike,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(toolbarBackdrop)) {
            if (horizontal) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pagerGestureOverride(pagerState),
                    // 手势由 pagerGestureOverride 驱动，避免与原生拖拽竞争
                    userScrollEnabled = false,
                    pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                ) { index ->
                    ReaderPage(
                        image = photo.images[index],
                        imageLoader = imageLoader,
                        pageSize = screenSize,
                        modifier = Modifier.fillMaxSize(),
                        onTap = { chromeVisible = !chromeVisible },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    items(photo.images, key = { it.index }) { image ->
                        ReaderPage(
                            image = image,
                            imageLoader = imageLoader,
                            pageSize = pageSize,
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }
                    // 底部悬浮工具栏会遮住最后一页末尾，留出可滚动空间
                    item(key = "toolbar-space") { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
            // 长图阅读时长列表无位置反馈，补 Miuix 滚动条（拖动可直接跳页）
            if (!horizontal) {
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = innerPadding,
                )
            }
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
                    goToPage(pickerValue)
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

        OverlayBottomSheet(
            show = showCommentSheet,
            title = "评论",
            onDismissRequest = { showCommentSheet = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val page = comments
                when {
                    commentsLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    commentsFailed -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(text = "加载失败，点击重试", onClick = ::loadComments, minWidth = 0.dp)
                    }

                    page == null || page.items.isEmpty() -> Text(
                        text = "还没有评论，来抢沙发吧",
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(page.items, key = { it.id }) { item ->
                            CommentRow(
                                item = item,
                                onReply = {
                                    if (user == null) onOpenLogin() else replyTarget = item
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                CommentComposer(
                    loggedIn = user != null,
                    text = commentText,
                    busy = commentBusy,
                    onTextChange = { if (it.length <= COMMENT_MAX_LENGTH) commentText = it },
                    onLogin = onOpenLogin,
                    onSend = ::sendComment,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 回复面板：与评论抽屉平级，避免底部抽屉叠加导致高度抖动
        CommentReplySheet(
            show = replyTarget != null,
            targetAuthor = replyTarget?.author,
            busy = replyBusy,
            onDismiss = { replyTarget = null },
            onSend = ::sendReply,
        )

        OverlayDialog(
            show = showTrackPrompt,
            title = "已经最后一话了！",
            onDismissRequest = { closeTrackPrompt() },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = "随时追踪最新连载漫画更新，不错过每一集精彩内容",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { trackMuteChecked = !trackMuteChecked }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        state = ToggleableState(trackMuteChecked),
                        onClick = { trackMuteChecked = !trackMuteChecked },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "3天不要显示", fontSize = MiuixTheme.textStyles.body2.fontSize)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(text = "追踪连载", onClick = ::followSeries, enabled = !trackBusy)
                    TextButton(text = "关闭", onClick = { closeTrackPrompt() })
                }
            }
        }
    }

    // 顶栏页码与预取共用一次订阅：同一次 collect 内完成，collectLatest 让快速滚动只处理最新页
    LaunchedEffect(listState, photo.images, imageLoader, pageSize, horizontal) {
        if (horizontal) return@LaunchedEffect
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

    // 左右模式：页码跟随 Pager，并预取后 3 页（整屏尺寸，与展示一致）
    LaunchedEffect(pagerState, photo.images, imageLoader, screenSize, horizontal) {
        if (!horizontal) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }
            .collectLatest { page ->
                val total = photo.images.size
                currentPage = if (total == 0) 0 else (page + 1).coerceAtMost(total)
                for (offset in 1..3) {
                    val index = page + offset
                    if (index < photo.images.size && prefetched.add(index)) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(photo.images[index])
                                .size(screenSize)
                                .build(),
                        )
                    }
                }
            }
    }
}

/** 阅读器底部悬浮工具栏：模式切换/目录/评论/收藏/喜欢（原版 ReadNav 底部动作行）。 */
@Composable
private fun ReaderToolbar(
    horizontal: Boolean,
    favorited: Boolean,
    liked: Boolean,
    backdrop: LayerBackdrop,
    blurRadius: Float,
    onToggleMode: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
) {
    // 外层承载模糊形状，内层 FloatingToolbar 只留半透明底色，避免不透明背景盖住模糊
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50.dp) },
                effects = { blur(blurRadius) },
            ),
    ) {
        FloatingToolbar(
            color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
            outSidePadding = PaddingValues(0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ReaderActionCell(
                    icon = if (horizontal) MiuixIcons.VerticalSplit else MiuixIcons.HorizontalSplit,
                    label = if (horizontal) "直向阅读" else "左右阅读",
                    onClick = onToggleMode,
                )
                ReaderActionCell(icon = MiuixIcons.ListView, label = "章节", onClick = onChapters)
                ReaderActionCell(icon = MiuixIcons.Messages, label = "评论", onClick = onComments)
                ReaderActionCell(
                    icon = if (favorited) MiuixIcons.FolderFill else MiuixIcons.Folder,
                    label = "收藏",
                    active = favorited,
                    onClick = onFavorite,
                )
                ReaderActionCell(
                    icon = if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    label = "喜欢",
                    active = liked,
                    onClick = onLike,
                )
            }
        }
    }
}

/** 工具栏单项：图标 + 文案，激活态用主题主色回显。 */
@Composable
private fun ReaderActionCell(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .width(66.dp)
            .clickable(onClick = onClick)
            .pressScale(hapticFeedback = true)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderPage(
    image: PageImage,
    imageLoader: ImageLoader,
    pageSize: Size,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    var retryKey by remember(image) { mutableIntStateOf(0) }
    // 与预取请求同一尺寸，保证内存缓存可复用；重试时重建请求绕过失败缓存
    val request = remember(image, pageSize, context, retryKey) {
        ImageRequest.Builder(context).data(image).size(pageSize).build()
    }
    var failed by remember(image, retryKey) { mutableStateOf(false) }
    Box(modifier = if (onTap != null) modifier.clickable(onClick = onTap) else modifier) {
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

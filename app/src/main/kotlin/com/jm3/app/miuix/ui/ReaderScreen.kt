package com.jm3.app.miuix.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.app.miuix.ui.glass.GlassSmallTopAppBar
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.Jm3Gateway
import com.jm3.core.runCatchingSuspending
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryEntry
import com.jm3.core.history.HistoryRepository
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderPagePlan
import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.advancePrefetch
import com.jm3.core.reader.jumpTo
import com.jm3.core.reader.readerProgressOnExit
import com.jm3.core.reader.resolveRestoredPage
import com.jm3.core.reader.settleJump
import com.jm3.core.reader.stepPages
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.app.miuix.ui.glass.GlassRole
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.glassSurface
import com.jm3.app.miuix.ui.glass.rememberBlurBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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

/** 阅读进度落盘前的静置时长：翻页停下来才写，避免连滚时逐页写设置存储。 */
private const val PROGRESS_SAVE_DEBOUNCE_MS = 400L
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
    /** [sessionExpired] 为真时整屏换成登录引导：重试必然再吃一次 401。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ReaderUiState
}

/**
 * 首载结局 → 阅读器失败态；成功返回 null，由调用方继续走内容分支。
 *
 * 失效与失败必须分开：前者只能去登录，后者才值得重试。
 */
internal fun readerLoadFailure(outcome: SessionOutcome<*>): ReaderUiState.Failed? = when (outcome) {
    is SessionOutcome.Ok -> null
    SessionOutcome.Expired -> ReaderUiState.Failed(SessionOutcome.Expired.message, sessionExpired = true)
    is SessionOutcome.Failed -> ReaderUiState.Failed(outcome.messageOr("章节加载失败"))
}

@Composable
fun ReaderScreen(
    deps: ScreenDeps,
    photoId: String,
    authCall: AuthCall,
    gateway: Jm3Gateway,
    downloadManager: DownloadManager,
    settings: ReaderSettings,
    history: HistoryRepository,
    downloadFiles: DownloadStore,
    account: AccountManager,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }
    val scope = rememberCoroutineScope()

    // 优先读已下载落盘数据，缺失时回退网关；供首载与失败重试复用
    fun load() {
        state = ReaderUiState.Loading
        scope.launch {
            // 落盘页是本地文件，不经网关
            val saved = runCatchingSuspending { downloadManager.find(photoId) }.getOrNull()
            val local = saved
                ?.takeIf { it.record.status == DownloadStatus.DONE }
                ?.pagesJson
                ?.let { decodePhotoPages(it) }
                ?.takeIf { it.images.isNotEmpty() }
            val outcome: SessionOutcome<PhotoPages> = if (local != null) {
                SessionOutcome.Ok(local)
            } else {
                authCall.call { gateway.photo(photoId) }
            }
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val photo = outcome.value
                    // 离线页的 albumId 未落盘，用下载记录的 albumId 补章节列表与收藏/点赞状态
                    val albumId = saved?.record?.albumId ?: photo.albumId
                    // 详情仅用于章节列表与收藏/点赞状态；失败不阻塞阅读
                    val detail = (authCall.call { gateway.album(albumId) } as? SessionOutcome.Ok)?.value
                    state = ReaderUiState.Loaded(
                        photo = photo,
                        chapters = detail?.chapters.orEmpty(),
                        chapterIndex = detail?.chapters?.indexOfFirst { it.id == photo.photoId } ?: -1,
                        favorited = detail?.favorited == true,
                        liked = detail?.liked == true,
                    )
                }

                else -> readerLoadFailure(outcome)?.let { state = it }
            }
        }
    }

    LaunchedEffect(photoId) {
        load()
    }

    when (val s = state) {
        is ReaderUiState.Loading -> GlassScaffold(
            topBar = {
                GlassTopAppBar(
                    title = "阅读",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
            },
        ) { innerPadding ->
            LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        is ReaderUiState.Failed -> GlassScaffold(
            topBar = {
                GlassTopAppBar(
                    title = "阅读",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
            },
        ) { innerPadding ->
            // 失效不给重试：重试必然再吃一次 401，只能去登录
            if (s.sessionExpired) {
                LoginExpiredScreen(onOpenLogin = onOpenLogin, modifier = Modifier.padding(innerPadding))
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = ::load,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        is ReaderUiState.Loaded -> {
            LaunchedEffect(s.photo.photoId) {
                val coverUrl = (authCall.call { gateway.coverUrl(s.photo.albumId) } as? SessionOutcome.Ok)?.value
                runCatchingSuspending {
                    history.upsert(
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
                deps = deps,
                photo = s.photo,
                chapters = s.chapters,
                chapterIndex = s.chapterIndex,
                favoritedInit = s.favorited,
                likedInit = s.liked,
                authCall = authCall,
                gateway = gateway,
                settings = settings,
                downloadFiles = downloadFiles,
                account = account,
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
    deps: ScreenDeps,
    photo: PhotoPages,
    chapters: List<Chapter>,
    chapterIndex: Int,
    favoritedInit: Boolean,
    likedInit: Boolean,
    authCall: AuthCall,
    gateway: Jm3Gateway,
    settings: ReaderSettings,
    downloadFiles: DownloadStore,
    account: AccountManager,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val imageLoader = rememberReaderImageLoader(gateway, downloadFiles)
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState { photo.images.size }
    val context = LocalPlatformContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val scope = rememberCoroutineScope()
    val user by account.user.collectAsState()
    // 页宽固定为容器宽；解码尺寸不再写死 3:4，而是按这一页的真实宽高比推算，
    // 否则 800x20000 的长条图会被解成 58x1440 再拉满整宽，放大十几倍必然发糊。
    val pageWidthPx = windowSize.width.coerceAtLeast(1)
    // 已解码页的真实宽高比：解码成功即回填，只驱动页框布局
    val pageRatios = remember(photo) { mutableStateMapOf<Int, Float>() }
    // 只驱动请求尺寸的比例：仅当解码结果顶满请求框时才回填，避免一次回填就把内存缓存键换掉
    val decodeRatios = remember(photo) { mutableStateMapOf<Int, Float>() }
    var chapterRatioSeed by remember(photo) { mutableStateOf<Float?>(null) }
    // 左右模式整屏展示，解码尺寸按屏幕容器
    val screenSize = remember(windowSize.width, windowSize.height) {
        Size(windowSize.width.coerceAtLeast(1), windowSize.height.coerceAtLeast(1))
    }
    // 横向翻页整屏、纵向按容器宽，两套排布的解码尺寸不同
    var horizontal by remember { mutableStateOf(false) }
    // 已发起预加载的页号；翻页向前向后都只发一次。键随章节与布局基线走：
    // 换话、页宽变了或切换纵横后，按旧尺寸解的页要重新发一次，否则新尺寸永远等不到解码
    val prefetched = remember(photo, pageWidthPx, horizontal) { mutableSetOf<Int>() }
    // 当前页是否处于放大态：放大时让出翻页手势，交给缩放层自己消费
    var pageZoomed by remember(photo) { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { pageZoomed = false }
    // 长图模式下双击打开的整屏缩放层：存放 photo.images 下标，null 表示未打开
    var zoomOverlayIndex by remember(photo) { mutableStateOf<Int?>(null) }
    // 页码的唯一真源：顶栏、跳页器、音量键与预取都从这份计划取页码，跳页冻结也在这里
    var pagePlan by remember(photo) { mutableStateOf(ReaderPagePlan.initial(photo.images.size)) }
    // 阅读进度：纵横模式落定后才恢复上次页码，恢复只做一次
    var horizontalReady by remember { mutableStateOf(false) }
    var progressRestored by remember(photo.photoId) { mutableStateOf(false) }
    // 离开阅读器时补记当前页：状态对象在离开时仍可读
    val latestPage by rememberUpdatedState(pagePlan.currentPage)
    var showPageSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var pickerValue by remember { mutableIntStateOf(1) }
    // 原版点击正文切换上下栏显隐
    var chromeVisible by remember { mutableStateOf(true) }
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

    // 悬浮工具栏毛玻璃：内容层录屏作为 backdrop，三态降级（液态/模糊/实色）交给 glassSurface；
    // 采样源先垫主题底色，避免页面留白区透明参与模糊产生黑边
    val toolbarBackdrop = rememberBlurBackdrop()

    val prevChapter = chapters.getOrNull(chapterIndex - 1)?.takeIf { chapterIndex > 0 }
    val nextChapter = chapters.getOrNull(chapterIndex + 1)
        ?.takeIf { chapterIndex in 0 until chapters.lastIndex }
    val totalPages = photo.images.size.coerceAtLeast(1)
    val lastIndex = (photo.images.size - 1).coerceAtLeast(0)

    // 恢复提示与音量键提示共用同一条队列，避免两条 Snackbar 互相顶掉
    val snackbarQueue = remember { Mutex() }

    LaunchedEffect(Unit) {
        horizontal = settings.readerHorizontal()
        // 纵横模式决定跳页走 pager 还是列表，恢复进度必须等它落定
        horizontalReady = true
        if (!settings.volumeKeyHintShown()) {
            delay(1000)
            snackbarQueue.withLock {
                snackbarHostState.showSnackbar("可使用音量键＋翻页")
            }
            settings.markVolumeKeyHintShown()
        }
    }

    /** 跳页：先让计划进入“跳页中”，再驱动 pager 或列表；落地后才恢复可见页回写。 */
    fun goToPage(page: Int) {
        val next = jumpTo(pagePlan, page)
        if (next == pagePlan) return
        pagePlan = next
        val target = next.pendingTarget ?: return
        if (horizontal) {
            scope.launch {
                pagerState.springAnimateToPage(target - 1)
                pagePlan = settleJump(pagePlan, target)
            }
        } else {
            scope.launch {
                listState.scrollToItem(target - 1)
                pagePlan = settleJump(pagePlan, target)
            }
        }
    }

    // 恢复上次读到的页码：等纵横模式落定后再跳，避免恢复动作又被布局回写覆盖。
    // progressRestored 同时作为「已尝试恢复」的闸门，进度写入要等它翻转后才允许，
    // 否则首帧的「第 1 页」会先写掉还没读的旧进度。
    LaunchedEffect(horizontalReady, photo.photoId) {
        if (!horizontalReady || progressRestored) return@LaunchedEffect
        // 先把旧进度读出来再放开写入闸门：反过来写会先落盘「第 1 页」，把要恢复的进度覆盖掉
        val saved = settings.readerLastPage(photo.photoId)
        progressRestored = true
        val target = resolveRestoredPage(saved, photo.images.size)
        if (saved == null || target == null) return@LaunchedEffect
        if (target != pagePlan.currentPage) goToPage(target)
        snackbarQueue.withLock {
            snackbarHostState.showSnackbar(
                if (saved.total != photo.images.size) {
                    "章节页数已更新，已恢复到第 $target 页"
                } else {
                    "已恢复到上次读到的第 $target 页"
                },
            )
        }
    }

    // 页码一变就记进度：读到最后一页同样落盘。
    // 落盘前等一下翻页停下来，避免长图连滚时把每一页都写一遍设置存储
    LaunchedEffect(pagePlan.currentPage, photo.images.size, progressRestored) {
        if (!progressRestored || photo.images.isEmpty()) return@LaunchedEffect
        delay(PROGRESS_SAVE_DEBOUNCE_MS)
        settings.setReaderLastPage(
            photo.photoId,
            ReaderPageProgress(page = pagePlan.currentPage, total = photo.images.size),
        )
    }

    // 离开阅读器时补记当前页：此时协程作用域已取消，挂在进程作用域上才写得完。
    // 恢复流程未完成时首帧页码还是第 1 页，此时不能写盘，否则会覆盖尚未读取的旧进度。
    DisposableEffect(photo.photoId) {
        onDispose {
            val progress = readerProgressOnExit(
                restored = progressRestored,
                page = latestPage,
                total = photo.images.size,
            ) ?: return@onDispose
            deps.appScope.launch {
                settings.setReaderLastPage(photo.photoId, progress)
            }
        }
    }

    /** 切换阅读方向：同一章节内保持当前页，不重新请求图片。 */
    fun switchMode(nextHorizontal: Boolean) {
        if (nextHorizontal == horizontal) return
        val page = pagePlan.currentPage.coerceIn(1, totalPages)
        horizontal = nextHorizontal
        scope.launch { settings.setReaderHorizontal(nextHorizontal) }
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

    // 动作型调用的统一落点：失效给登录入口，失败给「动作：原因」
    suspend fun reportAction(outcome: SessionOutcome<*>, failurePrefix: String) {
        outcome.notifyAction(
            notify = { snackbarHostState.showSnackbar(it) },
            failurePrefix = failurePrefix,
            onExpired = onOpenLogin,
        )
    }

    fun toggleFavorite() {
        if (!requireLogin() || engagementBusy) return
        engagementBusy = true
        scope.launch {
            val outcome = authCall.call { gateway.toggleFavorite(photo.albumId) }
            engagementBusy = false
            if (outcome is SessionOutcome.Ok) {
                favorited = !favorited
                snackbarHostState.showSnackbar(if (favorited) "已收藏" else "已取消收藏")
            } else {
                reportAction(outcome, "收藏失败")
            }
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
            val outcome = authCall.call { gateway.toggleLike(photo.albumId) }
            engagementBusy = false
            if (outcome is SessionOutcome.Ok) {
                liked = true
                snackbarHostState.showSnackbar("已喜欢")
            } else {
                reportAction(outcome, "操作失败")
            }
        }
    }

    fun loadComments() {
        commentsLoading = true
        commentsFailed = false
        scope.launch {
            val outcome = authCall.call { gateway.comments(photo.albumId) }
            commentsLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> comments = outcome.value
                // 内联「加载失败 + 重试」对失效是误导：重试必然再吃一次 401
                SessionOutcome.Expired -> reportAction(outcome, "评论加载失败")
                is SessionOutcome.Failed -> commentsFailed = true
            }
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
            val outcome = authCall.call { gateway.postComment(photo.albumId, text) }
            commentBusy = false
            if (outcome is SessionOutcome.Ok) {
                commentText = ""
                snackbarHostState.showSnackbar("评论已发表")
                loadComments()
            } else {
                reportAction(outcome, "发表失败")
            }
        }
    }

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val outcome = authCall.call { gateway.replyComment(photo.albumId, text, target.id) }
            replyBusy = false
            if (outcome is SessionOutcome.Ok) {
                replyTarget = null
                snackbarHostState.showSnackbar("回复已发送")
                loadComments()
            } else {
                reportAction(outcome, "回复失败")
            }
        }
    }

    /** 关闭末页追更引导：勾选「3天不要显示」时写入静默截止时间。 */
    fun closeTrackPrompt() {
        showTrackPrompt = false
        if (trackMuteChecked) {
            scope.launch {
                settings.setTrackPromptMuteUntil(System.currentTimeMillis() + TRACK_MUTE_DAYS_MS)
            }
        }
    }

    fun followSeries() {
        if (!requireLogin() || trackBusy) return
        trackBusy = true
        scope.launch {
            val outcome = authCall.call { gateway.setTracking(photo.albumId) }
            trackBusy = false
            if (outcome is SessionOutcome.Ok) {
                snackbarHostState.showSnackbar("已追踪连载")
            } else {
                reportAction(outcome, "追踪失败")
            }
            closeTrackPrompt()
        }
    }

    // 音量键翻页：150ms 节流，与原生桥一致。
    // ReaderVolumeKeys.handler 是进程级单值，换章时新旧屏会短暂同时挂载，
    // 所以必须记住自己写入的实例，否则旧屏 onDispose 会把新屏刚写进去的 handler 清成 null。
    DisposableEffect(Unit) {
        val mine: (Int) -> Boolean = { keyCode ->
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                else -> 0
            }
            when {
                delta == 0 -> false
                // 长图整屏覆盖层打开时按键不属于底层的页：既不翻页也不消费，
                // 否则关掉覆盖层会发现自己已经被换到别的页了。
                zoomOverlayIndex != null -> false
                else -> {
                    val now = System.currentTimeMillis()
                    if (now - lastVolumeAt >= VOLUME_THROTTLE_MS) {
                        lastVolumeAt = now
                        goToPage(stepPages(pagePlan, delta))
                    }
                    true
                }
            }
        }
        ReaderVolumeKeys.handler = mine
        onDispose {
            // 只清自己写进去的那一个，避免踩掉后挂载的新屏
            if (ReaderVolumeKeys.handler === mine) ReaderVolumeKeys.handler = null
        }
    }

    // 最后一话最后一页弹追更引导；勾选静默后三天内不再出现
    LaunchedEffect(pagePlan.currentPage, photo.images.size) {
        if (trackPromptShown || photo.images.isEmpty()) return@LaunchedEffect
        if (chapters.isEmpty() || chapterIndex != chapters.lastIndex) return@LaunchedEffect
        if (pagePlan.currentPage != photo.images.size) return@LaunchedEffect
        if (System.currentTimeMillis() < settings.trackPromptMuteUntil()) return@LaunchedEffect
        trackPromptShown = true
        showTrackPrompt = true
    }

    GlassScaffold(
        topBar = {
            if (chromeVisible) {
                // 阅读页顶栏不折叠：大标题在长书名下会吃掉三行，与看图抢空间
                GlassSmallTopAppBar(
                    title = photo.title,
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    actions = {
                        IconButton(onClick = { prevChapter?.let { onOpenChapter(it.id) } }, enabled = prevChapter != null) {
                            Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一话")
                        }
                        TextButton(
                            text = "${pagePlan.currentPage}/${photo.images.size}",
                            onClick = {
                                pickerValue = pagePlan.currentPage
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
        snackbarHost = { GlassSnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .then(
                    if (toolbarBackdrop != null) {
                        Modifier.layerBackdrop(toolbarBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (horizontal) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        // 放大时整条手势链都交给缩放层，未放大才允许翻页
                        .pagerGestureOverride(pagerState, enabled = !pageZoomed),
                    // 手势由 pagerGestureOverride 驱动，避免与原生拖拽竞争
                    userScrollEnabled = false,
                    pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                ) { index ->
                    ReaderPage(
                        image = photo.images[index],
                        imageLoader = imageLoader,
                        decodeSize = screenSize,
                        modifier = Modifier.fillMaxSize(),
                        zoomEnabled = true,
                        isCurrentPage = index == pagerState.currentPage,
                        onTap = { chromeVisible = !chromeVisible },
                        onZoomChanged = { zoomed ->
                            // 只有当前页的缩放状态才约束翻页，预加载的邻页不参与
                            if (index == pagerState.currentPage) pageZoomed = zoomed
                        },
                        // 放大后内容已到边界仍继续横拖时接翻页：此时缩放层不再消费手势
                        onHorizontalEdgeDrag = if (pageZoomed) {
                            { dragX, stillPinned ->
                                val turn = decideEdgePageTurn(
                                    dragX = dragX,
                                    // 左右模式整屏一页，页宽就是窗口宽（Size 的 width 是 Dimension，不宜参与算术）
                                    pageWidth = pageWidthPx.toFloat(),
                                    currentPage = pagerState.currentPage,
                                    pageCount = photo.images.size,
                                )
                                if (turn != EdgePageTurn.None) {
                                    val target = if (turn == EdgePageTurn.Next) {
                                        pagerState.currentPage + 1
                                    } else {
                                        pagerState.currentPage - 1
                                    }
                                    // 惯性带不走内容才算真的贴边，否则这次手势只是在平移
                                    scope.launch {
                                        if (stillPinned()) pagerState.springAnimateToPage(target)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            } else {
                // 长列表与它的滚动条必须同层：TrackedLazyColumn 内部就是 Box { LazyColumn + ListScrollBar }，
                // 轨道与列表共用同一份 contentPadding；横向模式用的是 Pager，不经过这里
                TrackedLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    items(photo.images, key = { it.index }) { image ->
                        // 布局比例优先本页已知比例、其次章节种子；请求尺寸只认解码比例，两者分开演进
                        val frame = readerPageFrame(
                            pageWidthPx = pageWidthPx,
                            knownRatio = pageRatios[image.index],
                            chapterSeed = chapterRatioSeed,
                            decodeRatio = decodeRatios[image.index],
                        )
                        ReaderPage(
                            image = image,
                            imageLoader = imageLoader,
                            decodeSize = frame.decodeSize.toCoilSize(),
                            dynamicAspectRatio = true,
                            aspectRatio = frame.layoutRatio,
                            modifier = Modifier.fillMaxWidth(),
                            // 解码出真实比例后回填：布局立刻重排，请求尺寸只在受框限制时重算
                            // 判定收在 readerPageBackfill：布局比例、章节种子、要不要重算解码尺寸
                            onIntrinsicRatio = { detected, decodedWidth, decodedHeight ->
                                readerPageBackfill(frame, detected, decodedWidth, decodedHeight)?.let { backfill ->
                                    if (pageRatios[image.index] != backfill.layoutRatio) {
                                        pageRatios[image.index] = backfill.layoutRatio
                                    }
                                    backfill.chapterSeed?.let { chapterRatioSeed = it }
                                    backfill.decodeRatio?.let { decodeRatio ->
                                        if (decodeRatios[image.index] != decodeRatio) {
                                            decodeRatios[image.index] = decodeRatio
                                        }
                                    }
                                }
                            },
                            // 长图模式按图片真实比例自适应展开无缝拼接，双击打开整屏缩放层
                            onDoubleTap = { zoomOverlayIndex = photo.images.indexOf(image) },
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }
                    // 底部悬浮工具栏会遮住最后一页末尾，留出可滚动空间
                    item(key = "toolbar-space") { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }

        // 底栏原由 Scaffold 的 floatingToolbar 槽承载；GlassScaffold 无该槽，
        // 改为内容层底部居中的覆盖层，位置与原先一致
        if (chromeVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding() + 4.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ReaderToolbar(
                    horizontal = horizontal,
                    favorited = favorited,
                    liked = liked,
                    backdrop = toolbarBackdrop,
                    onToggleMode = { switchMode(!horizontal) },
                    onChapters = { showChapterSheet = true },
                    onComments = ::openComments,
                    onFavorite = ::toggleFavorite,
                    onLike = ::toggleLike,
                )
            }
        }

        // 长图模式的整屏缩放层：双击页面打开，单击或返回键退出。
        // 放在工具栏之后，保证盖住内容与工具栏而不是被它们压住
        val overlayImage = zoomOverlayIndex?.let { photo.images.getOrNull(it) }
        if (overlayImage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                ReaderPage(
                    image = overlayImage,
                    imageLoader = imageLoader,
                    decodeSize = screenSize,
                    modifier = Modifier.fillMaxSize(),
                    zoomEnabled = true,
                    onTap = { zoomOverlayIndex = null },
                )
            }
            BackHandler { zoomOverlayIndex = null }
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
                    style = MiuixTheme.textStyles.body2,
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
                    ) { InfiniteProgressIndicator() }

                    commentsFailed -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(text = "加载失败，点击重试", onClick = ::loadComments, minWidth = 0.dp)
                    }

                    page == null || page.items.isEmpty() -> Text(
                        text = "还没有评论，来抢沙发吧",
                        style = MiuixTheme.textStyles.body2,
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
                    style = MiuixTheme.textStyles.body2,
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
                    Text(text = "3天不要显示", style = MiuixTheme.textStyles.body2)
                }
                Spacer(modifier = Modifier.height(10.dp))
                // HyperOS 弹窗惯例：按钮纵向堆叠、各占满宽，避免横排等分与系统弹窗观感不一致
                TextButton(
                    text = "追踪连载",
                    onClick = ::followSeries,
                    enabled = !trackBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = "关闭",
                    onClick = { closeTrackPrompt() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // 页码与预取共用一次订阅：纵向跟 LazyColumn、横向跟 Pager，只差请求尺寸；
    // 该发哪几页由 advancePrefetch 统一判定（页码收敛与去重都在那里），collectLatest 让快速滚动只处理最新页
    LaunchedEffect(listState, pagerState, photo.images, imageLoader, pageWidthPx, screenSize, horizontal) {
        snapshotFlow { if (horizontal) pagerState.currentPage else listState.firstVisibleItemIndex }
            .collectLatest { first ->
                val next = advancePrefetch(pagePlan, first, prefetched)
                pagePlan = next.plan
                for (index in next.pages) {
                    prefetched += index
                    val page = photo.images[index]
                    val size = if (horizontal) {
                        // 左右模式整屏展示，请求尺寸与展示一致
                        screenSize
                    } else {
                        // 纵向按容器宽推算页框；尺寸与展示走同一处换算，否则内存缓存键不同，预取等于白解
                        readerPageFrame(
                            pageWidthPx = pageWidthPx,
                            knownRatio = pageRatios[page.index],
                            chapterSeed = chapterRatioSeed,
                            decodeRatio = decodeRatios[page.index],
                        ).decodeSize.toCoilSize()
                    }
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(page)
                            .size(size)
                            .configureReaderDefaults()
                            .build(),
                    )
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
    backdrop: LayerBackdrop?,
    onToggleMode: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
) {
    // 三态降级交给 glassSurface 的 Toolbar 角色：液态档是 22dp 透镜 + 0.78 底衬，
    // 无着色器退 22dp 模糊，再无采样源退实色；形状与 FloatingToolbar 的胶囊保持一致
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(
                glassSurface(
                    backdrop = backdrop,
                    role = GlassRole.Toolbar,
                    tint = MiuixTheme.colorScheme.surfaceContainer,
                    shape = remember { RoundedCornerShape(50.dp) },
                ),
            ),
    ) {
        // 底衬与高光由 glassSurface 画在内容之下，这里不能再盖一层半透明底色
        FloatingToolbar(
            color = Color.Transparent,
            outSidePadding = PaddingValues(0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionCell(
                    icon = if (horizontal) MiuixIcons.VerticalSplit else MiuixIcons.HorizontalSplit,
                    label = if (horizontal) "直向阅读" else "左右阅读",
                    onClick = onToggleMode,
                    modifier = Modifier.width(66.dp),
                )
                ActionCell(icon = MiuixIcons.ListView, label = "章节", onClick = onChapters, modifier = Modifier.width(66.dp))
                ActionCell(icon = MiuixIcons.Messages, label = "评论", onClick = onComments, modifier = Modifier.width(66.dp))
                ActionCell(
                    icon = if (favorited) MiuixIcons.FolderFill else MiuixIcons.Folder,
                    label = "收藏",
                    active = favorited,
                    onClick = onFavorite,
                    modifier = Modifier.width(66.dp),
                )
                ActionCell(
                    icon = if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    label = "喜欢",
                    active = liked,
                    onClick = onLike,
                    modifier = Modifier.width(66.dp),
                )
            }
        }
    }
}


/** 纯函数算出的解码像素转成 Coil 的 Size；预取与展示共用这一处，内存缓存键才一致。 */
private fun IntSize.toCoilSize(): Size = Size(width, height)

@Composable
private fun ReaderPage(
    image: PageImage,
    imageLoader: ImageLoader,
    decodeSize: Size,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = false,
    isCurrentPage: Boolean = true,
    dynamicAspectRatio: Boolean = false,
    aspectRatio: Float = DEFAULT_PAGE_ASPECT_RATIO,
    onIntrinsicRatio: ((detected: Float, decodedWidthPx: Float, decodedHeightPx: Float) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onHorizontalEdgeDrag: ((dragX: Float, stillPinned: suspend () -> Boolean) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    var retryKey by remember(image) { mutableIntStateOf(0) }
    var failed by remember(image, retryKey) { mutableStateOf(false) }
    // 与预取请求同一尺寸，保证内存缓存可复用；重试时重建请求绕过失败缓存。
    // 缩放路径没有 onSuccess/onError 形参，失败态改由请求自带的 listener 上报，
    // 普通路径仍用 AsyncImage 的回调，两条路径写同一个 failed。
    val request = remember(image, decodeSize, context, retryKey, zoomEnabled) {
        ImageRequest.Builder(context)
            .data(image)
            .configureReaderDefaults()
            .apply {
                if (zoomEnabled) {
                    // 解码尺寸交给 telephoto 按视口与缩放级别决定，写死尺寸会让放大后发糊
                    listener(
                        onSuccess = { _, _ -> failed = false },
                        onError = { _, _ -> failed = true },
                    )
                } else {
                    // 与预取请求同一尺寸，保证内存缓存可复用
                    size(decodeSize)
                }
            }
            .build()
    }
    if (zoomEnabled) {
        val zoomableState = rememberZoomableImageState()
        // derivedStateOf 让缩放比例变化只触发读取方重组，不必每帧重算整页
        val zoomed by remember(zoomableState) {
            derivedStateOf { isPageZoomed(zoomableState.zoomableState.zoomFraction) }
        }
        LaunchedEffect(zoomed) { onZoomChanged?.invoke(zoomed) }
        // 翻走即复位：回到本页时不会残留放大态与翻页手势叠加
        LaunchedEffect(isCurrentPage) {
            if (!isCurrentPage) zoomableState.zoomableState.resetZoom()
        }
        val tapHandler: ((Offset) -> Unit)? = if (onTap != null) {
            { onTap() }
        } else {
            null
        }
        // telephoto 越过触摸阈值后会无条件消费全部位移事件，内容到边界之后也不放手，
        // 父节点在 Main pass 只能看到「已消费」。改在 Initial pass 抢先读原始位移，再用内容
        // 偏移判断内容是否还在跟随手势：内容停下后累计的位移才算边界空拖，参与翻页判定。
        val edgeDrag = rememberUpdatedState(onHorizontalEdgeDrag)
        Box(
            modifier = if (onHorizontalEdgeDrag != null) {
                modifier.pointerInput(zoomableState) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val zoomable = zoomableState.zoomableState
                        val edgeTracker = EdgeDragTracker()
                            .apply { start(zoomable.contentTransformation.offset.x) }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // 多指是捏合缩放，不参与翻页判定
                            if (event.changes.size > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            edgeTracker.onDrag(
                                deltaX = change.positionChange().x,
                                offsetX = zoomable.contentTransformation.offset.x,
                            )
                            if (!change.pressed) {
                                val pinnedDragX = edgeTracker.pinnedDragX()
                                if (pinnedDragX != 0f) {
                                    // 快甩时 telephoto 要等松手后才用惯性把内容带走，拖动期间内容
                                    // 几乎不动，会把整段位移误算成空拖。这里把复核推迟到惯性起步之后。
                                    val offsetAtRelease = zoomable.contentTransformation.offset.x
                                    edgeDrag.value?.invoke(pinnedDragX) {
                                        kotlinx.coroutines.delay(EDGE_CONFIRM_DELAY_MS)
                                        kotlin.math.abs(
                                            zoomable.contentTransformation.offset.x - offsetAtRelease,
                                        ) < EDGE_CONFIRM_MOVE_PX
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            } else {
                modifier
            },
        ) {
            if (failed) ReaderLoadFailedHint { failed = false; retryKey++ }
            ZoomableAsyncImage(
                model = request,
                contentDescription = null,
                imageLoader = imageLoader,
                state = zoomableState,
                contentScale = ContentScale.Fit,
                onClick = tapHandler,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    // 需要区分双击时用 detectTapGestures 自己起手势识别，单击回调顺延到双击超时之后
    val tapModifier = when {
        onDoubleTap != null -> Modifier.pointerInput(onTap, onDoubleTap) {
            detectTapGestures(
                onTap = { onTap?.invoke() },
                onDoubleTap = { onDoubleTap() },
            )
        }
        onTap != null -> Modifier.clickable(onClick = onTap)
        else -> Modifier
    }
    val contentModifier = if (dynamicAspectRatio) {
        modifier.aspectRatio(aspectRatio)
    } else {
        modifier
    }
    Box(modifier = contentModifier.then(tapModifier)) {
        if (failed) ReaderLoadFailedHint { failed = false; retryKey++ }
        AsyncImage(
            model = request,
            contentDescription = null,
            imageLoader = imageLoader,
            // 页框已按真实比例布局，Fit 不会再拉伸；缩放分支同样用 Fit，两条路径保持一致
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                failed = false
                if (dynamicAspectRatio) {
                    val intrinsic = state.painter.intrinsicSize
                    // 判定收敛到 pageAspectRatio 纯函数：非有限值、非正数一律不采信。
                    // 解码出的像素宽高一起交出去，调用方才能判断画面是否被请求框限制住
                    pageAspectRatio(intrinsic.width, intrinsic.height)?.let { detected ->
                        onIntrinsicRatio?.invoke(detected, intrinsic.width, intrinsic.height)
                    }
                }
            },
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 单页加载失败时的居中提示与重试入口，缩放与普通两条渲染路径共用。 */
@Composable
private fun BoxScope.ReaderLoadFailedHint(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "图片加载失败")
        TextButton(
            text = "重试",
            onClick = onRetry,
            minWidth = 0.dp,
            minHeight = 32.dp,
        )
    }
}

package com.jm3.app.miuix.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.WindowManager
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.app.miuix.reader.ReaderSession
import com.jm3.app.miuix.ui.glass.GlassSmallTopAppBar
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.runCatchingSuspending
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.reader.readerProgressOnExit
import com.jm3.core.reader.shouldClearReaderProgress
import com.jm3.core.reader.stepPages
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import com.jm3.core.session.SessionOutcome
import com.jm3.app.miuix.ui.glass.GlassRole
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.glassSurface
import com.jm3.app.miuix.ui.glass.rememberBlurBackdrop
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
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

/** 从 Compose 的 Context 链上找宿主 Activity，窗口亮度与常亮标记都要打在它上面。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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

internal fun resolveReaderLoadedState(
    photo: PhotoPages,
    savedRecord: DownloadRecord?,
    detail: AlbumDetail?,
): ReaderUiState.Loaded {
    val resolvedAlbumId = photo.albumId.ifBlank { savedRecord?.albumId.orEmpty() }
    val resolvedTitle = photo.title.ifBlank {
        savedRecord?.title?.takeIf { it.isNotBlank() } ?: detail?.title.orEmpty()
    }
    val resolvedPhoto = if (resolvedAlbumId == photo.albumId && resolvedTitle == photo.title) {
        photo
    } else {
        photo.copy(albumId = resolvedAlbumId, title = resolvedTitle)
    }
    val sortedChapters = detail?.chapters?.sortedBy { it.sortOrder }.orEmpty()
    return ReaderUiState.Loaded(
        photo = resolvedPhoto,
        chapters = sortedChapters,
        chapterIndex = sortedChapters.indexOfFirst { it.id == resolvedPhoto.photoId },
        favorited = detail?.favorited == true,
        liked = detail?.liked == true,
    )
}

/**
 * 首载结局 → 阅读器失败态；成功返回 null，由调用方继续走内容分支。
 *
 * 失效与失败必须分开：前者只能去登录，后者才值得重试。
 */
internal fun readerLoadFailure(outcome: SessionOutcome<*>): ReaderUiState.Failed? = when (outcome) {
    is SessionOutcome.Ok -> null
    SessionOutcome.Expired -> ReaderUiState.Failed(SessionOutcome.Expired.message, sessionExpired = true)
    is SessionOutcome.Failed -> ReaderUiState.Failed(failureCopy(outcome, "章节加载失败"))
}

@Composable
internal fun ReaderScreen(
    deps: ScreenDeps,
    photoId: String,
    startPage: Int? = null,
    settings: ReaderSettings,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }
    val scope = rememberCoroutineScope()
    // 首载与重试共享一次去重：新一次加载先取消上一次，结局写回前校验代际，
    // 避免连点重试并行发出多个 gateway.photo，旧响应覆盖新状态。
    val loadGate = remember(photoId) { ReaderLoadGate() }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    // 优先读已下载落盘数据，缺失时回退网关；首载与失败重试复用同一段请求
    suspend fun loadContent() {
        val token = loadGate.start()
        // 落盘页是本地文件，不经网关
        val saved = runCatchingSuspending { deps.downloadManager.find(photoId) }.getOrNull()
        val local = saved
            ?.takeIf { it.record.status == DownloadStatus.DONE }
            ?.pagesJson
            ?.let { decodePhotoPages(it) }
            ?.takeIf { it.images.isNotEmpty() }
        val outcome: SessionOutcome<PhotoPages> = if (local != null) {
            SessionOutcome.Ok(local)
        } else {
            deps.authCall.call { deps.gateway.photo(photoId) }
        }
        when (outcome) {
            is SessionOutcome.Ok -> {
                val photo = outcome.value
                // 离线页的 albumId 未落盘，用下载记录的 albumId 补章节列表与收藏/点赞状态
                val albumId = saved?.record?.albumId ?: photo.albumId
                // 详情仅用于章节列表与收藏/点赞状态；失败不阻塞阅读
                val detail = (deps.authCall.call { deps.gateway.album(albumId) } as? SessionOutcome.Ok)?.value
                // 期间又发起了新的加载：本次结局作废，不覆盖新状态
                if (!loadGate.isLatest(token)) return
                state = resolveReaderLoadedState(photo = photo, savedRecord = saved?.record, detail = detail)
            }

            else -> {
                if (!loadGate.isLatest(token)) return
                readerLoadFailure(outcome)?.let { state = it }
            }
        }
    }

    /** 失败重试入口：先取消在途加载，避免连点并行。 */
    fun load() {
        loadJob?.cancel()
        state = ReaderUiState.Loading
        loadJob = scope.launch { loadContent() }
    }

    LaunchedEffect(photoId) {
        // 同槽位切换 photoId 时先取消前一个章节的在途加载，避免旧请求继续占位或竞争状态
        loadJob?.cancel()
        // 首载也要登记 Job：失败重试入口靠它取消在途加载，注释与实现才一致
        loadJob = scope.launch { loadContent() }
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
                LoginExpiredScreen(onOpenLogin = nav::login, modifier = Modifier.padding(innerPadding))
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
                val coverUrl = (deps.authCall.call { deps.images.coverUrl(s.photo.albumId) } as? SessionOutcome.Ok)?.value
                runCatchingSuspending {
                    deps.history.upsert(
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
                startPage = startPage,
                chapters = s.chapters,
                chapterIndex = s.chapterIndex,
                favoritedInit = s.favorited,
                likedInit = s.liked,
                settings = settings,
                onBack = onBack,
                onOpenChapter = nav::nextChapter,
                onOpenLogin = nav::login,
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
    startPage: Int?,
    chapters: List<Chapter>,
    chapterIndex: Int,
    favoritedInit: Boolean,
    likedInit: Boolean,
    settings: ReaderSettings,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val skin = LocalSkin.current
    val imageLoader = rememberReaderImageLoader(deps.gateway, deps.downloadFiles, deps.authCall)
    val listState = rememberLazyListState()
    val context = LocalPlatformContext.current
    // 亮度与常亮直接作用在宿主 Activity 的窗口上
    val activityContext = LocalContext.current
    val activity = remember(activityContext) { activityContext.findActivity() }
    val windowSize = LocalWindowInfo.current.containerSize
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    // 页宽取「窗口宽」与正文列宽的较小值：竖屏手机两者相同，1280dp 横屏窗口里正文列只有 840dp，
    // 横向单页因此收成居中的阅读列，而不是拉满整屏只留左右各 340dp 纯黑边。
    // 解码尺寸也从这一页的真实宽高比推算，否则 800x20000 的长条图会被解成 58x1440 再拉满整宽，
    // 放大十几倍必然发糊。
    val wideLayout = LocalWideLayout.current
    val density = LocalDensity.current
    val pageWidthPx = remember(windowSize.width, wideLayout.contentWidthDp, density) {
        val contentWidthPx = with(density) { wideLayout.contentWidthDp.dp.roundToPx() }
        minOf(windowSize.width, contentWidthPx).coerceAtLeast(1)
    }
    // pager 的左右内缩：让单页/双页都落在阅读列内并居中，整条翻页手势区仍是满窗宽
    val pageSidePadding = with(density) { ((windowSize.width - pageWidthPx) / 2f).coerceAtLeast(0f).toDp() }
    // 已解码页的真实宽高比：解码成功即回填，只驱动页框布局
    val pageRatios = remember(photo) { mutableStateMapOf<Int, Float>() }
    // 只驱动请求尺寸的比例：仅当解码结果顶满请求框时才回填，避免一次回填就把内存缓存键换掉
    val decodeRatios = remember(photo) { mutableStateMapOf<Int, Float>() }
    var chapterRatioSeed by remember(photo) { mutableStateOf<Float?>(null) }
    // 阅读器设置：首载后从设置存储读取，决定音量键、常亮、亮度、质量档与预取页数
    var volumeKeyPaging by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var readerBrightness by remember { mutableIntStateOf(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) }
    var imageQuality by remember { mutableStateOf(ReaderImageQuality.STANDARD) }
    var prefetchPages by remember { mutableIntStateOf(ReaderPreferences.DEFAULT_PREFETCH_PAGES) }
    // 左右模式整屏展示，解码尺寸按屏幕容器
    val screenSize = remember(pageWidthPx, windowSize.height, imageQuality) {
        // 横向单页按阅读列宽解码（宽屏下小于窗口宽），同样受质量档预算约束，预取与展示共用这一份尺寸
        val fit = readerScreenDecodeSize(pageWidthPx, windowSize.height, imageQuality.maxDecodePixels)
        Size(fit.width, fit.height)
    }
    // 横向翻页整屏、纵向按容器宽，两套排布的解码尺寸不同
    var horizontal by rememberSaveable { mutableStateOf(false) }
    // 双页阅读只在大屏横屏下叠加在左右阅读上：默认单页，转屏或换设备后自动回落到单页。
    var spreadMode by rememberSaveable { mutableStateOf(ReaderSpreadMode.SINGLE) }
    val spreadEligible = ReaderSpreadPolicy.supportsSpread(wideLayout.widthDp, wideLayout.heightDp)
    val spreadActive = spreadEligible && spreadMode == ReaderSpreadMode.DOUBLE && horizontal
    // pager 的页数随单/双页切换：跨页索引只是 pager 下标，持久化仍走单页索引
    val pagerPageCount = if (spreadActive) {
        ReaderSpreadPolicy.spreadCount(photo.images.size)
    } else {
        photo.images.size
    }
    val pagerState = rememberPagerState { pagerPageCount }
    // 双页并存时两页共用一档质量预算：单页宽度取半屏、预算取一半，合计不翻倍
    val spreadPageSize = remember(pageWidthPx, windowSize.height, imageQuality) {
        val fit = readerSpreadPageDecodeSize(
            pageWidthPx,
            windowSize.height,
            imageQuality.maxDecodePixels,
        )
        Size(fit.width, fit.height)
    }
    // 页码、预取账本与进度闸门的唯一真源；换章或页数变化才换一份
    val session = remember(photo.photoId, photo.images.size) { ReaderSession(photo.images.size) }
    // 换话、页宽变了、切换纵横或换质量档后，按旧尺寸解的页要重新发一次，否则新尺寸永远等不到解码
    LaunchedEffect(pageWidthPx, horizontal, imageQuality, spreadActive) { session.resetPrefetchLedger() }
    // 当前页是否处于放大态：放大时让出翻页手势，交给缩放层自己消费
    var pageZoomed by remember(photo) { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage, spreadActive) { pageZoomed = false }
    // 长图模式下双击打开的整屏缩放层：存放 photo.images 下标，null 表示未打开
    var zoomOverlayIndex by remember(photo) { mutableStateOf<Int?>(null) }
    // 页码的唯一真源：顶栏、跳页器、音量键与预取都从这份计划取页码，跳页冻结也在这里
    val pagePlan by session.plan.collectAsState()
    // 阅读进度：纵横模式落定后才恢复上次页码，恢复只做一次
    var horizontalReady by remember { mutableStateOf(false) }
    val progressRestored by session.restored.collectAsState()
    // 离开阅读器时补记当前页：状态对象在离开时仍可读
    val latestPage by rememberUpdatedState(pagePlan.currentPage)
    var showPageSheet by rememberSaveable { mutableStateOf(false) }
    var showChapterSheet by rememberSaveable { mutableStateOf(false) }
    var showCommentSheet by rememberSaveable { mutableStateOf(false) }
    var pickerValue by rememberSaveable { mutableIntStateOf(1) }
    // 原版点击正文切换上下栏显隐
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var lastVolumeAt by remember { mutableLongStateOf(0L) }
    var favorited by remember(photo.albumId) { mutableStateOf(favoritedInit) }
    // 详情页改过喜欢状态时以共享表为准，避免回来读到的还是首载快照
    var liked by remember(photo.albumId) {
        mutableStateOf(deps.sessionCache.engagement.likedOf(photo.albumId) ?: likedInit)
    }
    var engagementBusy by remember { mutableStateOf(false) }
    var comments by remember(photo.albumId) { mutableStateOf<CommentPage?>(null) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsFailed by remember { mutableStateOf(false) }
    var commentsExpired by remember { mutableStateOf(false) }
    var commentText by rememberSaveable(photo.albumId) { mutableStateOf("") }
    var commentBusy by remember { mutableStateOf(false) }
    // 评论抽屉内的回复：目标评论与发送态
    var replyTarget by rememberSaveable(photo.albumId, stateSaver = CommentItemNullableSaver) { mutableStateOf<CommentItem?>(null) }
    var replyBusy by remember { mutableStateOf(false) }
    var showTrackPrompt by rememberSaveable(photo.photoId) { mutableStateOf(false) }
    var trackPromptShown by rememberSaveable(photo.photoId) { mutableStateOf(false) }
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
    // 顶栏页码：双页下按单页索引显示当前可见范围（如 4-5/12），仍以单页坐标对外
    val pageLabel = if (spreadActive && photo.images.isNotEmpty()) {
        val pages = ReaderSpreadPolicy.pagesInSpread(
            ReaderSpreadPolicy.spreadIndexForPage(pagePlan.currentPage, photo.images.size),
            photo.images.size,
        )
        "${pages.joinToString("-")}/${photo.images.size}"
    } else {
        "${pagePlan.currentPage}/${photo.images.size}"
    }

    // 恢复提示与音量键提示共用同一条队列，避免两条 Snackbar 互相顶掉
    val snackbarQueue = remember { Mutex() }

    LaunchedEffect(Unit) {
        horizontal = settings.readerHorizontal()
        spreadMode = settings.readerSpread()
        // 纵横模式决定跳页走 pager 还是列表，恢复进度必须等它落定
        horizontalReady = true
        volumeKeyPaging = settings.readerVolumeKeyPaging()
        keepScreenOn = settings.readerKeepScreenOn()
        readerBrightness = settings.readerBrightness()
        imageQuality = settings.readerImageQuality()
        prefetchPages = settings.readerPrefetchPages()
        if (volumeKeyPaging && !settings.volumeKeyHintShown()) {
            delay(1000)
            snackbarQueue.withLock {
                snackbarHostState.showSnackbar("可使用音量键＋翻页")
            }
            settings.markVolumeKeyHintShown()
        }
    }

    // 阅读亮度作用在 Activity 窗口上：进入页面按设置应用，离开时恢复原值。
    // 换章时新旧页短暂并存，原亮度只由首个页面记录、最后一个页面恢复，否则旧页销毁会抹掉新页的亮度。
    val readerWindow = activity?.window
    DisposableEffect(readerWindow) {
        ReaderWindowLeases.enterBrightness(readerWindow?.attributes?.screenBrightness)
        onDispose {
            val restore = ReaderWindowLeases.originalBrightness
            if (ReaderWindowLeases.exitBrightness() && readerWindow != null && restore != null) {
                readerWindow.attributes = readerWindow.attributes.apply {
                    screenBrightness = restore
                }
            }
        }
    }
    LaunchedEffect(readerWindow, readerBrightness) {
        if (readerWindow == null) return@LaunchedEffect
        readerWindow.attributes = readerWindow.attributes.apply {
            screenBrightness = if (readerBrightness == ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                readerBrightness / 100f
            }
        }
    }
    // 阅读时保持亮屏：开关与窗口标记同生命周期；并存的新旧页按引用计数，最后一个离开才清标记
    DisposableEffect(readerWindow, keepScreenOn) {
        if (readerWindow == null || !keepScreenOn) return@DisposableEffect onDispose {}
        ReaderWindowLeases.enterKeepScreenOn()
        readerWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (ReaderWindowLeases.exitKeepScreenOn()) {
                readerWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /**
     * 边界拖拽翻页的唯一出口：按方向取相邻页，夹进 pager 有效范围后起 Spring 动画。
     * 双页与单页两条编排共用，避免一处夹取、另一处漏夹取而落到不存在的页。
     * 目标页在松手时定下（与旧编排一致），惯性复核通过后才真的翻。
     */
    fun turnPage(turn: EdgePageTurn, stillPinned: suspend () -> Boolean) {
        val target = pagerState.currentPage + if (turn == EdgePageTurn.Next) 1 else -1
        scope.launch {
            if (!stillPinned()) return@launch
            pagerState.springAnimateToPage(clampPagerTarget(target, pagerPageCount))
        }
    }

    /** 跳页：先让计划进入“跳页中”，再驱动 pager 或列表；落地后才恢复可见页回写。 */
    fun goToPage(page: Int) {
        // 双页下把目标换算成所在跨页的锚点：进度、页码与跳页都用同一个单页坐标，
        // 用户输入第 5 页时落到含 4、5 页的那一屏，不会因为并排而错开一页。
        val canonical = if (spreadActive) {
            ReaderSpreadPolicy.anchorPageForPage(page, photo.images.size)
        } else {
            page
        }
        // 冻结与解冻都交给 session：目标页在动画前定下，落地后才放开可见页回写
        val target = session.requestJump(canonical) ?: return
        if (horizontal) {
            val pagerTarget = if (spreadActive) {
                ReaderSpreadPolicy.spreadIndexForPage(target, photo.images.size)
            } else {
                target - 1
            }
            scope.launch {
                try {
                    pagerState.springAnimateToPage(clampPagerTarget(pagerTarget, pagerPageCount))
                } finally {
                    session.onJumpSettled(target)
                }
            }
        } else {
            scope.launch {
                try {
                    listState.scrollToItem(target - 1)
                } finally {
                    session.onJumpSettled(target)
                }
            }
        }
    }

    // 恢复上次读到的页码：等纵横模式落定后再跳，避免恢复动作又被布局回写覆盖。
    // progressRestored 同时作为「已尝试恢复」的闸门，进度写入要等它翻转后才允许，
    // 否则首帧的「第 1 页」会先写掉还没读的旧进度。
    LaunchedEffect(horizontalReady, photo.photoId, photo.images.size) {
        if (!horizontalReady) return@LaunchedEffect
        // 首帧布局已落定：放开恢复流程（session 内部先读旧进度、再开写盘闸门）
        session.onLayoutReady()
        val outcome = session.restore(
            readSaved = { settings.readerLastPage(photo.photoId) },
            startPage = startPage,
        ) ?: return@LaunchedEffect
        val saved = outcome.saved
        // 详情页预览点第 i 张时优先直达该页；起始页无效再回退历史进度
        val target = outcome.targetPage ?: return@LaunchedEffect
        // 双页下提示与落点一致：显示的是目标页所在的跨页锚点，避免提示页比画面多一页
        val restoredPage = if (spreadActive) {
            ReaderSpreadPolicy.anchorPageForPage(target, photo.images.size)
        } else {
            target
        }
        if (restoredPage != pagePlan.currentPage) goToPage(restoredPage)
        // 直达预览页不提示「恢复进度」，只有回退历史进度时才提示
        val directPage = startPage != null && startPage in 1..photo.images.size
        if (directPage) return@LaunchedEffect
        snackbarQueue.withLock {
            snackbarHostState.showSnackbar(
                if (saved != null && saved.total != photo.images.size) {
                    "章节页数已更新，已恢复到第 $restoredPage 页"
                } else {
                    "已恢复到上次读到的第 $restoredPage 页"
                },
            )
        }
    }

    /** 记进度：已读到末页说明本章读完，清键；否则写键。 */
    suspend fun persistProgress(progress: ReaderPageProgress) {
        if (shouldClearReaderProgress(progress)) {
            settings.clearReaderLastPage(photo.photoId)
        } else {
            settings.setReaderLastPage(photo.photoId, progress)
        }
    }

    // 页码一变就记进度：读到最后一页改为清掉进度键，未读完才落盘。
    // 落盘前等一下翻页停下来，避免长图连滚时把每一页都写一遍设置存储
    LaunchedEffect(pagePlan.currentPage, photo.images.size, progressRestored) {
        // 闸门未开（首帧还没恢复完）或本章无页时不写盘
        if (!session.canPersistProgress) return@LaunchedEffect
        delay(PROGRESS_SAVE_DEBOUNCE_MS)
        val pending = session.progressToSave() ?: return@LaunchedEffect
        persistProgress(pending)
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
                persistProgress(progress)
            }
        }
    }

    /** 切换阅读方向：同一章节内保持当前页，不重新请求图片。 */
    fun switchMode(nextHorizontal: Boolean) {
        if (nextHorizontal == horizontal) return
        val page = pagePlan.currentPage.coerceIn(1, totalPages)
        horizontal = nextHorizontal
        scope.launch { settings.setReaderHorizontal(nextHorizontal) }
        val nextSpreadActive = spreadEligible && spreadMode == ReaderSpreadMode.DOUBLE && nextHorizontal
        if (nextHorizontal) {
            val nextPageCount = if (nextSpreadActive) {
                ReaderSpreadPolicy.spreadCount(photo.images.size)
            } else {
                photo.images.size
            }
            val index = if (nextSpreadActive) {
                ReaderSpreadPolicy.spreadIndexForPage(page, photo.images.size)
            } else {
                page - 1
            }
            scope.launch { pagerState.scrollToPage(index.coerceIn(0, (nextPageCount - 1).coerceAtLeast(0))) }
        } else {
            scope.launch { listState.scrollToItem((page - 1).coerceIn(0, lastIndex)) }
        }
    }

    // 转屏或分屏改变可用宽度时，pager 的页数会切换单/双页语义：
    // 用当前单页锚点重新定位到对应的跨页，避免把「第 4 页」直接当成第 4 屏而错位。
    LaunchedEffect(spreadActive, horizontal, pagerPageCount) {
        if (!progressRestored || !horizontal || pagerPageCount <= 0) return@LaunchedEffect
        val page = pagePlan.currentPage.coerceIn(1, totalPages)
        val index = if (spreadActive) {
            ReaderSpreadPolicy.spreadIndexForPage(page, photo.images.size)
        } else {
            page - 1
        }
        pagerState.scrollToPage(index.coerceIn(0, pagerPageCount - 1))
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
            val outcome = deps.authCall.call { deps.gateway.toggleFavorite(photo.albumId) }
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
        val next = !liked
        engagementBusy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.toggleLike(photo.albumId) }
            engagementBusy = false
            if (outcome is SessionOutcome.Ok) {
                liked = next
                deps.sessionCache.engagement.setLiked(photo.albumId, next)
                snackbarHostState.showSnackbar(if (next) "已喜欢" else "已取消喜欢")
            } else {
                reportAction(outcome, "操作失败")
            }
        }
    }

    fun loadComments() {
        commentsLoading = true
        commentsFailed = false
        commentsExpired = false
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.comments(photo.albumId) }
            commentsLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> comments = outcome.value
                // 内联「加载失败 + 重试」对失效是误导：重试必然再吃一次 401
                SessionOutcome.Expired -> {
                    commentsExpired = true
                    reportAction(outcome, "评论加载失败")
                }
                is SessionOutcome.Failed -> commentsFailed = true
            }
        }
    }

    fun openComments() {
        showCommentSheet = true
        if (comments == null) loadComments()
    }

    // 评论抽屉展开态跨旋转保留，但评论列表本身不可序列化：旋转恢复后若已展开且数据为空则补拉一次
    LaunchedEffect(showCommentSheet, photo.albumId) {
        if (showCommentSheet && comments == null && !commentsLoading && !commentsFailed && !commentsExpired) loadComments()
    }

    fun sendComment() {
        if (!requireLogin() || commentBusy || commentText.isBlank()) return
        commentBusy = true
        val text = commentText.trim()
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.postComment(photo.albumId, text) }
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
            val outcome = deps.authCall.call { deps.gateway.replyComment(photo.albumId, text, target.id) }
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
            val outcome = deps.authCall.call { deps.gateway.setTracking(photo.albumId) }
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
    // 翻页动作走 rememberUpdatedState：DisposableEffect 只注册一次，闭包里的 spreadActive/
    // photo 若直接捕获会在页内切换方向或换章后过期，音量键就会走进错误分支。
    val onVolumeStep by rememberUpdatedState(
        newValue = { delta: Int ->
            // 双页下按屏步进：音量键一次翻一屏，不会因为锚点相同而卡住
            goToPage(
                if (spreadActive) {
                    ReaderSpreadPolicy.stepAnchor(pagePlan.currentPage, delta, photo.images.size)
                } else {
                    stepPages(pagePlan, delta)
                },
            )
        },
    )
    DisposableEffect(volumeKeyPaging) {
        // 关闭音量键翻页时直接不注册：按键回到系统，恢复音量调节
        if (!volumeKeyPaging) return@DisposableEffect onDispose {}
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
                        onVolumeStep(delta)
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
    LaunchedEffect(pagePlan.currentPage, photo.images.size, spreadActive) {
        if (trackPromptShown || photo.images.isEmpty()) return@LaunchedEffect
        if (chapters.isEmpty() || chapterIndex != chapters.lastIndex) return@LaunchedEffect
        // 双页下当前页记的是末屏锚点，判断「已到末页」要看是否落在最后那一屏
        val atLastPage = if (spreadActive) {
            val total = photo.images.size
            ReaderSpreadPolicy.spreadIndexForPage(pagePlan.currentPage, total) ==
                ReaderSpreadPolicy.spreadCount(total) - 1
        } else {
            pagePlan.currentPage == photo.images.size
        }
        if (!atLastPage) return@LaunchedEffect
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
                        MinTouchTarget(
                            onClick = {
                                pickerValue = pagePlan.currentPage
                                showPageSheet = true
                            },
                        ) {
                            TextButton(
                                text = pageLabel,
                                onClick = {
                                    pickerValue = pagePlan.currentPage
                                    showPageSheet = true
                                },
                                minWidth = 0.dp,
                                minHeight = 36.dp,
                            )
                        }
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
                    contentPadding = PaddingValues(horizontal = pageSidePadding),
                    modifier = Modifier
                        .fillMaxSize()
                        // 放大时整条手势链都交给缩放层，未放大才允许翻页
                        .pagerGestureOverride(pagerState, enabled = !pageZoomed),
                    // 手势由 pagerGestureOverride 驱动，避免与原生拖拽竞争
                    userScrollEnabled = false,
                    pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                    key = { index ->
                        ReaderSpreadPolicy.pagerKey(
                            photoId = photo.photoId,
                            spreadActive = spreadActive,
                            pageCount = photo.images.size,
                            index = index,
                            singlePageIndex = photo.images.getOrNull(index)?.index,
                        )
                    },
                ) { index ->
                    if (spreadActive) {
                        val spreadImages = ReaderSpreadPolicy.pagesInSpread(index, photo.images.size)
                            .mapNotNull { page -> photo.images.getOrNull(page - 1) }
                        ReaderSpreadPage(
                            images = spreadImages,
                            imageLoader = imageLoader,
                            decodeSize = spreadPageSize,
                            modifier = Modifier.fillMaxSize(),
                            isCurrentPage = index == pagerState.currentPage,
                            onTap = { chromeVisible = !chromeVisible },
                            onZoomChanged = { zoomed ->
                                if (index == pagerState.currentPage) pageZoomed = zoomed
                            },
                            // 整屏同步放大后内容到边界仍继续横拖时接翻页，语义与单页一致
                            onHorizontalEdgeDrag = if (pageZoomed) {
                                { dragX, stillPinned ->
                                    val turn = decideEdgePageTurn(
                                        dragX = dragX,
                                        // 双页一屏铺满窗口，翻页行程仍按整屏宽度判定
                                        pageWidth = pageWidthPx.toFloat(),
                                        currentPage = pagerState.currentPage,
                                        pageCount = pagerPageCount,
                                    )
                                    if (turn != EdgePageTurn.None) turnPage(turn, stillPinned)
                                }
                            } else {
                                null
                            },
                        )
                    } else {
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
                                    if (turn != EdgePageTurn.None) turnPage(turn, stillPinned)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            } else {
                // 长列表与它的滚动条必须同层：TrackedLazyColumn 内部就是 Box { LazyColumn + ListScrollBar }，
                // 轨道与列表共用同一份 contentPadding；横向模式用的是 Pager，不经过这里
                TrackedLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().readableWidth(),
                    contentPadding = innerPadding,
                ) {
                    itemsIndexed(photo.images, key = { _, image -> image.index }) { index, image ->
                        // 布局比例优先本页已知比例、其次章节种子；请求尺寸只认解码比例，两者分开演进
                        val frame = readerPageFrame(
                            pageWidthPx = pageWidthPx,
                            knownRatio = pageRatios[image.index],
                            chapterSeed = chapterRatioSeed,
                            decodeRatio = decodeRatios[image.index],
                            maxPixels = imageQuality.maxDecodePixels,
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
                            onDoubleTap = { zoomOverlayIndex = index },
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
            cornerRadius = skin.shapes.sheetCorner,
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
                // 水平内边距由抽屉自带，这里只留垂直间距
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                Text(text = "跳转到第 $pickerValue 页")
            }
        }

        OverlayBottomSheet(
            show = showChapterSheet,
            title = "章节",
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { showChapterSheet = false },
        ) {
            if (chapters.isEmpty()) {
                Text(
                    text = CouiStrings.EmptyChapters,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(420.dp))) {
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
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { showCommentSheet = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val page = comments
                when {
                    commentsLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { InfiniteProgressIndicator() }

                    commentsExpired -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val openLogin = {
                            showCommentSheet = false
                            onOpenLogin()
                        }
                        MinTouchTarget(onClick = openLogin) {
                            TextButton(text = "登录已过期，点击去登录", onClick = openLogin, minWidth = 0.dp)
                        }
                    }

                    commentsFailed -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MinTouchTarget(onClick = ::loadComments) {
                            TextButton(text = "加载失败，点击重试", onClick = ::loadComments, minWidth = 0.dp)
                        }
                    }

                    page == null || page.items.isEmpty() -> Text(
                        text = CouiStrings.EmptyComments,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(320.dp))) {
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
                    onTextChange = { if (it.length <= COMMENT_REPLY_MAX_LENGTH) commentText = it },
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
            maxWidth = overlayDialogMaxWidth(),
            title = "已经最后一话了！",
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = { closeTrackPrompt() },
        ) {
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
    // 该发哪几页由 session 统一判定（内部转调 advancePrefetch，页码收敛与去重都在那里），collectLatest 让快速滚动只处理最新页
    LaunchedEffect(
        listState,
        pagerState,
        photo.photoId,
        photo.images.size,
        imageLoader,
        pageWidthPx,
        screenSize,
        spreadActive,
        spreadPageSize,
        horizontal,
        imageQuality,
        prefetchPages,
    ) {
        snapshotFlow {
            if (horizontal) {
                // 双页下 pager 下标是跨页索引：回写计划前先换算成该屏的单页锚点
                if (spreadActive) {
                    (ReaderSpreadPolicy.anchorPageForSpread(pagerState.currentPage, photo.images.size) - 1)
                        .coerceAtLeast(0)
                } else {
                    pagerState.currentPage
                }
            } else {
                listState.firstVisibleItemIndex
            }
        }.collectLatest { first ->
            // 页码回写与「该发哪几页」由 session 判定；屏幕只按展示同源尺寸入队
            for (index in session.onVisibleIndex(first, ahead = prefetchPages)) {
                val page = photo.images[index]
                val size = if (horizontal) {
                    // 左右模式整屏展示；双页时两页共用半屏尺寸与半档预算，与展示保持一致
                    if (spreadActive) spreadPageSize else screenSize
                } else {
                    // 纵向按容器宽推算页框；尺寸与展示走同一处换算，否则内存缓存键不同，预取等于白解
                    readerPageFrame(
                        pageWidthPx = pageWidthPx,
                        knownRatio = pageRatios[page.index],
                        chapterSeed = chapterRatioSeed,
                        decodeRatio = decodeRatios[page.index],
                        maxPixels = imageQuality.maxDecodePixels,
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
            .glassSurface(
                backdrop = backdrop,
                role = GlassRole.Toolbar,
                tint = MiuixTheme.colorScheme.surfaceContainer,
                shape = remember { RoundedCornerShape(50.dp) },
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

/** pager 目标页夹取：越界目标一律落到端点页，空 pager 退化为 0。 */
internal fun clampPagerTarget(target: Int, pageCount: Int): Int =
    target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

/**
 * 边界空拖采集：telephoto 越过触摸阈值后会无条件消费全部位移，内容到边界之后也不放手，
 * 父节点在 Main pass 只能看到「已消费」。这里在 Initial pass 抢读原始位移，
 * 再用内容偏移判断内容是否还在跟随手势：内容停下后累计的位移才算边界空拖。
 * 双页与单页两条缩放路径共用这一段，判定与复核时机不会各写一份而漂移。
 */
private suspend fun PointerInputScope.collectHorizontalEdgeDrag(
    contentOffsetX: () -> Float,
    onEdgeDrag: (dragX: Float, stillPinned: suspend () -> Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val edgeTracker = EdgeDragTracker()
            .apply { start(contentOffsetX()) }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // 多指是捏合缩放，不参与翻页判定
            if (event.changes.size > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            edgeTracker.onDrag(
                deltaX = change.positionChange().x,
                offsetX = contentOffsetX(),
            )
            if (!change.pressed) {
                val pinnedDragX = edgeTracker.pinnedDragX()
                if (pinnedDragX != 0f) {
                    // 快甩时 telephoto 要等松手后才用惯性把内容带走，拖动期间内容几乎不动，
                    // 会把整段位移误算成空拖：把复核推迟到惯性起步之后。
                    val offsetAtRelease = contentOffsetX()
                    onEdgeDrag(pinnedDragX) {
                        kotlinx.coroutines.delay(EDGE_CONFIRM_DELAY_MS)
                        kotlin.math.abs(contentOffsetX() - offsetAtRelease) < EDGE_CONFIRM_MOVE_PX
                    }
                }
                break
            }
        }
    }
}

/**
 * 双页阅读的一屏：两页共用一个缩放层，整屏同步缩放。
 *
 * 不用「两页各自缩放」：并排两页各带一套手势识别会在中缝附近抢指针，
 * 放大后横向拖动究竟该平移左页、右页还是翻页没有稳定答案。整屏共用一个
 * ZoomableState 后，捏合与平移只作用于整屏，未放大时仍由 pager 负责翻页；
 * 放大到边界后沿用单页的 edge-drag 交接，翻页手势不会被缩放层吃掉。
 */
@Composable
private fun ReaderSpreadPage(
    images: List<PageImage>,
    imageLoader: ImageLoader,
    decodeSize: Size,
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
    onTap: (() -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onHorizontalEdgeDrag: ((dragX: Float, stillPinned: suspend () -> Boolean) -> Unit)? = null,
) {
    val zoomableState = rememberZoomableState()
    val zoomed by remember(zoomableState) {
        derivedStateOf { isPageZoomed(zoomableState.zoomFraction) }
    }
    LaunchedEffect(zoomed) { onZoomChanged?.invoke(zoomed) }
    // 翻走即复位：整屏缩放态不残留到下一屏
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) zoomableState.resetZoom()
    }
    val tapHandler: ((Offset) -> Unit)? = if (onTap != null) {
        { onTap() }
    } else {
        null
    }
    // 与单页缩放路径同一套边界空拖判定；只是内容由整屏两页共同承担
    val edgeDrag = rememberUpdatedState(onHorizontalEdgeDrag)
    Box(
        modifier = if (onHorizontalEdgeDrag != null) {
            modifier
                .zoomable(state = zoomableState, gestures = EnabledZoomGestures.ZoomAndPan, onClick = tapHandler)
                .pointerInput(zoomableState) {
                    collectHorizontalEdgeDrag(
                        contentOffsetX = { zoomableState.contentTransformation.offset.x },
                        onEdgeDrag = { dragX, stillPinned -> edgeDrag.value?.invoke(dragX, stillPinned) },
                    )
                }
        } else {
            modifier.zoomable(state = zoomableState, gestures = EnabledZoomGestures.ZoomAndPan, onClick = tapHandler)
        },
    ) {
        if (images.size >= 2) {
            Row(modifier = Modifier.fillMaxSize()) {
                images.forEach { image ->
                    ReaderPage(
                        image = image,
                        imageLoader = imageLoader,
                        decodeSize = decodeSize,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        } else {
            // 首页单页与末尾补空：单页保持与并排页相同的宽度，居中显示，翻屏时版面不跳
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                images.firstOrNull()?.let { image ->
                    ReaderPage(
                        image = image,
                        imageLoader = imageLoader,
                        decodeSize = decodeSize,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.5f),
                    )
                }
            }
        }
    }
}

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
                    // 单页的缩放状态套了一层 rememberZoomableImageState，内容偏移在内层
                    collectHorizontalEdgeDrag(
                        contentOffsetX = { zoomableState.zoomableState.contentTransformation.offset.x },
                        onEdgeDrag = { dragX, stillPinned -> edgeDrag.value?.invoke(dragX, stillPinned) },
                    )
                }
            } else {
                modifier
            },
        ) {
            ZoomableAsyncImage(
                model = request,
                contentDescription = null,
                imageLoader = imageLoader,
                state = zoomableState,
                contentScale = ContentScale.Fit,
                onClick = tapHandler,
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) ReaderLoadFailedHint { failed = false; retryKey++ }
        }
        return
    }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    // 需要区分双击时用 detectTapGestures 自己起手势识别，单击回调顺延到双击超时之后
    val tapModifier = when {
        onDoubleTap != null -> Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnTap?.invoke() },
                onDoubleTap = { currentOnDoubleTap?.invoke() },
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
        if (failed) ReaderLoadFailedHint { failed = false; retryKey++ }
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
        MinTouchTarget(onClick = onRetry) {
            TextButton(
                text = "重试",
                onClick = onRetry,
                minWidth = 0.dp,
                minHeight = 32.dp,
            )
        }
    }
}

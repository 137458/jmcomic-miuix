package com.jm3.app.miuix.ui.coui.page

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.jm3.app.miuix.ui.CommentItemNullableSaver
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.BlendMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.diagnostic.renderDiagnosticProbe
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Item
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.slider.Slider
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBarSurface
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLibraryNavBarMinSdk
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarPlainBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiDialogListMaxHeight
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiSpotlightLight
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.DEFAULT_PAGE_ASPECT_RATIO
import com.jm3.app.miuix.ui.EDGE_CONFIRM_DELAY_MS
import com.jm3.app.miuix.ui.EDGE_CONFIRM_MOVE_PX
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.configureReaderDefaults
import com.jm3.app.miuix.ui.notifyAction
import com.jm3.app.miuix.ui.EdgeDragTracker
import com.jm3.app.miuix.ui.EdgePageTurn
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.ReaderLoadGate
import com.jm3.app.miuix.ui.ReaderVolumeKeys
import com.jm3.app.miuix.ui.ReaderWindowLeases
import com.jm3.app.miuix.ui.TrackedLazyColumn
import com.jm3.app.miuix.ui.chapterSeedCandidate
import com.jm3.app.miuix.ui.decideEdgePageTurn
import com.jm3.app.miuix.ui.isPageZoomed
import com.jm3.app.miuix.ui.pageAspectRatio
import com.jm3.app.miuix.ui.readerPageBackfill
import com.jm3.app.miuix.ui.readerPageFrame
import com.jm3.app.miuix.ui.readerScreenDecodeSize
import com.jm3.app.miuix.ui.readerSpreadPageDecodeSize
import com.jm3.app.miuix.ui.rememberReaderImageLoader
import com.jm3.core.runCatchingSuspending
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderPagePlan
import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.reader.advancePrefetch
import com.jm3.core.reader.jumpTo
import com.jm3.core.reader.readerProgressOnExit
import com.jm3.core.reader.resolveInitialPage
import com.jm3.core.reader.settleJump
import com.jm3.core.reader.shouldClearReaderProgress
import com.jm3.core.reader.stepPages
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/** 阅读进度落盘前的静置时长：翻页停下来才写，避免连滚时逐页写设置存储。 */
private const val PROGRESS_SAVE_DEBOUNCE_MS = 400L
private const val PageSliderTickMaxPages = 24
private const val TRACK_MUTE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

/** 从 Compose 的 Context 链上找宿主 Activity，窗口亮度与常亮标记都要打在它上面。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 阅读器首载状态：加载、内容、失败三分支，失败再分「登录失效」与「可重试」两种结局。 */
private sealed interface ReaderUiState {
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
private fun readerLoadFailure(outcome: SessionOutcome<*>): ReaderUiState.Failed? = when (outcome) {
    is SessionOutcome.Ok -> null
    SessionOutcome.Expired -> ReaderUiState.Failed(SessionOutcome.Expired.message, sessionExpired = true)
    is SessionOutcome.Failed -> ReaderUiState.Failed(failureCopy(outcome, "章节加载失败"))
}

@Composable
fun CouiReaderScreen(
    deps: ScreenDeps,
    photoId: String,
    startPage: Int? = null,
    settings: ReaderSettings,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }
    val scope = rememberCoroutineScope()
    // 首载与重试共享一次去重：新一次加载先取消上一次，结局写回前校验代际，
    // 避免连点重试并行发出多个 gateway.photo，旧响应覆盖新状态。
    val loadGate = remember(photoId) { ReaderLoadGate() }
    var loadJob by remember(photoId) { mutableStateOf<Job?>(null) }

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
                state = ReaderUiState.Loaded(
                    photo = photo,
                    chapters = detail?.chapters.orEmpty(),
                    chapterIndex = detail?.chapters?.indexOfFirst { it.id == photo.photoId } ?: -1,
                    favorited = detail?.favorited == true,
                    liked = detail?.liked == true,
                )
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
        // 首载也要登记 Job：失败重试入口靠它取消在途加载，注释与实现才一致
        loadJob = scope.launch { loadContent() }
    }

    when (val s = state) {
        is ReaderUiState.Loading -> ReaderStateScreen(title = "阅读", onBack = onBack) {
            CouiLoadingGroup(
                modifier = Modifier.padding(top = 8.dp),
                text = "正在加载章节…",
            )
        }
        is ReaderUiState.Failed -> ReaderStateScreen(title = "阅读", onBack = onBack) {
            // 失效不给重试：重试必然再吃一次 401，只能去登录
            if (s.sessionExpired) {
                CouiFeedLoginGroup(message = s.message, onOpenLogin = onOpenLogin)
            } else {
                CouiErrorGroup(
                    message = s.message,
                    onRetry = ::load,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        is ReaderUiState.Loaded -> {
            LaunchedEffect(s.photo.photoId) {
                val coverUrl = (deps.authCall.call { deps.gateway.coverUrl(s.photo.albumId) } as? SessionOutcome.Ok)?.value
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
            CouiTheme {
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
                    onOpenChapter = onOpenChapter,
                    onOpenLogin = onOpenLogin,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

/** 首载态外壳：加载、失败、登录失效共用一顶栏，只换正文。 */
@Composable
private fun ReaderStateScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    CouiTheme {
        CouiAppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(CouiKitTheme.colorScheme.background),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = title, textAlign = TextAlign.Start) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    colors = couiTopAppBarColors(),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                content()
            }
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
    // 原先由 Scaffold 提供的窗口内边距：HUD 各自避让状态栏/导航栏，列表只让出系统栏高度
    val contentInsets = WindowInsets.systemBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val imageLoader = rememberReaderImageLoader(deps.gateway, deps.downloadFiles, deps.authCall)
    val listState = rememberLazyListState()
    val context = LocalPlatformContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    val wideLayout = LocalWideLayout.current
    val containerWidthPx = windowSize.width.coerceAtLeast(1)
    val readablePaddingPx = with(density) { wideLayout.readableHorizontalPadding.roundToPx() }
    // 竖向按扣减宽屏左右可读留白后的实际页宽推算页框与解码尺寸；
    // 横向 pager 铺满窗口，边界拖动手势仍以窗口全宽 containerWidthPx 判定。
    val pageWidthPx = couiReaderVerticalPageWidthPx(containerWidthPx, readablePaddingPx)
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
    val screenSize = remember(windowSize.width, windowSize.height, imageQuality) {
        // 横向整屏同样受质量档预算约束，预取与展示共用这一份尺寸
        val fit = readerScreenDecodeSize(windowSize.width, windowSize.height, imageQuality.maxDecodePixels)
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
    val spreadPageSize = remember(windowSize.width, windowSize.height, imageQuality) {
        val fit = readerSpreadPageDecodeSize(
            windowSize.width,
            windowSize.height,
            imageQuality.maxDecodePixels,
        )
        Size(fit.width, fit.height)
    }
    // 已发起预加载的页号；翻页向前向后都只发一次。键随章节与布局基线走：
    // 换话、页宽变了、切换纵横或换质量档后，按旧尺寸解的页要重新发一次，否则新尺寸永远等不到解码
    val prefetched = remember(photo, pageWidthPx, horizontal, imageQuality, spreadActive) { mutableSetOf<Int>() }
    // 当前页是否处于放大态：放大时让出翻页手势，交给缩放层自己消费
    var pageZoomed by remember(photo) { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage, spreadActive) { pageZoomed = false }
    // 长图模式下双击打开的整屏缩放层：存放 photo.images 下标，null 表示未打开
    var zoomOverlayIndex by remember(photo) { mutableStateOf<Int?>(null) }
    // 页码的唯一真源：顶栏、跳页器、音量键与预取都从这份计划取页码，跳页冻结也在这里
    var pagePlan by remember(photo) { mutableStateOf(ReaderPagePlan.initial(photo.images.size)) }
    // 阅读进度：纵横模式落定后才恢复上次页码，恢复只做一次
    var horizontalReady by remember { mutableStateOf(false) }
    var progressRestored by remember(photo.photoId) { mutableStateOf(false) }
    // 离开阅读器时补记当前页：状态对象在离开时仍可读
    val latestPage by rememberUpdatedState(pagePlan.currentPage)
    var modalState by rememberSaveable(photo.photoId, stateSaver = CouiReaderModalStateSaver) {
        mutableStateOf(CouiReaderModalState())
    }
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
    var commentNotice by remember(photo.albumId) { mutableStateOf<String?>(null) }
    // 评论弹窗内的回复：目标评论与发送态
    var replyTarget by rememberSaveable(photo.albumId, stateSaver = CommentItemNullableSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    var replyBusy by remember { mutableStateOf(false) }
    var replyNotice by remember(photo.albumId) { mutableStateOf<String?>(null) }
    // 回复框草稿：换目标即清空，免得上一条的残稿被带进新回复；跨旋转保留（发送成功后显式清空）
    var replyText by rememberSavedDialogDraft(
        isOpen = replyTarget != null,
        targetKey = replyTarget?.id,
        resetKey = photo.albumId,
    )
    var trackPromptShown by rememberSaveable(photo.photoId) { mutableStateOf(false) }
    var trackMuteChecked by remember { mutableStateOf(false) }
    var trackBusy by remember { mutableStateOf(false) }

    val scheme = CouiKitTheme.colorScheme
    // HUD 采样源：正文层自带不透明底色并作为 backdrop，避免留白与转场露出透明像素
    val hudBackdrop = rememberCouiReaderBackdrop(diagnosticName = "readerHud")

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

    // 阅读亮度与常亮均挂在宿主 Activity 窗口上，经由租约协调换章共存期
    BindCouiReaderWindowEffects(
        keepScreenOn = keepScreenOn,
        readerBrightness = readerBrightness,
    )

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
        val next = jumpTo(pagePlan, canonical)
        if (next == pagePlan) return
        pagePlan = next
        val target = next.pendingTarget ?: return
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
                    pagePlan = settleJump(pagePlan, target)
                }
            }
        } else {
            scope.launch {
                try {
                    listState.scrollToItem(target - 1)
                } finally {
                    pagePlan = settleJump(pagePlan, target)
                }
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
        // 详情页预览点第 i 张时优先直达该页；起始页无效再回退历史进度
        val target = resolveInitialPage(startPage, saved, photo.images.size) ?: return@LaunchedEffect
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
        if (!progressRestored || photo.images.isEmpty()) return@LaunchedEffect
        delay(PROGRESS_SAVE_DEBOUNCE_MS)
        persistProgress(ReaderPageProgress(page = pagePlan.currentPage, total = photo.images.size))
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
    // 等模式加载与初始进度恢复都已落地、且无跳页动画在途时，再用当前单页锚点对齐跨页。
    LaunchedEffect(spreadActive, horizontal, pagerPageCount, horizontalReady, progressRestored) {
        if (
            !couiShouldSyncPagerOnModeChange(
                horizontalReady = horizontalReady,
                progressRestored = progressRestored,
                horizontal = horizontal,
                pagerPageCount = pagerPageCount,
                pendingJumpTarget = pagePlan.pendingTarget,
            )
        ) {
            return@LaunchedEffect
        }
        val page = pagePlan.currentPage.coerceIn(1, totalPages)
        val index = if (spreadActive) {
            ReaderSpreadPolicy.spreadIndexForPage(page, photo.images.size)
        } else {
            page - 1
        }.coerceIn(0, pagerPageCount - 1)
        if (pagerState.currentPage != index) pagerState.scrollToPage(index)
    }

    fun openLoginFromModal() {
        modalState = modalState.dismissAllForNavigation()
        replyTarget = null
        commentNotice = null
        replyNotice = null
        onOpenLogin()
    }

    fun requireLogin(): Boolean {
        if (user != null) return true
        openLoginFromModal()
        return false
    }

    // 动作型调用的统一落点：失效给登录入口，失败给「动作：原因」
    suspend fun reportAction(outcome: SessionOutcome<*>, failurePrefix: String) {
        outcome.notifyAction(
            notify = { snackbarHostState.showSnackbar(it) },
            failurePrefix = failurePrefix,
            onExpired = ::openLoginFromModal,
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
                // 弹窗内保留「登录已过期，点此登录」入口，点击后再关闭弹窗进登录页，避免在后台压住弹窗
                SessionOutcome.Expired -> commentsExpired = true
                is SessionOutcome.Failed -> commentsFailed = true
            }
        }
    }

    fun openComments() {
        commentNotice = null
        modalState = modalState.open(CouiReaderModal.COMMENTS)
        if (comments == null) loadComments()
    }

    fun sendComment() {
        if (!requireLogin() || commentBusy || commentText.isBlank()) return
        commentBusy = true
        commentNotice = null
        val text = commentText.trim()
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.postComment(photo.albumId, text) }
            commentBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    commentText = ""
                    commentNotice = "评论已发表"
                    loadComments()
                }
                SessionOutcome.Expired -> reportAction(outcome, "发表失败")
                is SessionOutcome.Failed -> {
                    commentNotice = "发表失败：" + failureCopy(outcome, "请稍后重试")
                }
            }
        }
    }

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        replyNotice = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.replyComment(photo.albumId, text, target.id) }
            replyBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    replyTarget = null
                    replyNotice = null
                    modalState = modalState.exitReply()
                    commentNotice = "回复已发送"
                    loadComments()
                }
                SessionOutcome.Expired -> reportAction(outcome, "回复失败")
                is SessionOutcome.Failed -> {
                    replyNotice = "回复失败：" + failureCopy(outcome, "请稍后重试")
                }
            }
        }
    }

    /** 关闭末页追更引导：勾选「3天不要显示」时写入静默截止时间。 */
    fun closeTrackPrompt() {
        modalState = modalState.dismiss()
        if (trackMuteChecked) {
            scope.launch {
                settings.setTrackPromptMuteUntil(System.currentTimeMillis() + TRACK_MUTE_DAYS_MS)
            }
        }
    }

    // 评论弹窗的展开状态跨旋转保留，但评论数据本身不是可保存类型：恢复后若已展开且没有数据，
    // 补拉一次，否则用户看到的是「暂无评论」的假空态
    LaunchedEffect(modalState.activeModal, photo.albumId) {
        if (
            modalState.activeModal == CouiReaderModal.COMMENTS &&
            comments == null &&
            !commentsLoading &&
            !commentsFailed &&
            !commentsExpired
        ) {
            loadComments()
        }
    }

    fun followSeries() {
        if (!requireLogin() || trackBusy) return
        trackBusy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.setTracking(photo.albumId) }
            trackBusy = false
            closeTrackPrompt()
            if (outcome is SessionOutcome.Ok) {
                snackbarHostState.showSnackbar("已追踪连载")
            } else {
                reportAction(outcome, "追踪失败")
            }
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
    val modalOpen by rememberUpdatedState(couiHasOpenReaderModal(modalState, replyTarget != null))
    DisposableEffect(volumeKeyPaging) {
        // 关闭音量键翻页时直接不注册：按键回到系统，恢复音量调节
        if (!volumeKeyPaging) return@DisposableEffect onDispose {}
        val mine: (Int) -> Boolean = { keyCode ->
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                else -> 0
            }
            if (!couiShouldHandleReaderVolumeKey(delta, zoomOverlayIndex != null, modalOpen)) {
                false
            } else {
                val now = System.currentTimeMillis()
                if (now - lastVolumeAt >= READER_VOLUME_THROTTLE_MS) {
                    lastVolumeAt = now
                    onVolumeStep(delta)
                }
                true
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
        modalState = modalState.open(CouiReaderModal.TRACK_PROMPT)
    }

    // 全屏 HUD：外层铺实色背景 + 正文采样源自带背景色，避免留白与旋转露出透明像素
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .couiBackdrop(hudBackdrop),
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
                                        // 双页一屏铺满窗口，翻页行程按容器全宽判定
                                        pageWidth = containerWidthPx.toFloat(),
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
                                        // 左右模式整屏一页，页宽就是容器全宽
                                        pageWidth = containerWidthPx.toFloat(),
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
                // 顶部让出 HUD 顶栏净空（首屏内容不被顶栏挡住），底部由尾项 Spacer 让出底栏
                TrackedLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().readableWidth(),
                    contentPadding = PaddingValues(
                        start = contentInsets.calculateLeftPadding(layoutDirection),
                        top = contentInsets.calculateTopPadding() + ReaderHudTopHeight + ReaderHudBarVerticalMargin * 2,
                        end = contentInsets.calculateRightPadding(layoutDirection),
                        bottom = contentInsets.calculateBottomPadding(),
                    ),
                ) {
                    items(photo.images, key = { it.index }) { image ->
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
                            onDoubleTap = { zoomOverlayIndex = photo.images.indexOf(image) },
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }
                    // 底部悬浮工具栏会遮住最后一页末尾，留出可滚动空间
                    item(key = "toolbar-space") { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }

        // HUD 顶栏：外层 TopAppBarSurface 提供顶部渐进模糊与柔和过渡，内层悬浮胶囊只画自身前景
        ReaderTopAppBarSurface(
            visible = chromeVisible,
            backdrop = hudBackdrop,
        ) {
            ReaderHudBar(
                backdrop = hudBackdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .readableWidth(),
                height = ReaderHudTopHeight,
                placement = CouiReaderHudPlacement.TOP,
                diagnosticName = "readerHud/top",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // HUD 栏自带浮动材质与凝光，返回键不叠第二层凝光
                    CouiTopBarPlainBackButton(onClick = onBack, spotlight = null)
                    Text(
                        text = photo.title,
                        kind = CouiTextKind.TopBar,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pageLabel,
                        kind = CouiTextKind.ItemValue,
                        color = scheme.accent,
                        modifier = Modifier
                            // 所在 HUD 面板本身已有凝光，这里不再叠第二层
                            .couiPressable(spotlight = null, minTouchTarget = CouiMinTouchTarget) {
                                pickerValue = pagePlan.currentPage
                                modalState = modalState.open(CouiReaderModal.PAGE_JUMP)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                    ReaderHudChapterStep(
                        icon = CouiIcons.Back,
                        label = "上话",
                        enabled = prevChapter != null,
                        onClick = { prevChapter?.let { onOpenChapter(it.id) } },
                    )
                    ReaderHudChapterStep(
                        icon = CouiIcons.Forward,
                        label = "下话",
                        enabled = nextChapter != null,
                        onClick = { nextChapter?.let { onOpenChapter(it.id) } },
                    )
                }
            }
        }

        // HUD 底栏：等分槽位匹配 CouiCapsuleShape 横向胶囊指示器与触区
        if (chromeVisible) {
            ReaderHudBar(
                backdrop = hudBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = ReaderHudBarVerticalMargin)
                    .fillMaxWidth()
                    .readableWidth(),
                height = ReaderHudBottomHeight,
                placement = CouiReaderHudPlacement.BOTTOM,
                diagnosticName = "readerHud/bottom",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderHudAction(
                        icon = if (horizontal) CouiIcons.Sort else CouiIcons.Book,
                        label = if (horizontal) "直向" else "左右",
                        modifier = Modifier.weight(1f),
                        onClick = { switchMode(!horizontal) },
                    )
                    ReaderHudAction(
                        icon = CouiIcons.List,
                        label = "章节",
                        modifier = Modifier.weight(1f),
                        onClick = { modalState = modalState.open(CouiReaderModal.CHAPTERS) },
                    )
                    ReaderHudAction(
                        icon = CouiIcons.Comment,
                        label = "评论",
                        modifier = Modifier.weight(1f),
                        onClick = ::openComments,
                    )
                    ReaderHudAction(
                        icon = CouiIcons.Folder,
                        label = "收藏",
                        modifier = Modifier.weight(1f),
                        active = favorited,
                        onClick = ::toggleFavorite,
                    )
                    ReaderHudAction(
                        icon = CouiIcons.Star,
                        label = "喜欢",
                        modifier = Modifier.weight(1f),
                        active = liked,
                        onClick = ::toggleLike,
                    )
                }
            }
        }

        // 提示条叠在 HUD 之上：HUD 显示时让出完整底栏与边距，隐藏时贴在系统导航栏上方
        val snackbarBottomPaddingDp = couiReaderSnackbarBottomPaddingDp(
            hudVisible = chromeVisible,
            bottomBarHeightDp = ReaderHudBottomHeightDp,
            barVerticalMarginDp = ReaderHudBarVerticalMarginDp,
        )
        GlassSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = snackbarBottomPaddingDp.dp),
        )

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

        val visibleModal = couiVisibleReaderModal(modalState, hasReplyTarget = replyTarget != null)

        // 跳页：滑杆搭配加减微调按钮，百页以上长章节也能精准选定目标页；
        // 页码越界交给 coerceIn / couiStepJumpPage 夹紧，避免把非法页码送进 goToPage
        Dialog(
            onDismissRequest = { modalState = modalState.dismiss() },
            visible = visibleModal == CouiReaderModal.PAGE_JUMP,
            material = couiDialogMaterial(),
            title = "跳页",
            confirmAction = CouiDialogAction(
                text = "跳转到第 $pickerValue 页",
                onClick = {
                    modalState = modalState.dismiss()
                    goToPage(pickerValue)
                },
            ),
            dismissAction = CouiDialogAction(text = "取消", onClick = { modalState = modalState.dismiss() }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "第 $pickerValue 页 / 共 $totalPages 页",
                        kind = CouiTextKind.ItemTitle,
                        modifier = Modifier.weight(1f),
                    )
                    if (totalPages > 1) {
                        ReaderPageJumpStepper(
                            icon = CouiIcons.Minus,
                            contentDescription = "上一页",
                            enabled = pickerValue > 1,
                            onClick = { pickerValue = couiStepJumpPage(pickerValue, -1, totalPages) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ReaderPageJumpStepper(
                            icon = CouiIcons.Plus,
                            contentDescription = "下一页",
                            enabled = pickerValue < totalPages,
                            onClick = { pickerValue = couiStepJumpPage(pickerValue, 1, totalPages) },
                        )
                    }
                }
                if (totalPages > 1) {
                    Slider(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        value = pickerValue.toFloat(),
                        onValueChange = { pickerValue = it.roundToInt().coerceIn(1, totalPages) },
                        valueRange = 1f..totalPages.toFloat(),
                        // 滑杆只停整数页：两端各留一档，中间档数就是总页数减二
                        steps = (totalPages - 2).coerceAtLeast(0),
                        // 短章节把可落位点画出来，页码不用靠猜；长章节点位会密到成带，改回纯滑杆
                        showTickMarks = totalPages <= PageSliderTickMaxPages,
                    )
                }
            }
        }

        // 章节：列表高度封顶后自身滚动，当前话用后缀标注而不是副标题，省一行行高
        Dialog(
            onDismissRequest = { modalState = modalState.dismiss() },
            visible = visibleModal == CouiReaderModal.CHAPTERS,
            material = couiDialogMaterial(),
            title = "章节",
            confirmAction = CouiDialogAction(text = "关闭", onClick = { modalState = modalState.dismiss() }),
        ) {
            if (chapters.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    CouiBodyText(text = CouiStrings.EmptyChapters)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = couiDialogListMaxHeight(420.dp))) {
                    items(chapters, key = { it.id }) { chapter ->
                        Item(
                            title = {
                                Text(
                                    text = if (chapter.id == photo.photoId) {
                                        chapter.title + "（当前话）"
                                    } else {
                                        chapter.title
                                    },
                                    kind = CouiTextKind.ItemTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                modalState = modalState.dismiss()
                                if (chapter.id != photo.photoId) onOpenChapter(chapter.id)
                            },
                        )
                        // 组卡片内部没有自动分隔线，行间需要自己补；末行不补，避免贴着卡片下沿
                        if (chapter.id != chapters.last().id) Divider()
                    }
                    item(key = "chapter-gap") { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }

        // 评论：直接承接 Dialog 玻璃材质，不再嵌套实底 Group 割裂质感
        Dialog(
            onDismissRequest = { modalState = modalState.dismiss() },
            visible = visibleModal == CouiReaderModal.COMMENTS,
            material = couiDialogMaterial(),
            title = "评论",
            confirmAction = CouiDialogAction(text = "关闭", onClick = { modalState = modalState.dismiss() }),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val page = comments
                when {
                    commentsLoading -> ReaderDialogHint(text = "正在加载评论…")

                    commentsExpired -> ReaderDialogHint(
                        text = "登录已过期，点此登录",
                        onClick = ::openLoginFromModal,
                    )

                    commentsFailed -> ReaderDialogHint(
                        text = "评论加载失败，点此重试",
                        onClick = ::loadComments,
                    )

                    page == null || page.items.isEmpty() -> ReaderDialogHint(text = CouiStrings.EmptyComments)

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = couiDialogListMaxHeight(320.dp)),
                    ) {
                        items(page.items, key = { it.id }) { item ->
                            ReaderCommentRow(
                                item = item,
                                onReply = {
                                    if (user == null) {
                                        openLoginFromModal()
                                    } else {
                                        replyTarget = item
                                        replyText = ""
                                        replyNotice = null
                                        modalState = modalState.enterReply()
                                    }
                                },
                            )
                            if (item.id != page.items.last().id) Divider()
                        }
                    }
                }
                Divider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    ReaderCommentComposer(
                        loggedIn = user != null,
                        text = commentText,
                        busy = commentBusy,
                        notice = commentNotice,
                        onTextChange = { commentText = couiClampCommentInput(it) },
                        onLogin = ::openLoginFromModal,
                        onSend = ::sendComment,
                    )
                }
            }
        }

        // 回复：与评论弹窗互斥；关闭时按 reopenCommentsAfterReply 回退评论弹窗
        val retainedReplyTarget = rememberRetainedTarget(replyTarget)
        Dialog(
            onDismissRequest = {
                if (!replyBusy) {
                    replyTarget = null
                    replyNotice = null
                    modalState = modalState.exitReply()
                }
            },
            visible = replyTarget != null,
            material = couiDialogMaterial(),
            title = "回复 " + retainedReplyTarget?.author.orEmpty(),
            confirmAction = CouiDialogAction(
                text = if (replyBusy) "发送中…" else "发送",
                enabled = !replyBusy,
                onClick = { sendReply(replyText) },
            ),
            dismissAction = CouiDialogAction(
                text = "取消",
                onClick = {
                    if (!replyBusy) {
                        replyTarget = null
                        replyNotice = null
                        modalState = modalState.exitReply()
                    }
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ReaderCommentComposer(
                    loggedIn = true,
                    text = replyText,
                    busy = replyBusy,
                    notice = replyNotice,
                    onTextChange = { replyText = couiClampCommentInput(it) },
                    onLogin = {},
                    onSend = { sendReply(replyText) },
                )
            }
        }

        // 追更引导：直接承接 Dialog 玻璃材质，动作按钮交给弹窗自身槽位（竖向堆叠由库负责）
        Dialog(
            onDismissRequest = { closeTrackPrompt() },
            visible = visibleModal == CouiReaderModal.TRACK_PROMPT,
            material = couiDialogMaterial(),
            title = "已经最后一话了！",
            confirmAction = CouiDialogAction(
                text = if (trackBusy) "追踪中…" else "追踪连载",
                enabled = !trackBusy,
                onClick = ::followSeries,
            ),
            dismissAction = CouiDialogAction(text = "关闭", onClick = { closeTrackPrompt() }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CouiBodyText(
                    text = "随时追踪最新连载漫画更新，不错过每一集精彩内容",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .couiPressable(minTouchTarget = CouiMinTouchTarget) {
                            trackMuteChecked = !trackMuteChecked
                        }
                        .padding(vertical = 8.dp),
                ) {
                    // 库复选框只画框，点击语义与热区由这一行自己提供
                    Checkbox(checked = trackMuteChecked)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "3天不要显示", kind = CouiTextKind.ItemTitle)
                }
            }
        }
    }

    // 页码与预取共用一次订阅：纵向跟 LazyColumn、横向跟 Pager，只差请求尺寸；
    // 该发哪几页由 advancePrefetch 统一判定（页码收敛与去重都在那里），collectLatest 让快速滚动只处理最新页
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
            val next = advancePrefetch(pagePlan, first, prefetched, ahead = prefetchPages)
            pagePlan = next.plan
            for (index in next.pages) {
                prefetched += index
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

/** HUD 外间距常量：底栏、顶栏与 Snackbar 避让共用同一组数值。 */
internal const val ReaderHudBarVerticalMarginDp = 8
internal val ReaderHudBarVerticalMargin = ReaderHudBarVerticalMarginDp.dp

/** 顶栏最小高度：只放返回、标题、页码与上下话，字号放大时可随内容撑开。 */
internal val ReaderHudTopHeight = 56.dp

/** 底栏最小高度：图标 22dp 加一行说明文字。 */
internal const val ReaderHudBottomHeightDp = 64
internal val ReaderHudBottomHeight = ReaderHudBottomHeightDp.dp

/** 降级底栏的不透明度：太实会挡住正文，太虚又读不清栏内文字。 */
private const val ReaderHudFallbackAlpha = 0.92f

/** 创建带实色背景的 HUD 采样源：防止页边缘留白或加载态露出透明像素。 */
@Composable
internal fun rememberCouiReaderBackdrop(diagnosticName: String): CouiBackdrop {
    val pageBackground = CouiKitTheme.colorScheme.background
    return rememberCouiBackdrop(
        diagnosticName = diagnosticName,
        drawSource = {
            drawRect(pageBackground)
            drawContent()
        },
    )
}

/** 绑定阅读器窗口级亮度与常亮租约：由宿主窗口消费，按引用计数安全释放。 */
@Composable
internal fun BindCouiReaderWindowEffects(
    keepScreenOn: Boolean,
    readerBrightness: Int,
) {
    val activityContext = LocalContext.current
    val activity = remember(activityContext) { activityContext.findActivity() }
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
}

/** 漫画与小说阅读器共用的顶部渐变模糊承托：覆盖状态栏到浮栏下缘，消除内容硬切。 */
@Composable
internal fun BoxScope.ReaderTopAppBarSurface(
    visible: Boolean,
    backdrop: CouiBackdrop,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val dark = CouiKitTheme.isDark
    val background = CouiKitTheme.colorScheme.background
    TopAppBarSurface(
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        backdrop = backdrop,
        fadeHeight = CouiTopAppBarDefaults.fadeHeight,
        bottomExtension = CouiTopAppBarDefaults.bottomExtension,
        gradientSpeed = 3f,
        gradientColor = background.copy(alpha = if (dark) 0.55f else 0.78f),
        dark = dark,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = ReaderHudBarVerticalMargin),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * HUD 顶/底栏的统一胶囊外壳：API 34 起走库的浮动表面（透镜折射、投影与描边一次给全），
 * 更低版本拿不到那套材质，退化成投影加半透明胶囊：容器色叠 0.92 不透明度，背后正文仍能透出来。
 * 两条路径以 [height] 作为最小高度，字体放大或标题两行时可自然撑开而不裁剪。
 */
@Composable
internal fun ReaderHudBar(
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
    height: Dp,
    placement: CouiReaderHudPlacement = CouiReaderHudPlacement.BOTTOM,
    diagnosticName: String,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
        ReaderHudFloatingBar(
            backdrop = backdrop,
            modifier = modifier,
            height = height,
            placement = placement,
            diagnosticName = diagnosticName,
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .heightIn(min = height)
                // 探针只在 CouiRenderDiagnostics 开启时生效，生产环境是空操作
                .renderDiagnosticProbe(diagnosticName)
                .dropShadow(
                    shape = CouiCapsuleShape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = if (CouiKitTheme.isDark) 0.5f else 0.16f,
                    ),
                )
                .clip(CouiCapsuleShape)
                .background(CouiKitTheme.colorScheme.container.copy(alpha = ReaderHudFallbackAlpha))
                // 低版本拿不到库材质，但凝光门槛更低：垫完底色就补光，与 34+ 的观感差距只剩折射
                .couiSpotlightLight(SpotlightPreset.TranslucentLarge1, interactive = true),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@RequiresApi(34)
@Composable
private fun ReaderHudFloatingBar(
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
    height: Dp,
    placement: CouiReaderHudPlacement,
    diagnosticName: String,
    content: @Composable () -> Unit,
) {
    val dark = CouiKitTheme.isDark
    val contourGlowEnabled = LocalCouiContourGlowEnabled.current
    val density = LocalDensity.current
    val contentLayer = rememberGraphicsLayer()
    // 一次只画一条栏，单主体渲染器就够；两处栏各自持有实例，互不共享坐标
    val renderer = remember { CouiFloatingRenderer.standalone() }
    val material = remember(dark, density) { with(density) { readerHudMaterial(dark) } }
    val shadow = remember(placement, dark, contourGlowEnabled, density) {
        with(density) { readerHudShadow(placement, dark, contourGlowEnabled) }
    }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    // 库的浮动材质本身不带凝光：光只能在 beforeContent 里补，那里是「材质之后、内容之前」，
    // 输入挂在外层并在 couiFloatingSurface 之前测量，外层不裁切阴影与前景合成区域
    val light = rememberCouiSpotlight(
        interactive = true,
        preset = SpotlightPreset.TranslucentLarge1,
        dark = dark,
    )

    Box(
        modifier = modifier
            .heightIn(min = height)
            .renderDiagnosticProbe(diagnosticName)
            .onSizeChanged { bounds = it }
            .then(light.first)
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = CouiCapsuleShape,
                contentLayer = contentLayer,
                contourGlowEnabled = contourGlowEnabled,
                foregroundComposition = true,
                beforeContent = { path ->
                    clipPath(path) { light.second?.invoke(this) }
                },
                surface = {
                    CouiFloatingSurface(
                        bounds = Rect(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat()),
                        cornerWeight = 1f,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 内层负责把内容录进图层並按胶囊裁剪，浮动表面才有透明前景可以折射叠加
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(CouiCapsuleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** 浮动表面的材质：深浅两套参数与 4dp 折射外扩取自库底栏，保证与首页底栏同质。 */
private fun Density.readerHudMaterial(dark: Boolean): CouiFloatingMaterial = if (dark) {
    CouiFloatingMaterial(
        firstColor = Color(0x80666666),
        firstBlendMode = BlendMode.OVERLAY,
        secondColor = Color(0xB3262626),
        edgeAlpha = 0.8f,
        innerGlowAlpha = 0.03f,
        extension = 4.dp.roundToPx(),
    )
} else {
    CouiFloatingMaterial(
        firstColor = Color(0xA3666666),
        firstBlendMode = BlendMode.COLOR_DODGE,
        secondColor = Color(0x80F0F0F0),
        edgeAlpha = 0.25f,
        innerGlowAlpha = 0.1f,
        extension = 4.dp.roundToPx(),
    )
}

/** 浮动表面的投影与焦散：按顶/底区分配置，顶栏关闭白色底晕，底栏复用 NavigationBar 胶囊焦散比例。 */
private fun Density.readerHudShadow(
    placement: CouiReaderHudPlacement,
    dark: Boolean,
    contourGlowEnabled: Boolean,
): CouiFloatingShadow {
    val spec = couiReaderHudShadowSpec(placement, dark, contourGlowEnabled)
    fun pxSize(dp: Float): Float {
        val px = dp * density
        return (if (px >= 0f) px + 0.5f else px - 0.5f).toInt().toFloat()
    }
    fun pxOffset(dp: Float): Float = (dp * density).toInt().toFloat()
    return CouiFloatingShadow(
        elevation = pxSize(spec.elevationDp),
        color = Color.Black.copy(alpha = spec.shadowAlpha),
        lightY = pxSize(spec.lightYDp),
        lightZ = if (spec.lightZDp == 0f) 0f else pxSize(spec.lightZDp),
        lightRadius = pxSize(spec.lightRadiusDp),
        causticColor = if (spec.causticAlpha > 0f) {
            Color.White.copy(alpha = spec.causticAlpha)
        } else {
            Color.Transparent
        },
        layout = Rect(0f, 0f, 0f, pxOffset(spec.causticBottomDp)),
        sdf = pxOffset(spec.causticSdfDp),
        smoothCorner = spec.smoothCorner,
    )
}

/** 顶栏的上一话/下一话：图标带极简文本标签以区分左侧返回键，无相邻章时置灰并保持 48dp 触区。 */
@Composable
internal fun ReaderHudChapterStep(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        CouiKitTheme.colorScheme.onSurface
    } else {
        CouiKitTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    Column(
        modifier = Modifier
            // 所在 HUD 面板本身已有凝光，这里不再叠第二层；只保留按压反馈
            .couiPressable(
                shape = CouiCapsuleShape,
                enabled = enabled,
                spotlight = null,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            text = label,
            kind = CouiTextKind.GroupFooter,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 跳页弹窗加减微调按钮：视觉保持 32dp 圆形胶囊，外侧补足 48dp 触区。 */
@Composable
private fun ReaderPageJumpStepper(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.container,
                enabled = enabled,
                spotlight = if (enabled) SpotlightPreset.OpaqueSmall2 else null,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

/** 底栏动作：等分宽胶囊触区 + 选中态横向胶囊底衬。 */
@Composable
internal fun ReaderHudAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.3f)
        active -> scheme.accent
        else -> scheme.onSurface
    }
    val containerColor = if (active && enabled) {
        scheme.accent.copy(alpha = CouiSelectedContainerAlpha)
    } else {
        Color.Transparent
    }
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            // 只在 HUD 面板内使用，光由面板统一给，项内不再叠第二层
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = containerColor,
                enabled = enabled,
                spotlight = null,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tint)
        Text(
            text = label,
            kind = CouiTextKind.GroupFooter,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
        )
    }
}

/** 弹窗内部提示条：避免在打开 CouiDialogWindow 时提示被主窗口下方的 Snackbar 挡住。 */
@Composable
internal fun ReaderDialogNotice(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        kind = CouiTextKind.GroupFooter,
        color = CouiKitTheme.colorScheme.accentOnSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** 评论行：头像 + 作者 + 时间 + 正文 + 回复入口，回复入口只在正文下方占一行。 */
@Composable
internal fun ReaderCommentRow(item: CommentItem, onReply: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    CouiCommentRow(
        item = item,
        authorColor = scheme.accentOnSurface,
        onReply = { onReply() },
    )
}

/** 评论输入区：未登录只给登录入口，登录后展示内联提示、输入框加字数与发表。 */
@Composable
internal fun ReaderCommentComposer(
    loggedIn: Boolean,
    text: String,
    busy: Boolean,
    notice: String? = null,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSend: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    ReaderDialogNotice(notice)
    if (!loggedIn) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .couiPressable(spotlight = null, minTouchTarget = CouiMinTouchTarget) { onLogin() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "登录后参与评论", kind = CouiTextKind.ItemTitle, color = scheme.accent)
        }
        return
    }
    // 空内容时输入框自身没有占位能力，这里叠一层说明文字当占位
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CouiCommentFieldShape)
            .background(scheme.container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (text.isEmpty()) {
            CouiBodyText(text = "说点什么…", color = scheme.onSurfaceVariant)
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.length.toString() + "/" + COMMENT_REPLY_MAX_LENGTH,
            kind = CouiTextKind.GroupFooter,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val enabled = !busy && text.isNotBlank()
        // 底色经 backgroundColor 交给 couiPressable，确保 48dp 触区不把可见胶囊撑大
        Box(
            modifier = Modifier
                .couiPressable(
                    shape = CouiCapsuleShape,
                    backgroundColor = if (enabled) scheme.accent else scheme.outlineVariant,
                    spotlight = if (enabled) SpotlightPreset.OpaqueMedium1 else null,
                    enabled = enabled,
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = onSend,
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (busy) "发送中…" else "发表",
                kind = CouiTextKind.ItemValue,
                color = if (enabled) scheme.onAccent else scheme.onSurfaceVariant,
            )
        }
    }
}

/** 评论弹窗的加载/失败/空态占位：失败时整行可点重试。 */
@Composable
internal fun ReaderDialogHint(text: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CouiBodyText(
            text = text,
            color = CouiKitTheme.colorScheme.onSurfaceVariant,
            modifier = if (onClick != null) {
                Modifier.couiPressable(minTouchTarget = CouiMinTouchTarget) { onClick() }.padding(vertical = 8.dp)
            } else {
                Modifier
            },
        )
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
                .zoomable(state = zoomableState, enabled = true, onClick = tapHandler)
                .pointerInput(zoomableState) {
                    collectHorizontalEdgeDrag(
                        contentOffsetX = { zoomableState.contentTransformation.offset.x },
                        onEdgeDrag = { dragX, stillPinned -> edgeDrag.value?.invoke(dragX, stillPinned) },
                    )
                }
        } else {
            modifier.zoomable(state = zoomableState, enabled = true, onClick = tapHandler)
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
            // 失败重试卡片叠在图像组件之上，否则全屏 ZoomableAsyncImage 会吞掉点击手势
            if (failed) ReaderLoadFailedHint { failed = false; retryKey++ }
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
        onTap != null -> Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap,
        )
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
    // 深色半透明底衬：页面本身可能是黑底长图，提示需要自己拉开对比
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(CouiCommentFieldShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CouiBodyText(text = "图片加载失败", color = Color.White)
        Text(
            text = CouiStrings.Retry,
            kind = CouiTextKind.ItemTitle,
            color = CouiKitTheme.colorScheme.accent,
            modifier = Modifier
                .padding(top = 8.dp)
                .couiPressable(minTouchTarget = CouiMinTouchTarget) { onRetry() }
                .padding(vertical = 4.dp),
        )
    }
}

package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.size.Size
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.reader.ReaderActions
import com.jm3.app.miuix.reader.ReaderSession
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.rememberBlurBackdrop
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.Chapter
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.reader.stepPages
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 阅读页本体：持有一屏内的全部页面级状态，并按原顺序装配副作用、页面容器与各浮层。 */
@Composable
internal fun ReaderPages(
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
    // 页宽取「窗口宽」与正文列宽的较小值：竖屏手机两者相同，1280dp 横屏窗口里正文列是 1120dp，
    // 横向单页因此收成居中的阅读列，而不是拉满整屏只留左右各 80dp 边。
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
    var pickerValue by rememberSaveable { mutableIntStateOf(1) }
    // 原版点击正文切换上下栏显隐
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var lastVolumeAt by remember { mutableLongStateOf(0L) }
    val engagement = rememberReaderEngagement(
        deps = deps,
        albumId = photo.albumId,
        scope = scope,
        snackbarHostState = snackbarHostState,
        loggedIn = user != null,
        onOpenLogin = onOpenLogin,
        favoritedInit = favoritedInit,
        likedInit = likedInit,
    )
    val commentSheet = rememberReaderComments(
        deps = deps,
        albumId = photo.albumId,
        scope = scope,
        snackbarHostState = snackbarHostState,
        loggedIn = user != null,
        onOpenLogin = onOpenLogin,
    )
    val track = rememberReaderTrackPrompt(
        deps = deps,
        albumId = photo.albumId,
        photoId = photo.photoId,
        scope = scope,
        settings = settings,
        snackbarHostState = snackbarHostState,
        loggedIn = user != null,
        onOpenLogin = onOpenLogin,
    )

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

    ReaderSettingsLoadEffect(
        settings = settings,
        snackbarHostState = snackbarHostState,
        snackbarQueue = snackbarQueue,
        onHorizontal = { horizontal = it },
        onSpread = { spreadMode = it },
        onHorizontalReady = { horizontalReady = true },
        onVolumeKeyPaging = { volumeKeyPaging = it },
        onKeepScreenOn = { keepScreenOn = it },
        onBrightness = { readerBrightness = it },
        onImageQuality = { imageQuality = it },
        onPrefetchPages = { prefetchPages = it },
    )

    val readerWindow = activity?.window
    ReaderWindowEffect(
        readerWindow = readerWindow,
        readerBrightness = readerBrightness,
        keepScreenOn = keepScreenOn,
    )

    // 翻页与进度落盘的编排收在 reader/ReaderActions：两套皮肤共用同一份，避免夹取或落地顺序各写一份。
    // 每次重组重建实例，读到的是当前的 spreadActive / horizontal / pagerPageCount。
    val actions = ReaderActions(
        session = session,
        pagerState = pagerState,
        listState = listState,
        scope = scope,
        pageCount = photo.images.size,
        pagerPageCount = pagerPageCount,
        spreadActive = spreadActive,
        horizontal = horizontal,
        settings = settings,
        photoId = photo.photoId,
    )

    ReaderProgressRestoreEffect(
        horizontalReady = horizontalReady,
        photo = photo,
        startPage = startPage,
        spreadActive = spreadActive,
        currentPage = { pagePlan.currentPage },
        session = session,
        actions = actions,
        settings = settings,
        snackbarHostState = snackbarHostState,
        snackbarQueue = snackbarQueue,
    )

    ReaderProgressSaveEffect(
        currentPage = pagePlan.currentPage,
        pageCount = photo.images.size,
        progressRestored = progressRestored,
        session = session,
        actions = actions,
    )

    ReaderProgressExitEffect(
        photoId = photo.photoId,
        restored = { progressRestored },
        latestPage = { latestPage },
        totalPages = photo.images.size,
        actions = actions,
        appScope = deps.appScope,
    )
    // 评论抽屉展开态跨旋转保留，但评论列表本身不可序列化：旋转恢复后若已展开且数据为空则补拉一次
    ReaderCommentRefetchEffect(
        showCommentSheet = commentSheet.show.value,
        albumId = photo.albumId,
        hasComments = { commentSheet.comments != null },
        commentsLoading = commentSheet.loading,
        commentsFailed = commentSheet.failed,
        commentsExpired = commentSheet.expired,
        onLoad = { commentSheet.load() },
    )

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

    // 音量键翻页：150ms 节流，与原生桥一致。
    // ReaderVolumeKeys.handler 是进程级单值，换章时新旧屏会短暂同时挂载，
    // 所以必须记住自己写入的实例，否则旧屏 onDispose 会把新屏刚写进去的 handler 清成 null。
    // 翻页动作走 rememberUpdatedState：DisposableEffect 只注册一次，闭包里的 spreadActive/
    // photo 若直接捕获会在页内切换方向或换章后过期，音量键就会走进错误分支。
    ReaderVolumeKeyEffect(
        volumeKeyPaging = volumeKeyPaging,
        zoomOverlayOpen = { zoomOverlayIndex != null },
        volumeStepAt = { lastVolumeAt },
        onVolumeStepAt = { lastVolumeAt = it },
        onStep = { delta ->
            // 双页下按屏步进：音量键一次翻一屏，不会因为锚点相同而卡住
            actions.goToPage(
                if (spreadActive) {
                    ReaderSpreadPolicy.stepAnchor(pagePlan.currentPage, delta, photo.images.size)
                } else {
                    stepPages(pagePlan, delta)
                },
            )
        },
    )

    // 最后一话最后一页弹追更引导；勾选静默后三天内不再出现
    ReaderTrackPromptEffect(
        currentPage = { pagePlan.currentPage },
        pageCount = photo.images.size,
        spreadActive = spreadActive,
        trackPromptShown = { track.shown.value },
        onTrackPromptShown = { track.shown.value = true },
        chapters = chapters,
        chapterIndex = chapterIndex,
        settings = settings,
        onShowPrompt = { track.show.value = true },
    )

    GlassScaffold(
        topBar = {
            ReaderTopBar(
                title = photo.title,
                pageLabel = pageLabel,
                visible = chromeVisible,
                prevChapter = prevChapter,
                nextChapter = nextChapter,
                onBack = onBack,
                onOpenChapter = onOpenChapter,
                onOpenPagePicker = {
                    pickerValue = pagePlan.currentPage
                    showPageSheet = true
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        ReaderPageArea(
            photo = photo,
            imageLoader = imageLoader,
            pagerState = pagerState,
            pagerPageCount = pagerPageCount,
            listState = listState,
            contentPadding = innerPadding,
            pageSidePadding = pageSidePadding,
            pageWidthPx = pageWidthPx,
            spreadActive = spreadActive,
            spreadPageSize = spreadPageSize,
            screenSize = screenSize,
            pageZoomed = pageZoomed,
            horizontal = horizontal,
            chromeVisible = chromeVisible,
            favorited = engagement.favorited,
            liked = engagement.liked,
            actions = actions,
            pageRatios = pageRatios,
            decodeRatios = decodeRatios,
            chapterRatioSeed = chapterRatioSeed,
            imageQuality = imageQuality,
            toolbarBackdrop = toolbarBackdrop,
            zoomOverlayIndex = zoomOverlayIndex,
            onToggleChrome = { chromeVisible = !chromeVisible },
            onPageZoomedChange = { pageZoomed = it },
            onChapterSeedChange = { chapterRatioSeed = it },
            onOpenZoom = { zoomOverlayIndex = it },
            onZoomDismiss = { zoomOverlayIndex = null },
            onToggleMode = { switchMode(!horizontal) },
            onChapters = { showChapterSheet = true },
            onComments = { commentSheet.open() },
            onFavorite = { engagement.toggleFavorite() },
            onLike = { engagement.toggleLike() },
        )

        ReaderOverlays(
            showPageSheet = showPageSheet,
            pickerValue = pickerValue,
            totalPages = totalPages,
            showChapterSheet = showChapterSheet,
            chapters = chapters,
            currentPhotoId = photo.photoId,
            showCommentSheet = commentSheet.show.value,
            comments = commentSheet.comments,
            commentsLoading = commentSheet.loading,
            commentsFailed = commentSheet.failed,
            commentsExpired = commentSheet.expired,
            loggedIn = user != null,
            commentText = commentSheet.text.value,
            commentBusy = commentSheet.busy,
            replyTarget = commentSheet.replyTarget.value,
            replyBusy = commentSheet.replyBusy,
            showTrackPrompt = track.show.value,
            trackMuteChecked = track.muteChecked,
            trackBusy = track.busy,
            sheetCorner = skin.shapes.sheetCorner,
            dialogCorner = skin.shapes.dialogCorner,
            onPickerValueChange = { pickerValue = it },
            onDismissPageSheet = { showPageSheet = false },
            onJumpToPage = { actions.goToPage(pickerValue) },
            onDismissChapterSheet = { showChapterSheet = false },
            onOpenChapter = onOpenChapter,
            onDismissComments = { commentSheet.show.value = false },
            onOpenLogin = onOpenLogin,
            onRetryComments = { commentSheet.load() },
            onReply = { commentSheet.replyTarget.value = it },
            onCommentTextChange = { if (it.length <= COMMENT_REPLY_MAX_LENGTH) commentSheet.text.value = it },
            onSendComment = { commentSheet.send() },
            onDismissReply = { commentSheet.replyTarget.value = null },
            onSendReply = { commentSheet.reply(it) },
            onTrackMuteCheckedChange = { track.muteChecked = it },
            onDismissTrackPrompt = { track.close() },
            onFollowSeries = { track.follow() },
        )
    }

    ReaderPrefetchEffect(
        listState = listState,
        pagerState = pagerState,
        photo = photo,
        imageLoader = imageLoader,
        pageWidthPx = pageWidthPx,
        screenSize = screenSize,
        spreadActive = spreadActive,
        spreadPageSize = spreadPageSize,
        horizontal = horizontal,
        imageQuality = imageQuality,
        prefetchPages = prefetchPages,
        session = session,
        context = context,
        pageRatios = pageRatios,
        decodeRatios = decodeRatios,
        chapterRatioSeed = { chapterRatioSeed },
    )
}

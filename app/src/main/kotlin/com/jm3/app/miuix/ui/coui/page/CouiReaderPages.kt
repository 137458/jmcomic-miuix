package com.jm3.app.miuix.ui.coui.page

import android.app.Activity
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil3.size.Size
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.dialog.Dialog
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.reader.ReaderActions
import com.jm3.app.miuix.reader.ReaderSession
import com.jm3.app.miuix.ui.CommentItemNullableSaver
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.ReaderVolumeKeys
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.notifyAction
import com.jm3.app.miuix.ui.readerScreenDecodeSize
import com.jm3.app.miuix.ui.readerSpreadPageDecodeSize
import com.jm3.app.miuix.ui.rememberReaderImageLoader
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.reader.readerProgressOnExit
import com.jm3.core.reader.stepPages
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 阅读进度落盘前的静置时长：翻页停下来才写，避免连滚时逐页写设置存储。 */
private const val PROGRESS_SAVE_DEBOUNCE_MS = 400L
private const val TRACK_MUTE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

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
    val density = LocalDensity.current
    val imageLoader = rememberReaderImageLoader(deps.gateway, deps.downloadFiles, deps.authCall)
    val listState = rememberLazyListState()
    val windowSize = LocalWindowInfo.current.containerSize
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    val wideLayout = LocalWideLayout.current
    val containerWidthPx = windowSize.width.coerceAtLeast(1)
    val readablePaddingPx = with(density) { wideLayout.readableHorizontalPadding.roundToPx() }
    // 竖向按扣减宽屏左右可读留白后的实际页宽推算页框与解码尺寸；
    // 横向 pager 铺满窗口，边界拖动手势仍以窗口全宽 containerWidthPx 判定。
    val pageWidthPx = couiReaderVerticalPageWidthPx(containerWidthPx, readablePaddingPx)
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
        if (restoredPage != pagePlan.currentPage) actions.goToPage(restoredPage)
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


    // 页码一变就记进度：读到最后一页改为清掉进度键，未读完才落盘。
    // 落盘前等一下翻页停下来，避免长图连滚时把每一页都写一遍设置存储
    LaunchedEffect(pagePlan.currentPage, photo.images.size, progressRestored) {
        // 闸门未开（首帧还没恢复完）或本章无页时不写盘
        if (!session.canPersistProgress) return@LaunchedEffect
        delay(PROGRESS_SAVE_DEBOUNCE_MS)
        val pending = session.progressToSave() ?: return@LaunchedEffect
        actions.persistProgress(pending)
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
                actions.persistProgress(progress)
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
            actions.goToPage(
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
            ReaderPageContent(
                photo = photo,
                imageLoader = imageLoader,
                geometry = CouiReaderPageGeometry(
                    containerWidthPx = containerWidthPx,
                    pageWidthPx = pageWidthPx,
                    screenSize = screenSize,
                    spreadPageSize = spreadPageSize,
                ),
                horizontal = horizontal,
                spreadActive = spreadActive,
                pageZoomed = pageZoomed,
                pagerState = pagerState,
                pagerPageCount = pagerPageCount,
                listState = listState,
                session = session,
                actions = actions,
                imageQuality = imageQuality,
                prefetchPages = prefetchPages,
                onPageZoomed = { pageZoomed = it },
                onToggleChrome = { chromeVisible = !chromeVisible },
                onOpenZoomOverlay = { zoomOverlayIndex = it },
            )
        }

        ReaderTopHudBar(
            visible = chromeVisible,
            backdrop = hudBackdrop,
            title = photo.title,
            pageLabel = pageLabel,
            accent = scheme.accent,
            onBack = onBack,
            onOpenPageJump = {
                pickerValue = pagePlan.currentPage
                modalState = modalState.open(CouiReaderModal.PAGE_JUMP)
            },
            prevChapter = prevChapter,
            nextChapter = nextChapter,
            onOpenChapter = onOpenChapter,
        )

        // HUD 底栏：等分槽位匹配 CouiCapsuleShape 横向胶囊指示器与触区
        ReaderBottomHudBar(
            visible = chromeVisible,
            backdrop = hudBackdrop,
            horizontal = horizontal,
            favorited = favorited,
            liked = liked,
            onToggleMode = { switchMode(!horizontal) },
            onOpenChapters = { modalState = modalState.open(CouiReaderModal.CHAPTERS) },
            onOpenComments = ::openComments,
            onToggleFavorite = ::toggleFavorite,
            onToggleLike = ::toggleLike,
        )

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
            ReaderZoomOverlay(
                image = overlayImage,
                imageLoader = imageLoader,
                decodeSize = screenSize,
                onDismiss = { zoomOverlayIndex = null },
            )
        }

        val visibleModal = couiVisibleReaderModal(modalState, hasReplyTarget = replyTarget != null)

        // 跳页：滑杆搭配加减微调按钮，百页以上长章节也能精准选定目标页
        ReaderPageJumpDialog(
            visible = visibleModal == CouiReaderModal.PAGE_JUMP,
            pickerValue = pickerValue,
            totalPages = totalPages,
            onValueChange = { pickerValue = it },
            onConfirm = { actions.goToPage(pickerValue) },
            onDismiss = { modalState = modalState.dismiss() },
        )

        // 章节：列表高度封顶后自身滚动，当前话由弹窗内部标注
        ReaderChaptersDialog(
            visible = visibleModal == CouiReaderModal.CHAPTERS,
            chapters = chapters,
            currentPhotoId = photo.photoId,
            onSelect = onOpenChapter,
            onDismiss = { modalState = modalState.dismiss() },
        )

        // 评论：直接承接 Dialog 玻璃材质，不再嵌套实底 Group 割裂质感
        ReaderCommentsDialog(
            visible = visibleModal == CouiReaderModal.COMMENTS,
            comments = comments,
            loading = commentsLoading,
            failed = commentsFailed,
            expired = commentsExpired,
            loggedIn = user != null,
            text = commentText,
            busy = commentBusy,
            notice = commentNotice,
            onTextChange = { commentText = couiClampCommentInput(it) },
            onSend = ::sendComment,
            onLogin = ::openLoginFromModal,
            onReload = ::loadComments,
            onReply = { item ->
                if (user == null) {
                    openLoginFromModal()
                } else {
                    replyTarget = item
                    replyText = ""
                    replyNotice = null
                    modalState = modalState.enterReply()
                }
            },
            onDismiss = { modalState = modalState.dismiss() },
        )

        // 回复：与评论弹窗互斥；关闭时按 reopenCommentsAfterReply 回退评论弹窗
        val retainedReplyTarget = rememberRetainedTarget(replyTarget)
        ReaderReplyDialog(
            visible = replyTarget != null,
            author = retainedReplyTarget?.author,
            text = replyText,
            busy = replyBusy,
            notice = replyNotice,
            onTextChange = { replyText = couiClampCommentInput(it) },
            onSend = { sendReply(replyText) },
            onDismiss = {
                replyTarget = null
                replyNotice = null
                modalState = modalState.exitReply()
            },
        )

        // 追更引导：直接承接 Dialog 玻璃材质，动作按钮交给弹窗自身槽位
        ReaderTrackPromptDialog(
            visible = visibleModal == CouiReaderModal.TRACK_PROMPT,
            muted = trackMuteChecked,
            busy = trackBusy,
            onMutedChange = { trackMuteChecked = it },
            onFollow = ::followSeries,
            onDismiss = { closeTrackPrompt() },
        )
    }
}
package com.jm3.app.miuix.ui

import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.jm3.app.miuix.reader.ReaderActions
import com.jm3.app.miuix.reader.ReaderSession
import com.jm3.core.model.Chapter
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.readerProgressOnExit
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/*
 * 阅读器的副作用组：首载设置、窗口亮度与常亮、进度恢复/落盘/离场补记、音量键分发、
 * 末页追更引导、评论补拉与页面预取。
 *
 * 这里只保留「什么时候做什么」：状态仍由 ReaderPages 持有，协程内活读的值一律以取值函数
 * 传入，避免把推荐重组时的旧值带进已经启动的协程。
 */

/** 首载后从设置存储恢复阅读器偏好；音量键提示与恢复提示共用 [snackbarQueue] 排队。 */
@Composable
internal fun ReaderSettingsLoadEffect(
    settings: ReaderSettings,
    snackbarHostState: SnackbarHostState,
    snackbarQueue: Mutex,
    onHorizontal: (Boolean) -> Unit,
    onSpread: (ReaderSpreadMode) -> Unit,
    onHorizontalReady: () -> Unit,
    onVolumeKeyPaging: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onImageQuality: (ReaderImageQuality) -> Unit,
    onPrefetchPages: (Int) -> Unit,
) {
    LaunchedEffect(Unit) {
        onHorizontal(settings.readerHorizontal())
        onSpread(settings.readerSpread())
        // 纵横模式决定跳页走 pager 还是列表，恢复进度必须等它落定
        onHorizontalReady()
        val volumeKeyPaging = settings.readerVolumeKeyPaging()
        onVolumeKeyPaging(volumeKeyPaging)
        onKeepScreenOn(settings.readerKeepScreenOn())
        onBrightness(settings.readerBrightness())
        onImageQuality(settings.readerImageQuality())
        onPrefetchPages(settings.readerPrefetchPages())
        if (volumeKeyPaging && !settings.volumeKeyHintShown()) {
            delay(1000)
            snackbarQueue.withLock {
                snackbarHostState.showSnackbar("可使用音量键＋翻页")
            }
            settings.markVolumeKeyHintShown()
        }
    }
}

/** 阅读亮度与常亮作用在宿主 Activity 的窗口上：进入页面按设置应用，离开时恢复原值。 */
@Composable
internal fun ReaderWindowEffect(
    readerWindow: Window?,
    readerBrightness: Int,
    keepScreenOn: Boolean,
) {
    // 阅读亮度作用在 Activity 窗口上：进入页面按设置应用，离开时恢复原值。
    // 换章时新旧页短暂并存，原亮度只由首个页面记录、最后一个页面恢复，否则旧页销毁会抹掉新页的亮度。
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
}

/** 恢复上次读到的页码；[currentPage] 取当前计划页码，供跳转前比较。 */
@Composable
internal fun ReaderProgressRestoreEffect(
    horizontalReady: Boolean,
    photo: PhotoPages,
    startPage: Int?,
    spreadActive: Boolean,
    currentPage: () -> Int,
    session: ReaderSession,
    actions: ReaderActions,
    settings: ReaderSettings,
    snackbarHostState: SnackbarHostState,
    snackbarQueue: Mutex,
) {
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
        if (restoredPage != currentPage()) actions.goToPage(restoredPage)
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
}

/** 页码一变就记进度；[currentPage] 与 [pageCount] 同时参与重跑这个副作用的 key。 */
@Composable
internal fun ReaderProgressSaveEffect(
    currentPage: Int,
    pageCount: Int,
    progressRestored: Boolean,
    session: ReaderSession,
    actions: ReaderActions,
) {
    // 页码一变就记进度：读到最后一页改为清掉进度键，未读完才落盘。
    // 落盘前等一下翻页停下来，避免长图连滚时把每一页都写一遍设置存储
    LaunchedEffect(currentPage, pageCount, progressRestored) {
        // 闸门未开（首帧还没恢复完）或本章无页时不写盘
        if (!session.canPersistProgress) return@LaunchedEffect
        delay(PROGRESS_SAVE_DEBOUNCE_MS)
        val pending = session.progressToSave() ?: return@LaunchedEffect
        actions.persistProgress(pending)
    }
}

/** 离开阅读器时补记当前页；[restored] 与 [latestPage] 在 onDispose 时才取值。 */
@Composable
internal fun ReaderProgressExitEffect(
    photoId: String,
    restored: () -> Boolean,
    latestPage: () -> Int,
    totalPages: Int,
    actions: ReaderActions,
    appScope: CoroutineScope,
) {
    // 离开阅读器时补记当前页：此时协程作用域已取消，挂在进程作用域上才写得完。
    // 恢复流程未完成时首帧页码还是第 1 页，此时不能写盘，否则会覆盖尚未读取的旧进度。
    DisposableEffect(photoId) {
        onDispose {
            val progress = readerProgressOnExit(
                restored = restored(),
                page = latestPage(),
                total = totalPages,
            ) ?: return@onDispose
            appScope.launch {
                actions.persistProgress(progress)
            }
        }
    }
}

/** 音量键翻页；覆盖层是否打开与节流时间点分别由 [zoomOverlayOpen]、[volumeStepAt] 现取。 */
@Composable
internal fun ReaderVolumeKeyEffect(
    volumeKeyPaging: Boolean,
    zoomOverlayOpen: () -> Boolean,
    volumeStepAt: () -> Long,
    onVolumeStepAt: (Long) -> Unit,
    onStep: (Int) -> Unit,
) {
    // 音量键翻页：150ms 节流，与原生桥一致。
    // ReaderVolumeKeys.handler 是进程级单值，换章时新旧屏会短暂同时挂载，
    // 所以必须记住自己写入的实例，否则旧屏 onDispose 会把新屏刚写进去的 handler 清成 null。
    // 翻页动作走 rememberUpdatedState：DisposableEffect 只注册一次，闭包里的 spreadActive/
    // photo 若直接捕获会在页内切换方向或换章后过期，音量键就会走进错误分支。
    val step by rememberUpdatedState(onStep)
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
                zoomOverlayOpen() -> false
                else -> {
                    val now = System.currentTimeMillis()
                    if (now - volumeStepAt() >= VOLUME_THROTTLE_MS) {
                        onVolumeStepAt(now)
                        step(delta)
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
}

/** 末页追更引导；[currentPage] 与 [trackPromptShown] 在协程内活读。 */
@Composable
internal fun ReaderTrackPromptEffect(
    currentPage: () -> Int,
    pageCount: Int,
    spreadActive: Boolean,
    trackPromptShown: () -> Boolean,
    onTrackPromptShown: () -> Unit,
    chapters: List<Chapter>,
    chapterIndex: Int,
    settings: ReaderSettings,
    onShowPrompt: () -> Unit,
) {
    // 最后一话最后一页弹追更引导；勾选静默后三天内不再出现
    LaunchedEffect(currentPage(), pageCount, spreadActive) {
        if (trackPromptShown() || pageCount == 0) return@LaunchedEffect
        if (chapters.isEmpty() || chapterIndex != chapters.lastIndex) return@LaunchedEffect
        // 双页下当前页记的是末屏锚点，判断「已到末页」要看是否落在最后那一屏
        val atLastPage = if (spreadActive) {
            val total = pageCount
            ReaderSpreadPolicy.spreadIndexForPage(currentPage(), total) ==
                ReaderSpreadPolicy.spreadCount(total) - 1
        } else {
            currentPage() == pageCount
        }
        if (!atLastPage) return@LaunchedEffect
        if (System.currentTimeMillis() < settings.trackPromptMuteUntil()) return@LaunchedEffect
        onTrackPromptShown()
        onShowPrompt()
    }
}

/** 评论抽屉展开态跨旋转保留，但评论列表本身不可序列化：旋转恢复后若已展开且数据为空则补拉一次。 */
@Composable
internal fun ReaderCommentRefetchEffect(
    showCommentSheet: Boolean,
    albumId: String,
    hasComments: () -> Boolean,
    commentsLoading: Boolean,
    commentsFailed: Boolean,
    commentsExpired: Boolean,
    onLoad: () -> Unit,
) {
    // 评论抽屉展开态跨旋转保留，但评论列表本身不可序列化：旋转恢复后若已展开且数据为空则补拉一次
    LaunchedEffect(showCommentSheet, albumId) {
        if (showCommentSheet && !hasComments() && !commentsLoading && !commentsFailed && !commentsExpired) onLoad()
    }
}

/** 页面预取：页码回写与「该发哪几页」由 session 判定，屏幕只按展示同源尺寸入队。 */
@Composable
internal fun ReaderPrefetchEffect(
    listState: LazyListState,
    pagerState: PagerState,
    photo: PhotoPages,
    imageLoader: ImageLoader,
    pageWidthPx: Int,
    screenSize: Size,
    spreadActive: Boolean,
    spreadPageSize: Size,
    horizontal: Boolean,
    imageQuality: ReaderImageQuality,
    prefetchPages: Int,
    session: ReaderSession,
    context: PlatformContext,
    pageRatios: Map<Int, Float>,
    decodeRatios: Map<Int, Float>,
    chapterRatioSeed: () -> Float?,
) {
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
                        chapterSeed = chapterRatioSeed(),
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


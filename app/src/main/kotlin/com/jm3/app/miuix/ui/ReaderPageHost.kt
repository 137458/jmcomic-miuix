package com.jm3.app.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.jm3.app.miuix.reader.collectHorizontalEdgeDrag
import com.jm3.app.miuix.reader.ReaderActions
import com.jm3.app.miuix.reader.ReaderSpreadPage
import com.jm3.app.miuix.ui.components.MinTouchTarget
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.settings.ReaderImageQuality
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

/**
 * 阅读器页面宿主：横向 pager、纵向长列表、长图整屏缩放层，以及单页渲染本身。
 *
 * 两套排布共用同一套页框解码逻辑（[readerPageFrame]）与同一张 [ReaderPage]，
 * 差异只在「谁来承载滚动」与「一屏放几页」，因此收进同一文件。
 */

/** 纯函数算出的解码像素转成 Coil 的 Size；预取与展示共用这一处，内存缓存键才一致。 */
internal fun IntSize.toCoilSize(): Size = Size(width, height)

/**
 * 横向翻页宿主：单页/双页共用一条 [HorizontalPager]，手势由 [pagerGestureOverride] 驱动。
 * 放大态（[pageZoomed]）让出翻页手势，交给缩放层自行消费。
 */
@Composable
internal fun ReaderHorizontalPageHost(
    photo: PhotoPages,
    imageLoader: ImageLoader,
    pagerState: PagerState,
    pagerPageCount: Int,
    pageSidePadding: Dp,
    pageWidthPx: Int,
    spreadActive: Boolean,
    spreadPageSize: Size,
    screenSize: Size,
    pageZoomed: Boolean,
    actions: ReaderActions,
    onToggleChrome: () -> Unit,
    onPageZoomedChange: (Boolean) -> Unit,
) {
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
                // 单页渲染留在本屏：失败提示与文字资源属皮肤层
                page = { image, pageModifier ->
                    ReaderPage(
                        image = image,
                        imageLoader = imageLoader,
                        decodeSize = spreadPageSize,
                        modifier = pageModifier,
                    )
                },
                modifier = Modifier.fillMaxSize(),
                isCurrentPage = index == pagerState.currentPage,
                onTap = onToggleChrome,
                onZoomChanged = { zoomed ->
                    if (index == pagerState.currentPage) onPageZoomedChange(zoomed)
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
                        if (turn != EdgePageTurn.None) actions.turnPage(turn, stillPinned)
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
                onTap = onToggleChrome,
                onZoomChanged = { zoomed ->
                    // 只有当前页的缩放状态才约束翻页，预加载的邻页不参与
                    if (index == pagerState.currentPage) onPageZoomedChange(zoomed)
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
                        if (turn != EdgePageTurn.None) actions.turnPage(turn, stillPinned)
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * 纵向长列表宿主：长图按真实比例自适应展开无缝拼接，双击交给 [onOpenZoom] 打开整屏缩放层。
 * 长列表与它的滚动条必须同层：[TrackedLazyColumn] 内部就是 Box { LazyColumn + ListScrollBar }，
 * 轨道与列表共用同一份 contentPadding；横向模式用的是 Pager，不经过这里。
 */
// ScrollBar 目前在 Miuix 标记为实验 API（warning 级别），仅此处显式表态使用
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun ReaderVerticalPageList(
    photo: PhotoPages,
    imageLoader: ImageLoader,
    listState: LazyListState,
    contentPadding: PaddingValues,
    pageWidthPx: Int,
    pageRatios: MutableMap<Int, Float>,
    decodeRatios: MutableMap<Int, Float>,
    chapterRatioSeed: Float?,
    imageQuality: ReaderImageQuality,
    onChapterSeedChange: (Float) -> Unit,
    onOpenZoom: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    // 长列表与它的滚动条必须同层：TrackedLazyColumn 内部就是 Box { LazyColumn + ListScrollBar }，
    // 轨道与列表共用同一份 contentPadding；横向模式用的是 Pager，不经过这里
    TrackedLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().readableWidth(),
        contentPadding = contentPadding,
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
                        backfill.chapterSeed?.let { onChapterSeedChange(it) }
                        backfill.decodeRatio?.let { decodeRatio ->
                            if (decodeRatios[image.index] != decodeRatio) {
                                decodeRatios[image.index] = decodeRatio
                            }
                        }
                    }
                },
                // 长图模式按图片真实比例自适应展开无缝拼接，双击打开整屏缩放层
                onDoubleTap = { onOpenZoom(index) },
                onTap = onToggleChrome,
            )
        }
        // 底部悬浮工具栏会遮住最后一页末尾，尾项净空与主屏、详情页同源取自 BottomSpacing
        item(key = "toolbar-space") { Spacer(modifier = Modifier.height(readerTailClearanceDp().dp)) }
    }
    }

/**
 * 长图模式的整屏缩放层：双击页面打开，单击或返回键退出。
 * 调用方把它放在工具栏之后，保证盖住内容与工具栏而不是被它们压住。
 */
@Composable
internal fun ReaderZoomOverlay(
    photo: PhotoPages,
    imageLoader: ImageLoader,
    screenSize: Size,
    zoomOverlayIndex: Int?,
    onDismiss: () -> Unit,
) {
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
                onTap = onDismiss,
            )
        }
        BackHandler { onDismiss() }
    }
}

@Composable
internal fun ReaderPage(
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
internal fun BoxScope.ReaderLoadFailedHint(onRetry: () -> Unit) {
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

/** 页面主体：内容层铺玻璃背景，横向走 pager、纵向走长列表，上面叠工具栏与长图缩放层。 */
@Composable
internal fun ReaderPageArea(
    photo: PhotoPages,
    imageLoader: ImageLoader,
    pagerState: PagerState,
    pagerPageCount: Int,
    listState: LazyListState,
    contentPadding: PaddingValues,
    pageSidePadding: Dp,
    pageWidthPx: Int,
    spreadActive: Boolean,
    spreadPageSize: Size,
    screenSize: Size,
    pageZoomed: Boolean,
    horizontal: Boolean,
    chromeVisible: Boolean,
    favorited: Boolean,
    liked: Boolean,
    actions: ReaderActions,
    pageRatios: MutableMap<Int, Float>,
    decodeRatios: MutableMap<Int, Float>,
    chapterRatioSeed: Float?,
    imageQuality: ReaderImageQuality,
    toolbarBackdrop: LayerBackdrop?,
    zoomOverlayIndex: Int?,
    onToggleChrome: () -> Unit,
    onPageZoomedChange: (Boolean) -> Unit,
    onChapterSeedChange: (Float) -> Unit,
    onOpenZoom: (Int) -> Unit,
    onZoomDismiss: () -> Unit,
    onToggleMode: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
) {
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
            ReaderHorizontalPageHost(
                photo = photo,
                imageLoader = imageLoader,
                pagerState = pagerState,
                pagerPageCount = pagerPageCount,
                pageSidePadding = pageSidePadding,
                pageWidthPx = pageWidthPx,
                spreadActive = spreadActive,
                spreadPageSize = spreadPageSize,
                screenSize = screenSize,
                pageZoomed = pageZoomed,
                actions = actions,
                onToggleChrome = onToggleChrome,
                onPageZoomedChange = onPageZoomedChange,
            )
        } else {
            ReaderVerticalPageList(
                photo = photo,
                imageLoader = imageLoader,
                listState = listState,
                contentPadding = contentPadding,
                pageWidthPx = pageWidthPx,
                pageRatios = pageRatios,
                decodeRatios = decodeRatios,
                chapterRatioSeed = chapterRatioSeed,
                imageQuality = imageQuality,
                onChapterSeedChange = onChapterSeedChange,
                onOpenZoom = onOpenZoom,
                onToggleChrome = onToggleChrome,
            )
        }
    }

    // 底栏原由 Scaffold 的 floatingToolbar 槽承载；GlassScaffold 无该槽，
    // 改为内容层底部居中的覆盖层，位置与原先一致
    ReaderToolbarOverlay(
        visible = chromeVisible,
        horizontal = horizontal,
        favorited = favorited,
        liked = liked,
        toolbarBackdrop = toolbarBackdrop,
        bottomInset = contentPadding.calculateBottomPadding(),
        onToggleMode = onToggleMode,
        onChapters = onChapters,
        onComments = onComments,
        onFavorite = onFavorite,
        onLike = onLike,
    )

    // 长图模式的整屏缩放层：双击页面打开，单击或返回键退出。
    // 放在工具栏之后，保证盖住内容与工具栏而不是被它们压住
    ReaderZoomOverlay(
        photo = photo,
        imageLoader = imageLoader,
        screenSize = screenSize,
        zoomOverlayIndex = zoomOverlayIndex,
        onDismiss = onZoomDismiss,
    )
}


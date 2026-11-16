package com.jm3.app.miuix.ui.coui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.jm3.app.miuix.reader.ReaderActions
import com.jm3.app.miuix.reader.ReaderSession
import com.jm3.app.miuix.reader.ReaderSpreadPage
import com.jm3.app.miuix.ui.EdgePageTurn
import com.jm3.app.miuix.ui.configureReaderDefaults
import com.jm3.app.miuix.ui.decideEdgePageTurn
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.readerPageBackfill
import com.jm3.app.miuix.ui.readerPageFrame
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.settings.ReaderImageQuality
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

/** 页框与解码尺寸的一组快照：容器宽、纵向页宽、整屏解码尺寸与双页解码尺寸由编排层算好传下来。 */
internal data class CouiReaderPageGeometry(
    val containerWidthPx: Int,
    val pageWidthPx: Int,
    val screenSize: Size,
    val spreadPageSize: Size,
)

/**
 * 页框与图像层：纵向用 LazyColumn 按真实比例逐页排布，横向用 HorizontalPager 整屏翻页。
 * 页框比例缓存与预取订阅只服务这一层，因此和绘制一起放在这里；页码真源仍由 session 持有。
 */
@Composable
internal fun ReaderPageContent(
    photo: PhotoPages,
    imageLoader: ImageLoader,
    geometry: CouiReaderPageGeometry,
    horizontal: Boolean,
    spreadActive: Boolean,
    pageZoomed: Boolean,
    pagerState: PagerState,
    pagerPageCount: Int,
    listState: LazyListState,
    session: ReaderSession,
    actions: ReaderActions,
    imageQuality: ReaderImageQuality,
    prefetchPages: Int,
    onPageZoomed: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    onOpenZoomOverlay: (Int) -> Unit,
) {
    val contentInsets = WindowInsets.systemBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalPlatformContext.current
    val containerWidthPx = geometry.containerWidthPx
    val pageWidthPx = geometry.pageWidthPx
    val screenSize = geometry.screenSize
    val spreadPageSize = geometry.spreadPageSize
    // 已解码页的真实宽高比：解码成功即回填，只驱动页框布局
    val pageRatios = remember(photo) { mutableStateMapOf<Int, Float>() }
    // 只驱动请求尺寸的比例：仅当解码结果顶满请求框时才回填，避免一次回填就把内存缓存键换掉
    val decodeRatios = remember(photo) { mutableStateMapOf<Int, Float>() }
    var chapterRatioSeed by remember(photo) { mutableStateOf<Float?>(null) }

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
                    onTap = { onToggleChrome() },
                    onZoomChanged = { zoomed ->
                        if (index == pagerState.currentPage) onPageZoomed(zoomed)
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
                    onTap = { onToggleChrome() },
                    onZoomChanged = { zoomed ->
                        // 只有当前页的缩放状态才约束翻页，预加载的邻页不参与
                        if (index == pagerState.currentPage) onPageZoomed(zoomed)
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
                            if (turn != EdgePageTurn.None) actions.turnPage(turn, stillPinned)
                        }
                    } else {
                        null
                    },
                )
            }
        }
    } else {
        // 竖向阅读使用 LazyColumn + LocalFloatingBarContentPadding，避免混用 Miuix 滚动条；
        // 顶部让出 HUD 顶栏净空（首屏内容不被顶栏挡住），底部叠加浮栏与 HUD 尾项净空
        val floatingBarPadding = LocalFloatingBarContentPadding.current
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().readableWidth(),
            contentPadding = PaddingValues(
                start = contentInsets.calculateLeftPadding(layoutDirection),
                top = contentInsets.calculateTopPadding() + ReaderHudTopHeight + ReaderHudBarVerticalMargin * 2,
                end = contentInsets.calculateRightPadding(layoutDirection),
                bottom = contentInsets.calculateBottomPadding() + floatingBarPadding,
            ),
            overscrollEffect = rememberCouiOverscrollEffect(),
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
                    onDoubleTap = { onOpenZoomOverlay(index) },
                    onTap = { onToggleChrome() },
                )
            }
            // 底部悬浮工具栏会遮住最后一页末尾，留出可滚动空间
            item(key = "toolbar-space") { Spacer(modifier = Modifier.height(96.dp)) }
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

/** 长图模式的整屏缩放层：铺黑底托住单页，返回键与单击都退出。 */
@Composable
internal fun ReaderZoomOverlay(
    image: PageImage,
    imageLoader: ImageLoader,
    decodeSize: Size,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        ReaderPage(
            image = image,
            imageLoader = imageLoader,
            decodeSize = decodeSize,
            modifier = Modifier.fillMaxSize(),
            zoomEnabled = true,
            onTap = { onDismiss() },
        )
    }
    BackHandler { onDismiss() }
}

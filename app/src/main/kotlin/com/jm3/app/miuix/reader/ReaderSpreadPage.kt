package com.jm3.app.miuix.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.jm3.app.miuix.ui.isPageZoomed
import com.jm3.core.model.PageImage
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/**
 * 双页阅读的一屏：两页共用一个缩放层，整屏同步缩放。
 *
 * 不用「两页各自缩放」：并排两页各带一套手势识别会在中缝附近抢指针，
 * 放大后横向拖动究竟该平移左页、右页还是翻页没有稳定答案。整屏共用一个
 * ZoomableState 后，捏合与平移只作用于整屏，未放大时仍由 pager 负责翻页；
 * 放大到边界后沿用单页的 edge-drag 交接，翻页手势不会被缩放层吃掉。
 *
 * 单页渲染由 [page] 提供：两套皮肤的失败提示与文字资源不同，页框编排这里只管缩放与手势。
 */
@Composable
internal fun ReaderSpreadPage(
    images: List<PageImage>,
    page: @Composable (image: PageImage, modifier: Modifier) -> Unit,
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
                    page(image, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            // 首页单页与末尾补空：单页保持与并排页相同的宽度，居中显示，翻屏时版面不跳
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                images.firstOrNull()?.let { image ->
                    page(image, Modifier.fillMaxHeight().fillMaxWidth(0.5f))
                }
            }
        }
    }
}

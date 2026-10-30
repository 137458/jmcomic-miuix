package com.jm3.app.miuix.ui

/** 缩放比超过该阈值即视为已放大：浮点比较留余量，避免 1x 时被抖动判成放大。 */
internal const val ZOOMED_THRESHOLD = 0.02f

/** 放大态下内容已到边界、单指继续横拖时的翻页判定结果。 */
internal enum class EdgePageTurn { None, Previous, Next }

/** 横拖距离占页宽的比例达到该值即认为翻页意图成立。 */
internal const val EDGE_PAGE_DISTANCE_RATIO = 0.18f

/** zoomFraction 为 null 表示内容尚未测量，按未放大处理。 */
internal fun isPageZoomed(zoomFraction: Float?): Boolean =
    zoomFraction != null && zoomFraction > ZOOMED_THRESHOLD

/**
 * 放大后内容已到边界、单指继续横向拖动时的翻页判定。
 *
 * [dragX] 为内容停住之后手指继续空拖的累计横向位移，向右为正：向左拖（负值）露出下一页，
 * 向右拖露出上一页。只看空拖距离、不看松手速度：平移收尾时的残余速度很容易被当成甩动，
 * 而快甩时 telephoto 要等松手后才把内容带走，速度在这一层并不可靠。
 */
internal fun decideEdgePageTurn(
    dragX: Float,
    pageWidth: Float,
    currentPage: Int,
    pageCount: Int,
): EdgePageTurn {
    if (pageWidth <= 0f || pageCount <= 0) return EdgePageTurn.None
    val distance = kotlin.math.abs(dragX)
    if (distance < pageWidth * EDGE_PAGE_DISTANCE_RATIO) return EdgePageTurn.None
    val towardsNext = dragX < 0f
    return if (towardsNext) {
        if (currentPage + 1 < pageCount) EdgePageTurn.Next else EdgePageTurn.None
    } else {
        if (currentPage > 0) EdgePageTurn.Previous else EdgePageTurn.None
    }
}
/**
 * 手势位移与内容位移之差在该像素值以内视为内容仍在跟随手势。留余量是因为父节点在 Initial
 * pass 读到的是上一帧的偏移，最后一个位移事件的处理结果还没反映进来。
 */
internal const val CONTENT_PIN_TOLERANCE_PX = 24f

/** 松手后先等惯性动画起步，再复核内容是否仍在移动，避免快甩被当成边界空拖。 */
internal const val EDGE_CONFIRM_DELAY_MS = 120L

/** 松手后内容位移超过该像素值即判定为平移或惯性，不参与翻页。 */
internal const val EDGE_CONFIRM_MOVE_PX = 8f

/**
 * 放大态横向拖拽的边界判定器。
 *
 * telephoto 越过触摸阈值后会无条件消费全部位移事件，内容到边界之后也不放手（见
 * transformable.kt 的 pastTouchSlop 分支），父节点因此拿不到「未消费」的位移。改看内容偏移：
 * 手势位移减去内容实际跟随的位移，剩下的才是边界处的空拖，只有它参与翻页判定。
 */
internal class EdgeDragTracker(
    private val tolerancePx: Float = CONTENT_PIN_TOLERANCE_PX,
) {
    private var startOffsetX = 0f
    private var dragX = 0f
    private var offsetX = 0f

    /** 一次手势开始时重置，[offsetX] 为内容当前的横向偏移。 */
    fun start(offsetX: Float) {
        startOffsetX = offsetX
        this.offsetX = offsetX
        dragX = 0f
    }

    /** 每个位移事件调用一次：[deltaX] 为本次手指位移，[offsetX] 为内容当前的横向偏移。 */
    fun onDrag(deltaX: Float, offsetX: Float) {
        dragX += deltaX
        this.offsetX = offsetX
    }

    /** 内容停下之后累计的横向位移，向右为正；内容一直跟着手势走时返回 0。 */
    fun pinnedDragX(): Float {
        val extra = dragX - (offsetX - startOffsetX)
        return if (kotlin.math.abs(extra) <= tolerancePx) 0f else extra
    }
}

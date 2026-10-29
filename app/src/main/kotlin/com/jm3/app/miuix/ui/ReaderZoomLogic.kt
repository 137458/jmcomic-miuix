package com.jm3.app.miuix.ui

/** 缩放比超过该阈值即视为已放大：浮点比较留余量，避免 1x 时被抖动判成放大。 */
internal const val ZOOMED_THRESHOLD = 0.02f

/** 放大态下内容已到边界、单指继续横拖时的翻页判定结果。 */
internal enum class EdgePageTurn { None, Previous, Next }

/** 横拖距离占页宽的比例达到该值即认为翻页意图成立。 */
internal const val EDGE_PAGE_DISTANCE_RATIO = 0.25f

/** 松手时的横向速度（px/s）达到该值即认为甩动翻页。 */
internal const val EDGE_PAGE_FLING_VELOCITY = 1_000f

/** zoomFraction 为 null 表示内容尚未测量，按未放大处理。 */
internal fun isPageZoomed(zoomFraction: Float?): Boolean =
    zoomFraction != null && zoomFraction > ZOOMED_THRESHOLD

/**
 * 放大后内容已到边界、单指继续横向拖动时的翻页判定。
 *
 * [dragX] 为手指累计横向位移，向右为正；[velocityX] 为松手速度，方向与位移一致。
 * 向左拖（负值）露出下一页，向右拖露出上一页。距离与速度都不达标时不动，
 * 避免边界处的轻微抖动被误判成翻页。
 */
internal fun decideEdgePageTurn(
    dragX: Float,
    velocityX: Float,
    pageWidth: Float,
    currentPage: Int,
    pageCount: Int,
): EdgePageTurn {
    if (pageWidth <= 0f || pageCount <= 0) return EdgePageTurn.None
    val byDistance = kotlin.math.abs(dragX) >= pageWidth * EDGE_PAGE_DISTANCE_RATIO
    val byFling = kotlin.math.abs(velocityX) >= EDGE_PAGE_FLING_VELOCITY
    if (!byDistance && !byFling) return EdgePageTurn.None
    // 距离达标时以位移方向为准，仅靠甩动时以速度方向为准
    val towardsNext = if (byDistance) dragX < 0f else velocityX < 0f
    return if (towardsNext) {
        if (currentPage + 1 < pageCount) EdgePageTurn.Next else EdgePageTurn.None
    } else {
        if (currentPage > 0) EdgePageTurn.Previous else EdgePageTurn.None
    }
}

package com.jm3.core.reader

import com.jm3.core.settings.BottomBarPreferencePolicy

/**
 * 双页阅读的窗口可用性与「跨页索引 ↔ 单页索引」换算。
 *
 * 排布为书籍式：第 1 页（封面）单独一屏，其余每屏两页（2、3 / 4、5 …）；
 * 末尾不足两页时该屏只有一页，另一侧补空。
 *
 * 所有页码均为 1 基单页索引；跨页索引只用于驱动 horizontal pager。
 * 进度落盘、页码显示与恢复都从单页索引出发，跨页索引不能混进持久化状态，
 * 否则转屏、换设备或切回单页时会错开一页。
 */
object ReaderSpreadPolicy {

    /** 与平板断点同源（[BottomBarPreferencePolicy.TABLET_MIN_WIDTH_DP]）：600dp 及以上且横屏才允许双页。 */
    const val MIN_WIDTH_DP = BottomBarPreferencePolicy.TABLET_MIN_WIDTH_DP

    /** 首页固定单独成屏。 */
    private const val FIRST_SINGLE_PAGE = 1

    fun supportsSpread(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= MIN_WIDTH_DP && widthDp > heightDp

    /** 首页一屏 + 其余两页一屏；空章节没有跨页。 */
    fun spreadCount(pageCount: Int): Int =
        if (pageCount <= 0) 0 else 1 + pageCount / 2

    /** 该跨页包含的单页；越界返回空。 */
    fun pagesInSpread(spreadIndex: Int, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        if (spreadIndex !in 0 until spreadCount(pageCount)) return emptyList()
        if (spreadIndex == 0) return listOf(FIRST_SINGLE_PAGE)
        val first = 2 * spreadIndex
        val last = minOf(first + 1, pageCount)
        return (first..last).toList()
    }

    /** 单页所在的跨页下标；越界页码夹进 1..pageCount。 */
    fun spreadIndexForPage(page: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        val normalized = page.coerceIn(1, pageCount)
        return if (normalized == FIRST_SINGLE_PAGE) 0 else normalized / 2
    }

    /** 该跨页的锚点页（第一个单页）；进度与当前页都用它。 */
    fun anchorPageForSpread(spreadIndex: Int, pageCount: Int): Int =
        pagesInSpread(spreadIndex, pageCount).firstOrNull() ?: 0

    /** 任意单页换算到所在跨页的锚点页，保证落盘值可稳定恢复。 */
    fun anchorPageForPage(page: Int, pageCount: Int): Int =
        anchorPageForSpread(spreadIndexForPage(page, pageCount), pageCount)

    /**
     * 按跨页步进：音量键与翻页按钮在双页下按屏前进，不会卡在同一屏。
     * 越界夹在首尾跨页内。
     */
    fun stepAnchor(currentAnchor: Int, delta: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        val index = spreadIndexForPage(currentAnchor, pageCount)
        val target = (index + delta).coerceIn(0, spreadCount(pageCount) - 1)
        return anchorPageForSpread(target, pageCount)
    }

    /** HorizontalPager 页键：区分章节、单/双页模式与具体页（或跨页）索引，避免复用错位。 */
    fun pagerKey(
        photoId: String,
        spreadActive: Boolean,
        pageCount: Int,
        index: Int,
        singlePageIndex: Int? = null,
    ): String {
        if (!spreadActive) {
            return "$photoId:single:${singlePageIndex ?: (index + 1)}"
        }
        val spreadPages = pagesInSpread(index, pageCount)
        val suffix = if (spreadPages.isEmpty()) "out-$index" else spreadPages.joinToString("-")
        return "$photoId:spread:$suffix"
    }
}

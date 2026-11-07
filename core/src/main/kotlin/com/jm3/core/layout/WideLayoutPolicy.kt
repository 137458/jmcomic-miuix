package com.jm3.core.layout

import com.jm3.core.settings.BottomBarPreferencePolicy

/**
 * 窗口宽度等级：与 Material 3 的 window size class 同义。
 *
 * 阈值只复用既有平板断点（600dp）与新增的 Expanded 断点（840dp），页面不再各自定义。
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * 大屏布局策略：列数、单栏可读宽度与详情分栏都从窗口宽高推导，不依赖设备类型。
 *
 * 折叠屏展开/折叠、分屏与自由窗口只改变窗口宽高，因此只需重组就能重新判定。
 * 手机（断点以下）一律走既有布局：列数仍是 2、单栏不限宽、详情不分栏。
 */
object WideLayoutPolicy {

    /** Medium 断点直接复用底栏/宽屏导航的平板断点，避免同一阈值出现两份定义。 */
    const val MEDIUM_MIN_WIDTH_DP = BottomBarPreferencePolicy.TABLET_MIN_WIDTH_DP

    /** Expanded 断点：大屏开始使用更宽松的限宽与详情分栏。 */
    const val EXPANDED_MIN_WIDTH_DP = 840

    /** 手机端既有的固定列数，断点以下保持完全不变。 */
    const val PHONE_GRID_COLUMNS = 2

    /** 网格卡片的理想最小宽度：大屏按可用宽度补列，卡片不会被等比拉宽。 */
    const val GRID_MIN_COLUMN_WIDTH_DP = 180

    /** 网格左右外边距之和（每侧 16dp），用于从窗口宽度换算排布宽度。 */
    const val GRID_HORIZONTAL_MARGIN_DP = 32

    /** Medium 窗口的单栏可读宽度（横屏手机、竖屏小平板）。 */
    const val MEDIUM_CONTENT_MAX_WIDTH_DP = 640

    /** Expanded 窗口的单栏可读宽度。 */
    const val EXPANDED_CONTENT_MAX_WIDTH_DP = 840

    /** 详情页固定双栏的最小宽度。 */
    const val DETAIL_TWO_PANE_MIN_WIDTH_DP = EXPANDED_MIN_WIDTH_DP

    /** 横屏详情双栏的最小宽度：折叠屏外屏这类窄横屏不满足。 */
    const val DETAIL_TWO_PANE_LANDSCAPE_MIN_WIDTH_DP = MEDIUM_MIN_WIDTH_DP

    /** 列数上限：避免超宽窗口把封面压成邮票大小。 */
    const val MAX_GRID_COLUMNS = 6

    fun widthClass(widthDp: Int): WindowWidthClass = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> WindowWidthClass.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.COMPACT
    }

    /**
     * 作品网格列数：断点以下沿用手机的两列；大屏按可用宽度补列。
     *
     * 卡片宽度由列数反算（每张占 `(可用宽度 - 间距) / 列数`），因此不会随窗口等比放大。
     */
    fun gridColumns(windowWidthDp: Int): Int {
        if (windowWidthDp < MEDIUM_MIN_WIDTH_DP) return PHONE_GRID_COLUMNS
        val available = windowWidthDp - GRID_HORIZONTAL_MARGIN_DP
        return (available / GRID_MIN_COLUMN_WIDTH_DP)
            .coerceIn(PHONE_GRID_COLUMNS + 1, MAX_GRID_COLUMNS)
    }

    /** 单栏内容最大宽度；null 表示手机断点以下不限制。 */
    fun contentMaxWidthDp(windowWidthDp: Int): Int? = when (widthClass(windowWidthDp)) {
        WindowWidthClass.COMPACT -> null
        WindowWidthClass.MEDIUM -> MEDIUM_CONTENT_MAX_WIDTH_DP
        WindowWidthClass.EXPANDED -> EXPANDED_CONTENT_MAX_WIDTH_DP
    }

    /**
     * 详情页是否双栏：Expanded 宽度直接分栏；Medium 宽度只在横屏时分成左右两栏。
     *
     * 竖屏平板上单栏宽度本就接近可读上限，强制分栏会让两侧都过窄。
     */
    fun supportsDetailTwoPane(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= DETAIL_TWO_PANE_MIN_WIDTH_DP ||
            (widthDp >= DETAIL_TWO_PANE_LANDSCAPE_MIN_WIDTH_DP && widthDp > heightDp)
}

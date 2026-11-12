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

    /** 固定双栏还要求的最小窗口高度：矮横屏分栏后每栏都不足一屏内容。 */
    const val DETAIL_TWO_PANE_MIN_HEIGHT_DP = 600

    /** 双栏单侧最小宽度：低于该值的栏放不下封面与正文，拖动到此为止。 */
    const val DETAIL_PANE_MIN_WIDTH_DP = 320

    /** 分隔条触摸区宽度：28dp 既够手指命中，也不至于挤占两侧内容。 */
    const val DETAIL_DIVIDER_TOUCH_WIDTH_DP = 28

    /** 双栏比例默认值（左栏占百分比），与详情页主图栏的视觉重量一致。 */
    const val DETAIL_SPLIT_PERCENT_DEFAULT = 60

    /** 双栏比例下限与上限，避免拖成只能看见一条缝。 */
    const val DETAIL_SPLIT_PERCENT_MIN = 20
    const val DETAIL_SPLIT_PERCENT_MAX = 80

    /** 侧栏要求的最小窗口高度：8 项侧栏在 412dp 高的横屏里会溢出且点不到。 */
    const val NAVIGATION_RAIL_MIN_HEIGHT_DP = MEDIUM_MIN_WIDTH_DP

    /** 弹层宽度档：Medium 与 Expanded 各放宽一档，手机仍用库默认宽度。 */
    const val DIALOG_WIDTH_MEDIUM_DP = 440
    const val DIALOG_WIDTH_EXPANDED_DP = 520

    /** 弹层最大高度占窗口高度的比例，矮横屏改走几乎满高。 */
    const val DIALOG_MAX_HEIGHT_RATIO = 0.6f

    /** 矮横屏判定：窗口高度低于该值时弹层几乎占满，避免长列表被压成两三行。 */
    const val DIALOG_COMPACT_WINDOW_HEIGHT_DP = 500

    /** 矮横屏弹层上下各留 16dp，避免贴住系统栏。 */
    const val DIALOG_COMPACT_VERTICAL_MARGIN_DP = 32

    /** 列数上限：避免超宽窗口把封面压成邮票大小。 */
    const val MAX_GRID_COLUMNS = 6

    fun widthClass(widthDp: Int): WindowWidthClass = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> WindowWidthClass.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.COMPACT
    }

    /**
     * 作品网格列数：断点以下沿用手机的两列；大屏按**限宽后的内容宽度**补列。
     *
     * 大屏正文被 [contentMaxWidthDp] 收窄（640/840dp），列数若按窗口宽度算，
     * 窗口越宽列越多、卡片反而越窄（1280dp 曾排出 7 列 116dp 的封面）。
     * 因此这里先取内容宽度再换算，卡片宽度始终不低于 [GRID_MIN_COLUMN_WIDTH_DP]。
     */
    fun gridColumns(windowWidthDp: Int): Int = gridColumnsForContent(contentWidthDp(windowWidthDp))

    /** 按实际排布宽度算列数：调用方已经知道网格自身可用宽度时直接用这个。 */
    fun gridColumnsForContent(availableWidthDp: Int): Int {
        if (availableWidthDp < MEDIUM_MIN_WIDTH_DP) return PHONE_GRID_COLUMNS
        val available = availableWidthDp - GRID_HORIZONTAL_MARGIN_DP
        return (available / GRID_MIN_COLUMN_WIDTH_DP)
            .coerceIn(PHONE_GRID_COLUMNS + 1, MAX_GRID_COLUMNS)
    }

    /**
     * 列表页行卡的分栏列数：内容宽达到 Expanded 断点才排两列。
     *
     * 行卡是「封面 + 多行文字 + 右侧动作」的横向结构，单栏限宽后一行能放两张；
     * 再宽的窗口也只排两列——三列会让每张行卡不足 300dp，标题被挤成省略号。
     * 判据用内容宽而不是窗口宽：手机断点下内容宽 = 窗口宽，永远单列。
     */
    fun feedColumns(contentWidthDp: Int): Int =
        if (contentWidthDp >= EXPANDED_MIN_WIDTH_DP) FEED_TWO_COLUMN_COUNT else 1

    /** 行卡列表最多两列。 */
    const val FEED_TWO_COLUMN_COUNT = 2

    /** 限宽后的实际内容宽度：窄窗口等于窗口宽度，大屏被断点上限封顶。 */
    fun contentWidthDp(windowWidthDp: Int): Int =
        contentMaxWidthDp(windowWidthDp)?.let { minOf(windowWidthDp, it) } ?: windowWidthDp

    /** 单栏内容最大宽度；null 表示手机断点以下不限制。 */
    fun contentMaxWidthDp(windowWidthDp: Int): Int? = when (widthClass(windowWidthDp)) {
        WindowWidthClass.COMPACT -> null
        WindowWidthClass.MEDIUM -> MEDIUM_CONTENT_MAX_WIDTH_DP
        WindowWidthClass.EXPANDED -> EXPANDED_CONTENT_MAX_WIDTH_DP
    }

    /**
     * 详情页是否双栏：横屏平板直接分栏；Expanded 宽度还要求足够的窗口高度。
     *
     * 竖屏平板上单栏宽度本就接近可读上限，强制分栏会让两侧都过窄；
     * 840x412dp 这类矮宽窗口分栏后每栏都不足 420dp，同样回落单栏。
     */
    fun supportsDetailTwoPane(widthDp: Int, heightDp: Int): Boolean =
        (widthDp >= DETAIL_TWO_PANE_LANDSCAPE_MIN_WIDTH_DP && widthDp > heightDp) ||
            (widthDp >= DETAIL_TWO_PANE_MIN_WIDTH_DP && heightDp >= DETAIL_TWO_PANE_MIN_HEIGHT_DP)

    /** 侧栏是否可用：宽高双条件，矮横屏一律退回底栏。 */
    fun supportsNavigationRail(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= MEDIUM_MIN_WIDTH_DP && heightDp >= NAVIGATION_RAIL_MIN_HEIGHT_DP

    /** 持久化的百分比转成分栏比例，越界值夹取到允许区间。 */
    fun detailSplitFraction(splitPercent: Int): Float =
        splitPercent.coerceIn(DETAIL_SPLIT_PERCENT_MIN, DETAIL_SPLIT_PERCENT_MAX) / 100f

    /** 分栏比例反算回整数百分比，供落盘使用。 */
    fun detailSplitPercent(fraction: Float): Int =
        Math.round(fraction * 100f).coerceIn(DETAIL_SPLIT_PERCENT_MIN, DETAIL_SPLIT_PERCENT_MAX)

    /**
     * 拖动分隔条时夹取比例：先保证单侧不小于 [DETAIL_PANE_MIN_WIDTH_DP]。
     *
     * 窗口窄到两栏都放不下最小宽度时退回等分，避免出现负值区间。
     */
    fun clampDetailSplitFraction(fraction: Float, availableWidthDp: Int): Float {
        val minFraction = DETAIL_PANE_MIN_WIDTH_DP.toFloat() / availableWidthDp.coerceAtLeast(1)
        if (minFraction * 2f >= 1f) return 0.5f
        return fraction.coerceIn(minFraction, 1f - minFraction)
    }

    /** 弹层宽度：手机为 null（沿用组件库默认），大屏按断点放宽。 */
    fun dialogWidthDp(windowWidthDp: Int): Int? = when (widthClass(windowWidthDp)) {
        WindowWidthClass.COMPACT -> null
        WindowWidthClass.MEDIUM -> DIALOG_WIDTH_MEDIUM_DP
        WindowWidthClass.EXPANDED -> DIALOG_WIDTH_EXPANDED_DP
    }

    /**
     * 弹层最大高度：常规窗口取六成，矮横屏改为几乎占满。
     *
     * 返回 0 表示窗口尺寸未知（组合根尚未测量），调用方应视为「不加约束」。
     */
    fun dialogMaxHeightDp(windowHeightDp: Int): Int = when {
        windowHeightDp <= DIALOG_COMPACT_VERTICAL_MARGIN_DP -> 0
        windowHeightDp < DIALOG_COMPACT_WINDOW_HEIGHT_DP -> windowHeightDp - DIALOG_COMPACT_VERTICAL_MARGIN_DP
        else -> (windowHeightDp * DIALOG_MAX_HEIGHT_RATIO).toInt()
    }
}

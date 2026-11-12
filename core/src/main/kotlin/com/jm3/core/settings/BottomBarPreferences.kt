package com.jm3.core.settings

/** 底栏尺寸档：普通按设备自适应（手机标准、平板偏大），偏大在所有设备强制大值。 */
enum class BottomBarSize(val label: String) {
    STANDARD("普通"),
    GENEROUS("偏大"),
}

/** 底栏形态档：悬浮胶囊 / 贴底通栏。 */
enum class BottomBarForm(val label: String) {
    FLOATING("悬浮"),
    DOCKED("不悬浮"),
}

/** 宽屏导航：宽高都够时默认侧栏，其余窗口一律底栏。 */
enum class WideScreenNavigation(val label: String) {
    BOTTOM_BAR("底栏"),
    RAIL("侧栏"),
}

/**
 * 底栏默认值解析：尺寸未显式设置时按窗口宽度断点判断，形态未设置恒为悬浮，都不写死某一档的具体设备。
 *
 * 尺寸档只有「偏大」是强制选择；「普通」与未设置同义，都按断点自适应
 * （手机标准、平板偏大）。形态档只有悬浮一档默认值，与断点无关，用户选过则以其选择为准。
 * 非法持久化值由 [BottomBarSettings] 读成 null，同样走这里的默认（尺寸按断点，形态恒为悬浮）。
 */
object BottomBarPreferencePolicy {

    /** 平板断点：窗口宽度达到该值即按平板处理。 */
    const val TABLET_MIN_WIDTH_DP = 600

    fun isTablet(widthDp: Int): Boolean = widthDp >= TABLET_MIN_WIDTH_DP

    /** 手机标准，平板偏大。 */
    fun defaultSize(isTablet: Boolean): BottomBarSize =
        if (isTablet) BottomBarSize.GENEROUS else BottomBarSize.STANDARD

    /** 全设备默认悬浮：唯一一档默认值，不受窗口宽度影响。 */
    fun defaultForm(): BottomBarForm = BottomBarForm.FLOATING

    /** 偏大档强制大值；普通档与未设置都按设备自适应。 */
    fun resolveSize(explicit: BottomBarSize?, widthDp: Int): BottomBarSize =
        if (explicit == BottomBarSize.GENEROUS) BottomBarSize.GENEROUS else defaultSize(isTablet(widthDp))

    /** 未设置即悬浮；默认值的唯一出口在 [defaultForm]。 */
    fun resolveForm(explicit: BottomBarForm?): BottomBarForm = explicit ?: defaultForm()

    /**
     * 只解析显式值：未设置返回底栏兜底。
     *
     * 布局用的默认值依赖窗口宽高，走 [defaultWideScreenNavigation]；
     * 这里保持无窗口参数的纯解析，供设置页等只读持久化值的场景使用。
     */
    fun resolveWideScreenNavigation(explicit: WideScreenNavigation?): WideScreenNavigation =
        explicit ?: WideScreenNavigation.BOTTOM_BAR

    /**
     * 宽屏导航默认形态：宽高都达到断点才用侧栏。
     *
     * 只看宽度会把 800x400dp 的横屏手机当宽屏，而 4 项侧栏在 400dp 高度里
     * 会挤到点不准；600x600 是「放得下侧栏且还能容纳正文」的最低组合。
     */
    fun defaultWideScreenNavigation(widthDp: Int, heightDp: Int): WideScreenNavigation =
        if (supportsWideScreenNavigationRail(widthDp, heightDp)) {
            WideScreenNavigation.RAIL
        } else {
            WideScreenNavigation.BOTTOM_BAR
        }

    /**
     * 宽屏导航设置项可见的窗口阈值：复用既有平板断点 [TABLET_MIN_WIDTH_DP]（600dp），
     * 小平板也按平板处理并显示该项；阈值以下整行不渲染。
     */
    const val WIDE_SCREEN_NAVIGATION_MIN_WIDTH_DP = TABLET_MIN_WIDTH_DP

    fun supportsWideScreenNavigation(widthDp: Int): Boolean =
        widthDp >= WIDE_SCREEN_NAVIGATION_MIN_WIDTH_DP

    /** 侧栏要求的最小窗口高度：矮横屏里侧栏放不下几项，也挤掉正文高度。 */
    const val WIDE_SCREEN_NAVIGATION_MIN_HEIGHT_DP = TABLET_MIN_WIDTH_DP

    /** 侧栏可用的宽高双条件：宽度与高度都必须达到断点。 */
    fun supportsWideScreenNavigationRail(widthDp: Int, heightDp: Int): Boolean =
        supportsWideScreenNavigation(widthDp) && heightDp >= WIDE_SCREEN_NAVIGATION_MIN_HEIGHT_DP

    /**
     * 布局实际采用的导航：未设置走断点默认，显式选择优先；但窗口撑不起侧栏时一律底栏。
     *
     * 设置项不可见（宽度不足）时同样底栏，即使盘上残留了侧栏的显式值。
     */
    fun resolveActiveWideScreenNavigation(
        explicit: WideScreenNavigation?,
        widthDp: Int,
        heightDp: Int,
    ): WideScreenNavigation = when {
        !supportsWideScreenNavigation(widthDp) -> WideScreenNavigation.BOTTOM_BAR
        explicit == null -> defaultWideScreenNavigation(widthDp, heightDp)
        explicit == WideScreenNavigation.RAIL ->
            if (supportsWideScreenNavigationRail(widthDp, heightDp)) {
                WideScreenNavigation.RAIL
            } else {
                WideScreenNavigation.BOTTOM_BAR
            }
        else -> WideScreenNavigation.BOTTOM_BAR
    }
}

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

/** 宽屏导航：默认底栏，侧栏为可选。 */
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

    /** 宽屏导航默认底栏；用户显式选过侧栏才走 Rail。 */
    fun resolveWideScreenNavigation(explicit: WideScreenNavigation?): WideScreenNavigation =
        explicit ?: WideScreenNavigation.BOTTOM_BAR

    /**
     * 宽屏导航设置项可见的窗口阈值：复用既有平板断点 [TABLET_MIN_WIDTH_DP]（600dp），
     * 小平板也按平板处理并显示该项；阈值以下整行不渲染。
     */
    const val WIDE_SCREEN_NAVIGATION_MIN_WIDTH_DP = TABLET_MIN_WIDTH_DP

    fun supportsWideScreenNavigation(widthDp: Int): Boolean =
        widthDp >= WIDE_SCREEN_NAVIGATION_MIN_WIDTH_DP

    /** 布局实际采用的导航：设置项不可见时一律底栏，即使盘上残留了侧栏的显式值。 */
    fun resolveActiveWideScreenNavigation(explicit: WideScreenNavigation?, widthDp: Int): WideScreenNavigation =
        if (supportsWideScreenNavigation(widthDp)) {
            resolveWideScreenNavigation(explicit)
        } else {
            WideScreenNavigation.BOTTOM_BAR
        }
}

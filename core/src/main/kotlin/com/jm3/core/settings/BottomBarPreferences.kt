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

/**
 * 底栏默认值解析：未显式设置时按窗口宽度断点判断，而不是写死某一档。
 *
 * 尺寸档只有「偏大」是强制选择；「普通」与未设置同义，都按断点自适应
 * （手机标准、平板偏大）。形态档未显式设置时走断点默认，用户选过则以其选择为准。
 * 非法持久化值由 [BottomBarSettings] 读成 null，同样走这里的断点默认。
 */
object BottomBarPreferencePolicy {

    /** 平板断点：窗口宽度达到该值即按平板处理。 */
    const val TABLET_MIN_WIDTH_DP = 600

    fun isTablet(widthDp: Int): Boolean = widthDp >= TABLET_MIN_WIDTH_DP

    /** 手机标准，平板偏大。 */
    fun defaultSize(isTablet: Boolean): BottomBarSize =
        if (isTablet) BottomBarSize.GENEROUS else BottomBarSize.STANDARD

    /** 全设备默认悬浮。 */
    fun defaultForm(isTablet: Boolean): BottomBarForm = BottomBarForm.FLOATING

    /** 偏大档强制大值；普通档与未设置都按设备自适应。 */
    fun resolveSize(explicit: BottomBarSize?, widthDp: Int): BottomBarSize =
        if (explicit == BottomBarSize.GENEROUS) BottomBarSize.GENEROUS else defaultSize(isTablet(widthDp))

    fun resolveForm(explicit: BottomBarForm?, widthDp: Int): BottomBarForm =
        explicit ?: defaultForm(isTablet(widthDp))
}

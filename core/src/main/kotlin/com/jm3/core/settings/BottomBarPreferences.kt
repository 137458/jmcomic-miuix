package com.jm3.core.settings

/** 底栏尺寸档：普通保持原有数值，偏大整体更饱满。 */
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
 * 尺寸与形态共用同一条断点判定；用户手动改过之后以用户选择为准，不再被断点覆盖。
 * 非法持久化值由 [BottomBarSettings] 读成 null，同样走这里的断点默认。
 */
object BottomBarPreferencePolicy {

    /** 平板断点：窗口宽度达到该值即按平板处理。 */
    const val TABLET_MIN_WIDTH_DP = 600

    fun isTablet(widthDp: Int): Boolean = widthDp >= TABLET_MIN_WIDTH_DP

    /** 手机默认普通，平板默认偏大。 */
    fun defaultSize(isTablet: Boolean): BottomBarSize =
        if (isTablet) BottomBarSize.GENEROUS else BottomBarSize.STANDARD

    /** 全设备默认悬浮。 */
    fun defaultForm(isTablet: Boolean): BottomBarForm = BottomBarForm.FLOATING

    fun resolveSize(explicit: BottomBarSize?, widthDp: Int): BottomBarSize =
        explicit ?: defaultSize(isTablet(widthDp))

    fun resolveForm(explicit: BottomBarForm?, widthDp: Int): BottomBarForm =
        explicit ?: defaultForm(isTablet(widthDp))
}

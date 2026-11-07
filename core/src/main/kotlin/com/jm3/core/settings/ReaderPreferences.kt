package com.jm3.core.settings

/** 阅读图片质量档：解码像素预算随之切换。 */
enum class ReaderImageQuality(val label: String, val maxDecodePixels: Int) {
    ECONOMY("省流", 2_000_000),
    STANDARD("标准", 4_000_000),
    HIGH("高清", 6_000_000),
}

/** 阅读页布局：单页 / 双页。双页仅在 600dp 以上横屏可用，默认单页。 */
enum class ReaderSpreadMode(val label: String) {
    SINGLE("单页"),
    DOUBLE("双页"),
}

/** 阅读器设置的归一常量与纯函数。 */
object ReaderPreferences {

    /** 亮度为 -1 表示跟随系统。 */
    const val FOLLOW_SYSTEM_BRIGHTNESS = -1

    const val MIN_BRIGHTNESS = 1
    const val MAX_BRIGHTNESS = 100

    const val DEFAULT_PREFETCH_PAGES = 3

    /** 预取页数可选档位；0 表示不预取。 */
    val PREFETCH_OPTIONS = listOf(0, 2, 3, 5)

    /** 亮度归一：非正数（含 0 与 -1）都归到跟随系统，超过上限夹到 100。 */
    fun normalizeBrightness(raw: Int?): Int = when {
        raw == null || raw <= 0 -> FOLLOW_SYSTEM_BRIGHTNESS
        raw > MAX_BRIGHTNESS -> MAX_BRIGHTNESS
        else -> raw
    }

    /** 预取页数只接受预设档位，其他值回退默认 3 页。 */
    fun normalizePrefetchPages(raw: Int?): Int =
        raw?.takeIf { it in PREFETCH_OPTIONS } ?: DEFAULT_PREFETCH_PAGES

    /** 质量档名归一，损坏值回退标准档。 */
    fun normalizeImageQuality(raw: String?): ReaderImageQuality =
        ReaderImageQuality.entries.firstOrNull { it.name == raw } ?: ReaderImageQuality.STANDARD

    /** 阅读页布局归一，损坏值回退单页。 */
    fun normalizeSpreadMode(raw: String?): ReaderSpreadMode =
        ReaderSpreadMode.entries.firstOrNull { it.name == raw } ?: ReaderSpreadMode.SINGLE
}

package com.jm3.app.miuix.reader

/** 一屏之内的页框排布：双页并排，或单页居中半宽。 */
internal enum class ReaderSpreadArrange {
    /** 两页并排：各占半屏，整屏共用一个缩放层。 */
    SideBySide,

    /** 单页（首页与末尾补空）：居中、半宽，与并排页同宽，翻屏时版面不跳。 */
    SingleCentered,
}

/** 按本屏图片数选排布：两页及以上并排，单页居中。 */
internal fun spreadArrangeFor(imageCount: Int): ReaderSpreadArrange =
    if (imageCount >= 2) ReaderSpreadArrange.SideBySide else ReaderSpreadArrange.SingleCentered

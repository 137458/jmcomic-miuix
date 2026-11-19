package com.jm3.app.miuix.reader

/**
 * 放大阅读时 pager 是否接管翻页手势。
 *
 * 放大后拖拽用于平移画面，再让 pager 抢手势就会把平移变成翻页；因此只有未放大时才接管。
 * 判定原本写在两套皮肤的 pagerGestureOverride(enabled = !pageZoomed) 里，屏幕一放大就没人测得到；
 * 抽成纯函数后，缩放阈值与手势开关落在同一个断言面上。
 *
 * ui/ReaderPageHost.kt 与 ui/coui/page/CouiReaderPageContent.kt 现有的 enabled = !pageZoomed
 * 与这里的实现等价，后续任务会把两处调用点收敛到本函数（本次写域不含那两个文件）。
 */
internal fun pagerGestureEnabled(pageZoomed: Boolean): Boolean = !pageZoomed

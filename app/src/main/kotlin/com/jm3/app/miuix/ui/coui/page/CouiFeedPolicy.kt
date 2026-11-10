// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import kotlin.math.ceil

/**
 * 触底预取的提前量（dp）：列表离末尾还有这么远就开始取下一页。
 *
 * 首页与浏览页原来各写一份（320dp / 240dp）同构的 snapshotFlow，同样的滚动速度在两页给出的
 * 手感不一致；阈值只留这一个来源，两页共用。
 */
internal const val COUI_LOAD_MORE_PREFETCH_DP = 320

/**
 * 行高未知时的名义行高（dp）。
 *
 * LazyListState 只有在可见项完成布局后才给得出行高；首帧、空列表、正在切页的瞬间都用它兜底，
 * 免得 dp→项数的换算退化成 0 或 NaN。
 */
internal const val COUI_FEED_ROW_HEIGHT_DP = 72f

/**
 * 把 dp 提前量换算成「提前多少项」：按实测行高向上取整，至少提前 1 项。
 *
 * LazyColumn 的触底判据只能拿项数（下标）比，拿不到像素；行高由调用方按可见项高度均值
 * 折算传入。[rowHeightDp] 非有限或非正（未布局、除零）时退回 [COUI_FEED_ROW_HEIGHT_DP]。
 */
internal fun prefetchItemCount(prefetchDistanceDp: Float, rowHeightDp: Float): Int {
    val height = if (rowHeightDp.isFinite() && rowHeightDp > 0f) rowHeightDp else COUI_FEED_ROW_HEIGHT_DP
    return ceil(prefetchDistanceDp / height).toInt().coerceAtLeast(1)
}

/**
 * 触底判据：最后一个可见项已进入末尾的提前量区间时预取下一页。
 *
 * 空列表永不预取（[totalCount] 为 0 时下面那个减法没有意义）；
 * 提前量为负按 0 处理，判据不会因此变得比「看见最后一项」更早。
 */
internal fun shouldPrefetch(index: Int, totalCount: Int, prefetchDistance: Int): Boolean {
    if (totalCount <= 0) return false
    val distance = prefetchDistance.coerceAtLeast(0)
    return index >= totalCount - 1 - distance
}

/**
 * 榜单名次：第 [page] 页（1 基）第 [index] 项（0 基）在整份榜单里的序号（1 基）。
 *
 * 跨页必须续号，否则第二页又从 1 开始，前三名的高亮会重复出现在每一页。
 * 页码与步长非法时按 1 兜底：宁可把本页当第一页重算，也不给出 0 或负名次。
 */
internal fun rankOf(page: Int, stride: Int, index: Int): Int {
    val safePage = page.coerceAtLeast(1)
    val safeStride = stride.coerceAtLeast(1)
    return (safePage - 1) * safeStride + index.coerceAtLeast(0) + 1
}

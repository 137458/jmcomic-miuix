// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.itos.coui.layout.CouiPageDefaults
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.shouldLoadMore
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
 * 触底判据的唯一实现在 core 的 [PagedFeed.shouldLoadMore]：本函数是「列表混排、拿不到行起点」时的适配器。
 *
 * 详情页评论列表的懒列表里还排着头图、章节等条目，评论只是尾部的一段，页面拿不到「第几项开始是评论」，
 * 只能把总项数当 [totalCount] 传进来；这里借用一份恒为「还有下一页、不在加载中、上次没失败」的
 * feed，只求 [PagedFeed.shouldLoadMore] 的下标比较部分——比较逻辑与页面侧同源，feed 级状态
 * （还能不能加载、上次是否失败）不参与，由调用方按自己的 feed 另行判断。
 * 结果行起点已知的页面请直接用 [shouldPrefetchRow]，不要在这里再拼一份条件。
 */
internal fun shouldPrefetch(index: Int, totalCount: Int, prefetchDistance: Int): Boolean =
    PagedFeed<Unit>(hasMore = true).shouldLoadMore(index, totalCount, prefetchDistance)

/**
 * 页面侧触底判据：把懒列表的可见下标换算成 feed 行下标，再交给 core 的 [PagedFeed.shouldLoadMore]。
 *
 * [rowStart] 是「列表第几项开始是 feed 的第一行」。feed 级开关（还能不能加载、上次是否失败、
 * 空列表）都由 core 判据一并处理，调用点不必自己再判一遍。
 */
internal fun PagedFeed<*>.shouldPrefetchRow(
    lastVisibleIndex: Int,
    rowStart: Int,
    prefetchDistance: Int,
): Boolean = shouldLoadMore(lastVisibleIndex - rowStart, items.size, prefetchDistance)

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

/**
 * 行下标对应的展示名次：累计列表直接用下标，一次点击一页才按页续号。
 *
 * 无限滚动把每页拼进同一个 items，页码已随 append 推进，再用 [rankOf] 会把页偏移加两遍
 * （翻一页后第一行变成第 21 名）。点页模式每页 replace 成独立一页，下标从 0 起，必须续号。
 */
internal fun displayRank(clickPaging: Boolean, page: Int, stride: Int, index: Int): Int =
    if (clickPaging) rankOf(page, stride, index) else index.coerceAtLeast(0) + 1

/**
 * 浏览页结果行在 LazyColumn 中的起始下标（即结果列表前有多少固定头项）。
 *
 * 分类模式将主分类栏固定在顶栏、并将「更多分类」与「每周连载更新」收进弹层后，
 * 列表头部只保留筛选胶囊、可选标签导航与结果区标题；榜单模式仍保留周更卡片。
 */
internal fun couiBrowseHeadCount(
    isCategoryMode: Boolean,
    hasTagNav: Boolean,
    hasWeeklyPaging: Boolean = false,
    weeklyItemCount: Int = 0,
    hasWeeklyFooter: Boolean = false,
): Int = if (isCategoryMode) {
    2 + if (hasTagNav) 1 else 0
} else {
    val weeklyCount = if (weeklyItemCount > 0) {
        1 + weeklyItemCount + (if (hasWeeklyFooter) 1 else 0)
    } else {
        1
    }
    2 + (if (hasTagNav) 1 else 0) + weeklyCount + (if (hasWeeklyPaging) 1 else 0)
}

/**
 * 每周更新卡片的容器规格：统一切换为与其他 COUI 页面一致的独立浮岛行卡，
 * 不再包在外层 Group 内，也不在卡片之间插分割线。
 */
internal data class CouiWeeklyCardLayoutSpec(
    val wrapInGroup: Boolean,
    val showDivider: Boolean,
    val cardSpacingDp: Float,
    val horizontalMarginDp: Float,
)

internal fun couiWeeklyCardLayoutSpec(inDialog: Boolean): CouiWeeklyCardLayoutSpec =
    CouiWeeklyCardLayoutSpec(
        wrapInGroup = false,
        showDivider = false,
        cardSpacingDp = CouiWorkCardTokens.RowSpacing.value,
        horizontalMarginDp = if (inDialog) 0f else CouiPageDefaults.horizontalMargin.value,
    )

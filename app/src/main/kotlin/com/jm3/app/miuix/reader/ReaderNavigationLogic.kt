package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.EdgePageTurn
import com.jm3.core.reader.ReaderSpreadPolicy

/**
 * 跳页与边缘翻页的页码换算：单页页码 → 跨页锚点 → pager / 列表的 0 基下标。
 *
 * 双页阅读下页码的唯一真源仍是单页坐标（进度、页码显示都用它），跨页下标只进 pager；
 * 换算全部来自 core 的 [ReaderSpreadPolicy]，两套皮肤、两类排布共用同一条路径。
 * 夹取留在 pager 出口：pager 页数可能还停在旧章，目标不能落到不存在的屏。
 */

/** 跳页前的计划坐标：双页取所在跨页的锚点页，单页原样。越界页码留给会话按章节夹取。 */
internal fun planJumpPage(page: Int, pageCount: Int, spreadActive: Boolean): Int =
    if (spreadActive) ReaderSpreadPolicy.anchorPageForPage(page, pageCount) else page

/** 计划坐标 → 横向 pager 的 0 基下标：双页取跨页下标，单页是页码减一，最后夹进 pager 范围。 */
internal fun planPagerIndex(page: Int, pageCount: Int, spreadActive: Boolean, pagerPageCount: Int): Int {
    val pagerIndex = if (spreadActive) {
        ReaderSpreadPolicy.spreadIndexForPage(page, pageCount)
    } else {
        page - 1
    }
    return clampPagerTarget(pagerIndex, pagerPageCount)
}

/** 计划坐标 → 纵向列表的 0 基下标。 */
internal fun planListIndex(page: Int): Int = page - 1

/** 边缘空拖翻页的目标 pager 下标：按方向取相邻屏，越界夹回首尾。 */
internal fun planEdgeTurnPagerIndex(currentPage: Int, turn: EdgePageTurn, pagerPageCount: Int): Int {
    val target = currentPage + if (turn == EdgePageTurn.Next) 1 else -1
    return clampPagerTarget(target, pagerPageCount)
}

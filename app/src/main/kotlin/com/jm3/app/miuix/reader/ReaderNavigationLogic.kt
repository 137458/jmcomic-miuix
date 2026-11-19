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

/**
 * 跳页出口：横向走 pager 的屏下标，纵向走列表的项下标。
 *
 * 两条出口的末尾都必须在 finally 里解冻可见页回写，所以出口类型由纯函数定下，
 * 编排里只剩一个分派——分支一旦各写一遍，漏掉解冻只是某一种排布失效，真机上很难发现。
 */
internal sealed interface ReaderJumpTarget {
    /** 横向 pager 的 0 基屏下标。 */
    data class Pager(val index: Int) : ReaderJumpTarget

    /** 纵向长图列表的 0 基项下标。 */
    data class VerticalList(val index: Int) : ReaderJumpTarget
}

/** 计划坐标 → 跳页出口。夹取只留在 pager 出口：列表项与页码一一对应，越界页码已由会话夹好。 */
internal fun planJumpTarget(
    page: Int,
    pageCount: Int,
    spreadActive: Boolean,
    pagerPageCount: Int,
    horizontal: Boolean,
): ReaderJumpTarget = if (horizontal) {
    ReaderJumpTarget.Pager(planPagerIndex(page, pageCount, spreadActive, pagerPageCount))
} else {
    ReaderJumpTarget.VerticalList(planListIndex(page))
}


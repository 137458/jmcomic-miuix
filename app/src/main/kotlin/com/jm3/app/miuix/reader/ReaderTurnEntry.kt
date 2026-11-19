package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.EdgePageTurn

/**
 * 边缘拖拽翻页的入口判定：真的翻一屏，还是停在原地。
 *
 * 边界空拖与只有一屏的章里，方向已经由 decideEdgePageTurn 算出，但换算后的目标仍可能
 * 就是当前屏（首屏继续上一页、末屏继续下一页）；此时起一次“原地 Spring 动画”只会让
 * 页码与可见页回写白跑一遍。把这一步定成纯函数，翻不翻在进协程之前就定了。
 *
 * Compose 的 PagerState 与库里的 springAnimateToPage 只能在帧时钟下调起来，
 * 判定放在这里才进得了 JVM 单测。
 */
internal sealed interface ReaderTurnEntry {
    /** 翻到 [pagerIndex] 这一屏。 */
    data class TurnTo(val pagerIndex: Int) : ReaderTurnEntry

    /** 已经到头：不起动画，也不进协程。 */
    data object Stay : ReaderTurnEntry
}

/** 当前屏 [currentPagerIndex] 按 [turn] 翻一屏的入口判定。 */
internal fun planReaderTurnEntry(
    currentPagerIndex: Int,
    turn: EdgePageTurn,
    pagerPageCount: Int,
): ReaderTurnEntry {
    if (turn == EdgePageTurn.None) return ReaderTurnEntry.Stay
    val target = planEdgeTurnPagerIndex(currentPagerIndex, turn, pagerPageCount)
    // 夹取后目标仍是当前屏：首屏继续上一页、末屏继续下一页、只有一屏、换章瞬间页数为 0
    return if (target == currentPagerIndex) ReaderTurnEntry.Stay else ReaderTurnEntry.TurnTo(target)
}

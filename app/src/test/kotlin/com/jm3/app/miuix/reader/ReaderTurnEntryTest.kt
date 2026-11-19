package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.EdgePageTurn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 边缘拖拽翻页的入口判定：章内翻一屏，还是停在原地。
 *
 * 库函数 springAnimateToPage 是 PagerState 的挂起扩展，只在组合期的帧时钟下有意义，
 * JVM 单测里调不起来（android unit test 没有帧时钟，withFrameNanos 会永久挂起），
 * 所以这里断言的是“调用它之前”的决策：目标屏下标，以及这一步该不该翻。
 *
 * 跨章不在这条路径上：换章由顶栏按钮与章节抽屉直接走 goToPage，这里不假装覆盖。
 */
class ReaderTurnEntryTest {

    @Test
    fun `章内翻下一页时给出相邻的屏下标`() {
        assertEquals(
            ReaderTurnEntry.TurnTo(4),
            planReaderTurnEntry(currentPagerIndex = 3, turn = EdgePageTurn.Next, pagerPageCount = 10),
        )
    }

    @Test
    fun `章内翻上一页时给出相邻的屏下标`() {
        assertEquals(
            ReaderTurnEntry.TurnTo(2),
            planReaderTurnEntry(currentPagerIndex = 3, turn = EdgePageTurn.Previous, pagerPageCount = 10),
        )
    }

    @Test
    fun `首屏继续翻上一页时停在原地`() {
        assertEquals(
            ReaderTurnEntry.Stay,
            planReaderTurnEntry(currentPagerIndex = 0, turn = EdgePageTurn.Previous, pagerPageCount = 10),
        )
    }

    @Test
    fun `末屏继续翻下一页时停在原地`() {
        assertEquals(
            ReaderTurnEntry.Stay,
            planReaderTurnEntry(currentPagerIndex = 9, turn = EdgePageTurn.Next, pagerPageCount = 10),
        )
    }

    @Test
    fun `只有一屏时两个方向都停在原地`() {
        assertEquals(
            ReaderTurnEntry.Stay,
            planReaderTurnEntry(currentPagerIndex = 0, turn = EdgePageTurn.Next, pagerPageCount = 1),
        )
        assertEquals(
            ReaderTurnEntry.Stay,
            planReaderTurnEntry(currentPagerIndex = 0, turn = EdgePageTurn.Previous, pagerPageCount = 1),
        )
    }

    @Test
    fun `空章没有可翻的屏`() {
        // 换章瞬间 pager 页数会短暂为 0（新章还没有图），此时任何方向都不该起动画
        assertEquals(
            ReaderTurnEntry.Stay,
            planReaderTurnEntry(currentPagerIndex = 0, turn = EdgePageTurn.Next, pagerPageCount = 0),
        )
    }

    @Test
    fun `边界判定为不动时不发起翻页`() {
        // 屏幕侧已把 None 挡在门外，入口判定仍要自洽：None 一律不动
        assertEquals(
            ReaderTurnEntry.Stay,
            planReaderTurnEntry(currentPagerIndex = 3, turn = EdgePageTurn.None, pagerPageCount = 10),
        )
    }

    @Test
    fun `pager 页数落后于当前屏时夹到末屏`() {
        // 手势开始时捕获的页数（3 屏）比当前屏下标（7）小：目标必须夹回首尾，不能落到不存在的屏
        assertEquals(
            ReaderTurnEntry.TurnTo(2),
            planReaderTurnEntry(currentPagerIndex = 7, turn = EdgePageTurn.Next, pagerPageCount = 3),
        )
    }
}

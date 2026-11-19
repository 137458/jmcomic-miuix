package com.jm3.app.miuix.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 跳页出口的判定：横向落到 pager 的哪一屏，纵向落到列表的哪一项。
 *
 * 两条出口都要在 finally 里解冻可见页回写，所以出口先由纯函数定下来，
 * 编排里只剩一个 when 分派。库里的 springAnimateToPage 与 LazyListState.scrollToItem
 * 都依赖组合期帧时钟/滚动通道，JVM 单测不可直接调用，这里覆盖调用前的输入输出。
 */
class ReaderJumpTargetTest {

    @Test
    fun `横向单页跳页把页码换成屏下标`() {
        assertEquals(
            ReaderJumpTarget.Pager(4),
            planJumpTarget(page = 5, pageCount = 10, spreadActive = false, pagerPageCount = 10, horizontal = true),
        )
    }

    @Test
    fun `横向双页跳页落到目标页所在的跨页`() {
        // 5 页按双页排成 3 屏：第 5 页与第 4 页同屏，锚点是第 4 页那一屏，下标为 2
        assertEquals(
            ReaderJumpTarget.Pager(2),
            planJumpTarget(page = 5, pageCount = 5, spreadActive = true, pagerPageCount = 3, horizontal = true),
        )
    }

    @Test
    fun `横向跳页把屏下标夹进 pager 有效范围`() {
        // 换章瞬间 pager 还停在旧章的 3 屏上，第 10 页不能换成不存在的屏
        assertEquals(
            ReaderJumpTarget.Pager(2),
            planJumpTarget(page = 10, pageCount = 10, spreadActive = false, pagerPageCount = 3, horizontal = true),
        )
    }

    @Test
    fun `纵向跳页落到列表项且不受 pager 页数影响`() {
        assertEquals(
            ReaderJumpTarget.VerticalList(4),
            planJumpTarget(page = 5, pageCount = 10, spreadActive = false, pagerPageCount = 3, horizontal = false),
        )
        assertEquals(
            ReaderJumpTarget.VerticalList(0),
            planJumpTarget(page = 1, pageCount = 1, spreadActive = true, pagerPageCount = 1, horizontal = false),
        )
    }
}

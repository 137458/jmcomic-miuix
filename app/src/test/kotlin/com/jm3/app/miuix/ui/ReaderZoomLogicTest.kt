package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderZoomLogicTest {

    @Test
    fun zoomFraction未测量时不算放大() {
        assertFalse(isPageZoomed(null))
    }

    @Test
    fun 一倍的缩放比不算放大() {
        assertFalse(isPageZoomed(0f))
    }

    @Test
    fun 恰好在阈值上不算放大() {
        // 写死 0.02f 而不是引用常量：常量值漂移时这条边界断言才会真的失败
        assertFalse(isPageZoomed(0.02f))
    }

    @Test
    fun 超过阈值才认为已放大() {
        assertTrue(isPageZoomed(0.021f))
        assertTrue(isPageZoomed(2.5f))
    }

    @Test
    fun 页宽未知时不做翻页判定() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-500f, 0f, 1, 5))
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-500f, -1f, 1, 5))
    }

    @Test
    fun 页数未知时不做翻页判定() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-500f, 1_000f, 0, 0))
    }

    @Test
    fun 空拖距离不足时不动() {
        // 页宽 1000，距离阈值 180：150 的空拖不够
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(150f, 1_000f, 2, 5))
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-150f, 1_000f, 2, 5))
    }

    @Test
    fun 空拖距离达标即翻页且方向由位移决定() {
        // 向左拖（负位移）露出下一页
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-250f, 1_000f, 2, 5))
        // 向右拖露出上一页
        assertEquals(EdgePageTurn.Previous, decideEdgePageTurn(250f, 1_000f, 2, 5))
    }

    @Test
    fun 空拖距离不足时速度再快也不翻页() {
        // 快甩时内容会被惯性带走，空拖往往只有触摸阈值那点残差，不能据此翻页
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-80f, 1_000f, 2, 5))
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(80f, 1_000f, 2, 5))
    }

    @Test
    fun 全程平移没有空拖时不翻页() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(0f, 1_000f, 2, 5))
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-30f, 1_000f, 2, 5))
    }

    @Test
    fun 首页没有上一页() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(400f, 1_000f, 0, 5))
    }

    @Test
    fun 末页没有下一页() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-400f, 1_000f, 4, 5))
    }

    @Test
    fun 中间页双向都可翻() {
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-400f, 1_000f, 3, 5))
        assertEquals(EdgePageTurn.Previous, decideEdgePageTurn(400f, 1_000f, 3, 5))
    }

    @Test
    fun 阈值随页宽缩放() {
        // 页宽 2000 时距离阈值 360：300 的位移不够，600 才够
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-300f, 2_000f, 2, 5))
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-600f, 2_000f, 2, 5))
    }

    @Test
    fun 放大时的边界拖动应能翻页() {
        // 放大到底后继续左拖 900px（页宽 40% 以上）应翻到下一页
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-900f, 1_000f, 2, 5))
    }

    @Test
    fun 内容跟着手势移动时不算边界空拖() {
        // 放大后正常平移：每一步内容偏移都在变，累计空拖始终为 0
        val tracker = EdgeDragTracker().apply { start(0f) }
        var offset = 0f
        repeat(6) {
            offset -= 100f
            tracker.onDrag(deltaX = -100f, offsetX = offset)
        }
        assertEquals(0f, tracker.pinnedDragX())
    }

    @Test
    fun 内容到边界后继续拖动累计为空拖() {
        // 前两步还在平移，之后内容停住，只剩后面的位移参与翻页判定
        val tracker = EdgeDragTracker().apply { start(0f) }
        tracker.onDrag(deltaX = -120f, offsetX = -120f)
        tracker.onDrag(deltaX = -120f, offsetX = -240f)
        tracker.onDrag(deltaX = -120f, offsetX = -240f)
        tracker.onDrag(deltaX = -120f, offsetX = -240f)
        assertEquals(-240f, tracker.pinnedDragX())
    }

    @Test
    fun 平移之后又回到原处也按最后一段算空拖() {
        // 内容先右移 200 再回到 0，最后两步内容没动，空拖只累计最后两步
        val tracker = EdgeDragTracker().apply { start(0f) }
        tracker.onDrag(deltaX = 200f, offsetX = 200f)
        tracker.onDrag(deltaX = -200f, offsetX = 0f)
        tracker.onDrag(deltaX = -200f, offsetX = 0f)
        tracker.onDrag(deltaX = -200f, offsetX = 0f)
        assertEquals(-400f, tracker.pinnedDragX())
    }

    @Test
    fun 亚像素偏移抖动不重置空拖() {
        // 浮点偏移在边界处会有零点几像素的抖动，不该把累计位移清掉
        val tracker = EdgeDragTracker().apply { start(0f) }
        tracker.onDrag(deltaX = -100f, offsetX = 0.2f)
        tracker.onDrag(deltaX = -100f, offsetX = -0.3f)
        assertEquals(-199.7f, tracker.pinnedDragX(), 0.01f)
    }

    @Test
    fun 内容位移比手指滞后一帧时不算空拖() {
        // 父节点读到的是上一帧偏移，内容是跟着走的，滞后 20px 仍在容差内
        val tracker = EdgeDragTracker().apply { start(0f) }
        var offset = 0f
        repeat(5) {
            offset -= 100f
            tracker.onDrag(deltaX = -100f, offsetX = offset + 20f)
        }
        assertEquals(0f, tracker.pinnedDragX())
    }

    @Test
    fun 平移撞到边界后的剩余位移算空拖() {
        // 手指走了 600，内容只跟了 200（后面贴边），空拖应为 400
        val tracker = EdgeDragTracker().apply { start(0f) }
        tracker.onDrag(deltaX = -200f, offsetX = -200f)
        tracker.onDrag(deltaX = -200f, offsetX = -200f)
        tracker.onDrag(deltaX = -200f, offsetX = -200f)
        assertEquals(-400f, tracker.pinnedDragX())
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(tracker.pinnedDragX(), 1_000f, 2, 5))
    }

    @Test
    fun 一次手势内平移的距离不参与边界翻页() {
        // 内容跟着走了 900px 才停下，空拖为 0，边界判定不该翻页
        val tracker = EdgeDragTracker().apply { start(0f) }
        var offset = 0f
        repeat(9) {
            offset -= 100f
            tracker.onDrag(deltaX = -100f, offsetX = offset)
        }
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(tracker.pinnedDragX(), 1_000f, 2, 5))
    }
}
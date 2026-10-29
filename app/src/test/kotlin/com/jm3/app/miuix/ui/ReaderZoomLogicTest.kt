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
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-500f, -2_000f, 0f, 1, 5))
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-500f, -2_000f, -1f, 1, 5))
    }

    @Test
    fun 页数未知时不做翻页判定() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-500f, -2_000f, 1_000f, 0, 0))
    }

    @Test
    fun 位移与速度都不达标时不动() {
        // 页宽 1000，阈值 250，位移 200 且速度仅 300 都不够
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(200f, 300f, 1_000f, 2, 5))
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-200f, -300f, 1_000f, 2, 5))
    }

    @Test
    fun 位移达标即翻页且方向由位移决定() {
        // 向左拖（负位移）露出下一页
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-250f, 0f, 1_000f, 2, 5))
        // 向右拖露出上一页
        assertEquals(EdgePageTurn.Previous, decideEdgePageTurn(250f, 0f, 1_000f, 2, 5))
    }

    @Test
    fun 位移不足但甩动速度足够也翻页() {
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-40f, -1_000f, 1_000f, 2, 5))
        assertEquals(EdgePageTurn.Previous, decideEdgePageTurn(40f, 1_000f, 1_000f, 2, 5))
    }

    @Test
    fun 距离达标时忽略反向甩动() {
        // 位移向左 300 已达标，速度反向 2000 也不该翻转成上一页
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-300f, 2_000f, 1_000f, 2, 5))
    }

    @Test
    fun 首页没有上一页() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(400f, 2_000f, 1_000f, 0, 5))
    }

    @Test
    fun 末页没有下一页() {
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-400f, -2_000f, 1_000f, 4, 5))
    }

    @Test
    fun 中间页双向都可翻() {
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-400f, 0f, 1_000f, 3, 5))
        assertEquals(EdgePageTurn.Previous, decideEdgePageTurn(400f, 0f, 1_000f, 3, 5))
    }

    @Test
    fun 阈值随页宽缩放() {
        // 页宽 2000 时阈值 500：400 的位移不够，600 才够
        assertEquals(EdgePageTurn.None, decideEdgePageTurn(-400f, 0f, 2_000f, 2, 5))
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-600f, 0f, 2_000f, 2, 5))
    }

    @Test
    fun 放大时的边界拖动应能翻页() {
        // 放大到底后继续左拖 900px（页宽 40% 以上）应翻到下一页
        assertEquals(EdgePageTurn.Next, decideEdgePageTurn(-900f, 0f, 1_000f, 2, 5))
    }
}

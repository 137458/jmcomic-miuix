package com.jm3.app.miuix.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * pager 目标页夹取：双页与单页两条边界拖拽编排共用一个出口，越界目标一律落到端点页。
 */
class ReaderPagerTargetTest {

    @Test
    fun 中间目标页原样保留() {
        assertEquals(3, clampPagerTarget(3, 10))
    }

    @Test
    fun 超过末页夹到末页() {
        assertEquals(4, clampPagerTarget(5, 5))
        assertEquals(4, clampPagerTarget(99, 5))
    }

    @Test
    fun 负数夹到首页() {
        assertEquals(0, clampPagerTarget(-1, 5))
        assertEquals(0, clampPagerTarget(-40, 5))
    }

    @Test
    fun 单页与空分页都落到零() {
        assertEquals(0, clampPagerTarget(1, 1))
        assertEquals(0, clampPagerTarget(0, 0))
        assertEquals(0, clampPagerTarget(7, 0))
    }
}
